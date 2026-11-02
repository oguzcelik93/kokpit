package io.github.yorulmazsinan.kokpit.panel

import android.content.Context
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.BuildConfig
import io.github.yorulmazsinan.kokpit.car.Place
import io.github.yorulmazsinan.kokpit.car.PlaceKind
import io.github.yorulmazsinan.kokpit.car.fuelStats
import io.github.yorulmazsinan.kokpit.car.localIpv4
import io.github.yorulmazsinan.kokpit.car.place
import io.github.yorulmazsinan.kokpit.car.setPlace
import io.github.yorulmazsinan.kokpit.car.tokenMatches
import io.github.yorulmazsinan.kokpit.system.diagnosticsText
import io.github.yorulmazsinan.kokpit.time.TrueTime
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.time.YearMonth
import io.github.yorulmazsinan.kokpit.system.BRIGHTNESS_MAX
import io.github.yorulmazsinan.kokpit.system.BRIGHTNESS_MIN
import io.github.yorulmazsinan.kokpit.system.Brightness
import io.github.yorulmazsinan.kokpit.update.UpdateState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phone control panel: while the driver keeps it open, a small web app lets the phone manage
 * photos, fuel log, home/work and read the report — with a phone keyboard and screen instead
 * of the slow unit. Two doors, one set of rules:
 * - Wi-Fi: the phone's browser, on the unit's hotspot/own-Wi-Fi address only;
 * - Bluetooth: Kokpit Kumanda on a paired phone, over a secure RFCOMM link ([PanelBluetooth]).
 * Both carry the same requests to the same [handle], so on either door:
 * - opened only by someone in the car: the QR on the unit's screen, or Kumanda's "Bağlan",
 *   which waits for a tap on "İzin ver" on the unit; never the internet;
 * - a fresh 128-bit token per opening, then the owner's PIN (or, over Bluetooth only, the key
 *   of a phone the owner chose to remember) for a session; closes after [PANEL_IDLE_MS] idle
 *   or [PANEL_MAX_MS];
 * - a fixed list of actions (no installs, no shell, no file browsing); every input is checked;
 *   photos must decode as pictures.
 * With the panel closed, the Bluetooth door takes only the connection request and, from a
 * remembered phone, a shared route or photo ("Paylaş → Kokpit").
 */
class PhonePanel(private val context: Context, private val container: AppContainer) {
    /** [url] is the QR text; [wifi]/[bluetooth] say which doors are open. */
    data class Status(val url: String, val lastActivityMs: Long, val wifi: Boolean = true, val bluetooth: Boolean = false)

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()

    /** One opening of the panel: its Wi-Fi listener (the Bluetooth door stays). */
    private class Opening(val tcp: ServerSocket?, val openedMs: Long) {
        fun close() {
            runCatching { tcp?.close() }
        }
    }

    /** A paired phone asking, from Kumanda's "Bağlan", to open the panel. */
    data class Knock(val peer: Peer, val remembered: Boolean)

    private val _knock = MutableStateFlow<Knock?>(null)
    val knock: StateFlow<Knock?> = _knock.asStateFlow()
    private val knockLock = Object()
    private var knockAnswer: Boolean? = null
    private val refusedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** A route sent from a remembered phone, waiting on the unit for "Yola çık". */
    private val _route = MutableStateFlow<SharedRoute?>(null)
    val route: StateFlow<SharedRoute?> = _route.asStateFlow()
    fun clearRoute() { _route.value = null }

    /** Brings Kokpit to the front (a request or a route is waiting on its screen). */
    var onAttention: () -> Unit = {}

    @Volatile private var door: PanelBluetooth.Server? = null
    private val trust = PanelTrust(context)

    /** Remembered phones, for the unit's list. */
    fun trustedPhones(): List<TrustedPhone> = trust.list()
    fun forgetPhone(address: String) = trust.remove(address)

    @Volatile private var current: Opening? = null
    @Volatile private var token: String = ""
    private val pin = PanelPin(context)
    private val bluetooth = PanelBluetooth(context)
    /** Requests from the two doors run one at a time, as they did with Wi-Fi alone. */
    private val handling = Any()
    /** Sessions opened with the PIN during this opening of the panel. */
    private val sessions = java.util.concurrent.CopyOnWriteArraySet<String>()

    /** Why the Bluetooth door is closed this time (null when open). */
    val bluetoothProblem: String? get() = bluetooth.problem

    /**
     * Keeps the Bluetooth door open while Kokpit runs (reopened when Bluetooth comes back).
     * Call once, at start.
     */
    fun startDoor() {
        Thread {
            while (true) {
                if (door?.running != true) {
                    door = bluetooth.listen()?.also { server ->
                        server.serve { peer, input, out ->
                            while (serveOnePanelRequest(input, out, { tokenOk(it) || remembered(peer, it) }) { r, b, o -> dispatch(peer, r, b, o) }) Unit
                        }
                    }
                }
                runCatching { Thread.sleep(DOOR_CHECK_MS) }
            }
        }.apply { isDaemon = true; name = "kokpit-panel-door" }.start()
    }

    /** Opens the panel; returns its status (QR text, open doors), or null when neither door could open. */
    @Synchronized
    fun open(): Status? {
        stopServer()
        val ip = localIpv4()
        val tcp = ip?.let { runCatching { ServerSocket(0, 8, InetAddress.getByName(it)) }.getOrNull() }
        val bt = door?.takeIf { it.running }
        val newToken = newToken()
        val url = panelQr(ip.takeIf { tcp != null }, tcp?.localPort, newToken, bt?.name, bt != null)
        if (url == null) {
            runCatching { tcp?.close() }
            return null
        }
        token = newToken
        val opened = System.currentTimeMillis()
        val opening = Opening(tcp, opened)
        current = opening
        val status = Status(url, opened, wifi = tcp != null, bluetooth = bt != null)
        _status.value = status

        tcp?.let { socket ->
            Thread {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: continue
                    runCatching {
                        client.use { c ->
                            c.soTimeout = 30_000
                            serveOnePanelRequest(BufferedInputStream(c.getInputStream()), c.getOutputStream(), ::tokenOk) { r, b, o -> dispatch(null, r, b, o) }
                        }
                    }
                }
            }.apply { isDaemon = true; name = "kokpit-panel" }.start()
        }

        Thread {
            while (current === opening) {
                runCatching { Thread.sleep(1_000) }
                val now = System.currentTimeMillis()
                val last = _status.value?.lastActivityMs ?: break
                if (now - last > PANEL_IDLE_MS || now - opened > PANEL_MAX_MS) {
                    closeIf(opening)
                    break
                }
            }
        }.apply { isDaemon = true; name = "kokpit-panel-timer" }.start()
        return status
    }

    /** On the unit only: forget the PIN (the next connection sets a new one). */
    fun resetPin() {
        pin.reset()
        trust.clear()
        sessions.clear()
    }

    val pinSet: Boolean get() = pin.isSet

    /** Called whenever the panel stops (closed, timed out). */
    var onClosed: () -> Unit = {}

    /** Closes the panel (and the unit's own Wi-Fi, if it was opened for it). */
    fun close() {
        stopServer()
        onClosed()
    }

    @Synchronized
    private fun closeIf(opening: Opening) {
        if (current === opening) close()
    }

    /** Stops listening without touching the Wi-Fi (used when reopening). */
    private fun stopServer() {
        token = ""
        sessions.clear()
        current?.close()
        current = null
        _status.value = null
    }

    private fun newToken(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun tokenOk(req: PanelRequest): Boolean = tokenMatches(token, req.query["t"] ?: req.headers["x-kokpit-token"])

    /** A remembered phone, on its own Bluetooth link, with its key. */
    private fun remembered(peer: Peer?, req: PanelRequest): Boolean = peer != null && trust.check(peer.address, req.headers[DEVICE_KEY_HEADER])

    private fun dispatch(peer: Peer?, req: PanelRequest, body: ByteArray, out: OutputStream) {
        when {
            // Waits for the tap on the unit, so it must not hold up anyone else's requests.
            req.path == "/api/knock" -> knock(peer, req, out)
            req.path.startsWith("/api/share/") || req.path == "/api/hello" -> synchronized(handling) { share(peer, req, body, out) }
            else -> synchronized(handling) { handle(peer, req, body, out) }
        }
    }

    /** Kumanda's "Bağlan": shown on the unit; only "İzin ver" there opens the panel for this phone. */
    private fun knock(peer: Peer?, req: PanelRequest, out: OutputStream) {
        fun reply(code: Int, text: String) = writePanelReply(out, code, "text/plain; charset=utf-8", text.toByteArray())
        if (peer == null || req.method != "POST") return reply(404, "not found")
        val asked = System.currentTimeMillis()
        synchronized(knockLock) {
            if (_knock.value != null) return reply(400, "Ünitede başka bir bağlanma isteği bekliyor.")
            if (asked - (refusedAt[peer.address] ?: 0L) < KNOCK_COOLDOWN_MS) return reply(400, "Ünitede reddedildi. Biraz sonra tekrar deneyin.")
            knockAnswer = null
            _knock.value = Knock(peer, trust.list().any { it.address == peer.address })
        }
        onAttention()
        val answer: Boolean?
        synchronized(knockLock) {
            while (knockAnswer == null && System.currentTimeMillis() - asked < KNOCK_WAIT_MS) knockLock.wait(1_000)
            answer = knockAnswer
            knockAnswer = null
            _knock.value = null
        }
        if (answer != true) {
            if (answer == false) refusedAt[peer.address] = System.currentTimeMillis()
            return reply(403, if (answer == false) "Ünitede reddedildi." else "Ünitede kimse \"İzin ver\"e dokunmadı.")
        }
        if (_status.value == null && open() == null) return reply(400, "Panel açılamadı.")
        _status.value = _status.value?.copy(lastActivityMs = System.currentTimeMillis())
        reply(200, token)
    }

    /** The unit's answer to [knock] ("İzin ver" / "Reddet"). */
    fun answerKnock(allow: Boolean) = synchronized(knockLock) {
        if (_knock.value != null) {
            knockAnswer = allow
            knockLock.notifyAll()
        }
    }

    /** "Paylaş → Kokpit" from a remembered phone: a route, or a photo for the album. */
    private fun share(peer: Peer?, req: PanelRequest, requestBody: ByteArray, out: OutputStream) {
        fun reply(code: Int, text: String) = writePanelReply(out, code, "text/plain; charset=utf-8", text.toByteArray())
        if (!remembered(peer, req)) return reply(403, "Bu telefon tanınmıyor. Önce Kumanda'da \"Bağlan\" deyip PIN'i girerken \"Bu telefonu hatırla\"yı işaretleyin.")
        when {
            req.method == "GET" && req.path == "/api/hello" -> reply(200, peer!!.name)
            req.method == "POST" && req.path == "/api/share/nav" -> {
                val text = requestBody.takeIf { it.isNotEmpty() && it.size <= PANEL_MAX_JSON }?.decodeToString() ?: return reply(400, "Boş")
                _route.value = parseSharedRoute(text) ?: return reply(400, "Bu paylaşımda bir yer bulunamadı.")
                onAttention()
                reply(200, "Rota ünitede hazır.")
            }
            req.method == "POST" && req.path == "/api/share/photo" -> {
                if (requestBody.isEmpty()) return reply(400, "Boş")
                if (!container.wallpaper.save(ByteArrayInputStream(requestBody))) return reply(400, "Bu bir fotoğraf değil.")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, io.github.yorulmazsinan.kokpit.R.string.photo_from_phone, android.widget.Toast.LENGTH_SHORT).show()
                }
                reply(200, "Fotoğraf albüme eklendi.")
            }
            else -> reply(404, "not found")
        }
    }

    private fun handle(peer: Peer?, req: PanelRequest, requestBody: ByteArray, out: OutputStream) {
        fun reply(out: OutputStream, code: Int, type: String, body: ByteArray, extra: Map<String, String> = emptyMap()) = writePanelReply(out, code, type, body, extra)
        val given = req.query["t"] ?: req.headers["x-kokpit-token"]
        if (!tokenMatches(token, given)) return reply(out, 403, "text/plain", "forbidden".toByteArray())
        _status.value = _status.value?.copy(lastActivityMs = System.currentTimeMillis())

        fun body(limit: Int): ByteArray? = requestBody.takeIf { it.isNotEmpty() && it.size <= limit }
        fun ok(text: String = "ok") = reply(out, 200, "text/plain; charset=utf-8", text.toByteArray())
        fun bad(text: String) = reply(out, 400, "text/plain; charset=utf-8", text.toByteArray())

        // The QR's token opens the page and the PIN step; everything else also needs a session,
        // which only the owner's PIN gives.
        when {
            req.method == "GET" && req.path == "/" -> return reply(out, 200, "text/html; charset=utf-8", panelPage(token).toByteArray())
            req.method == "GET" && req.path == "/api/pin" -> return reply(out, 200, "application/json; charset=utf-8",
                buildJsonObject {
                    put("set", pin.isSet); put("lockedUntil", pin.lockedUntil())
                    // Kumanda (Bluetooth) may remember the phone; a remembered one skips the PIN.
                    put("kumanda", peer != null); put("remembered", pin.isSet && remembered(peer, req))
                }.toString().toByteArray())
            req.method == "POST" && req.path == "/api/pin/device" -> {
                if (!pin.isSet || !remembered(peer, req)) return reply(out, 401, "text/plain; charset=utf-8", "PIN gerekli".toByteArray())
                return ok(newSession())
            }
            req.method == "POST" && (req.path == "/api/pin/setup" || req.path == "/api/pin/login") -> {
                val text = body(PANEL_MAX_JSON)?.decodeToString() ?: return bad("Boş")
                val fields = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                val entered = runCatching { fields?.get("pin")?.jsonPrimitive?.content }.getOrNull().orEmpty()
                val remember = runCatching { fields?.get("remember")?.jsonPrimitive?.content == "true" }.getOrDefault(false)
                if (req.path == "/api/pin/setup") {
                    if (pin.isSet) return bad("PIN zaten belirlenmiş")
                    if (!pin.create(entered)) return bad("4 rakam seçin; 0000, 1234 gibi kolay PIN'ler olmaz")
                } else {
                    when (val r = pin.check(entered)) {
                        PinResult.Ok -> Unit
                        is PinResult.Wrong -> return bad("Yanlış PIN (${r.triesLeft} hak kaldı)")
                        is PinResult.Locked -> return bad("Çok fazla yanlış deneme. ${((r.untilMs - System.currentTimeMillis()) / 60_000 + 1)} dakika sonra tekrar deneyin.")
                    }
                }
                val session = newSession()
                // Only over Bluetooth: the key is bound to this phone's own link.
                if (remember && peer != null) {
                    val key = trust.add(peer.address, peer.name)
                    return reply(out, 200, "text/plain; charset=utf-8", session.toByteArray(), mapOf(DEVICE_KEY_HEADER to key))
                }
                return ok(session)
            }
        }
        val session = req.headers["x-kokpit-session"] ?: req.query["s"]
        if (session == null || sessions.none { tokenMatches(it, session) }) return reply(out, 401, "text/plain; charset=utf-8", "PIN gerekli".toByteArray())

        val photo = photoAction(req.path)
        when {
            req.method == "GET" && req.path == "/api/state" -> reply(out, 200, "application/json; charset=utf-8", state().toByteArray())
            req.method == "GET" && req.path.startsWith("/photo/") -> {
                val id = req.path.removePrefix("/photo/").removeSuffix(".jpg")
                val file = if (isPhotoId(id)) container.wallpaper.photoFile(id) else null
                if (file == null) reply(out, 404, "text/plain", "not found".toByteArray()) else reply(out, 200, "image/jpeg", file.readBytes())
            }
            req.method == "GET" && req.path == "/api/report" -> reply(out, 200, "text/plain; charset=utf-8", diagnosticsText(context).toByteArray())
            req.method == "POST" && req.path == "/api/photo" -> {
                val bytes = body(PANEL_MAX_BODY) ?: return bad("Fotoğraf çok büyük ya da boş")
                if (container.wallpaper.save(ByteArrayInputStream(bytes))) ok() else bad("Bu bir fotoğraf değil")
            }
            req.method == "POST" && photo != null -> {
                val (id, action) = photo
                if (container.wallpaper.photoFile(id) == null) return bad("Fotoğraf bulunamadı")
                when (action) {
                    "select" -> container.wallpaper.select(id)
                    "rotate" -> container.wallpaper.rotateQuarter(id)
                    "delete" -> container.wallpaper.delete(id)
                }
                ok()
            }
            req.method == "POST" && req.path == "/api/fuel" -> {
                val text = body(PANEL_MAX_JSON)?.decodeToString() ?: return bad("Boş")
                val entry = parseFuel(text, TrueTime.nowMs(), container.car.fuel.value.maxOfOrNull { it.odometerKm })
                    ?: return bad("Kilometre, litre ve tutarı kontrol edin (kilometre son kayıttan büyük olmalı)")
                container.car.addFuel(entry)
                ok()
            }
            req.method == "POST" && req.path == "/api/place" -> {
                val text = body(PANEL_MAX_JSON)?.decodeToString() ?: return bad("Boş")
                val (kind, address) = parsePlace(text) ?: return bad("Adres 2–200 karakter olmalı")
                container.car.setPlace(if (kind == "home") PlaceKind.HOME else PlaceKind.WORK, Place(address = address))
                ok()
            }
            req.method == "GET" && req.path == "/api/settings" -> reply(out, 200, "application/json; charset=utf-8", panelSettingsJson(container.settings.settings.value).toByteArray())
            req.method == "POST" && req.path == "/api/settings" -> {
                val text = body(PANEL_MAX_JSON)?.decodeToString() ?: return bad("Boş")
                val current = runBlocking { container.settings.current() }
                val changed = applyPanelSettings(current, text) ?: return bad("Geçersiz ayar")
                runBlocking { container.settings.update { changed } }
                container.scope.launch { container.autoBrightness.reapply() }
                ok()
            }
            req.method == "POST" && req.path == "/api/brightness" -> {
                val text = body(PANEL_MAX_JSON)?.decodeToString() ?: return bad("Boş")
                val value = runCatching { Json.parseToJsonElement(text).jsonObject["value"]?.jsonPrimitive?.int }.getOrNull()
                    ?.takeIf { it in BRIGHTNESS_MIN..BRIGHTNESS_MAX } ?: return bad("Parlaklık 10–255 olmalı")
                if (!Brightness.canWrite(context)) return bad("Ünitede bir kez parlaklık izni verilmeli (☀ düğmesi)")
                Brightness.set(context, value)
                ok()
            }
            req.method == "GET" && req.path == "/api/trips" -> reply(out, 200, "application/json; charset=utf-8", tripsJson().toByteArray())
            req.method == "POST" && recordDelete(req.path, "trips") != null -> {
                val start = recordDelete(req.path, "trips")!!
                val trip = container.trips.trips.value.firstOrNull { it.startMs == start } ?: return bad("Kayıt yok")
                container.trips.delete(trip)
                ok()
            }
            req.method == "GET" && req.path == "/api/fuel" -> reply(out, 200, "application/json; charset=utf-8", fuelJson().toByteArray())
            req.method == "POST" && recordDelete(req.path, "fuel") != null -> {
                val time = recordDelete(req.path, "fuel")!!
                val entry = container.car.fuel.value.firstOrNull { it.timeMs == time } ?: return bad("Kayıt yok")
                container.car.removeFuel(entry)
                ok()
            }
            req.method == "GET" && req.path == "/api/park" -> {
                val spot = container.car.parked.value
                val text = if (spot == null) "{}" else buildJsonObject {
                    put("time", java.time.Instant.ofEpochMilli(spot.timeMs).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString())
                    put("link", io.github.yorulmazsinan.kokpit.car.mapsLink(spot.lat, spot.lon))
                }.toString()
                reply(out, 200, "application/json; charset=utf-8", text.toByteArray())
            }
            req.method == "GET" && req.path == "/api/bt" -> {
                val list = JsonArray(container.btConnect.phones().map { p ->
                    buildJsonObject { put("address", p.address); put("name", p.name); put("connected", p.connected) }
                })
                reply(out, 200, "application/json; charset=utf-8", list.toString().toByteArray())
            }
            req.method == "POST" && req.path == "/api/bt/switch" -> {
                val text = body(PANEL_MAX_JSON)?.decodeToString() ?: return bad("Boş")
                val address = runCatching { Json.parseToJsonElement(text).jsonObject["address"]?.jsonPrimitive?.content }.getOrNull()
                    ?.takeIf(::isBluetoothAddress) ?: return bad("Geçersiz telefon")
                if (container.btConnect.phones().none { it.address == address }) return bad("Bu telefon eşleşmiş değil")
                val done = runBlocking { container.btConnect.switchTo(address) }
                if (done) ok("Bağlandı") else bad("Bağlanmadı — telefonun Bluetooth'u açık mı?")
            }
            req.method == "POST" && req.path == "/api/intro" -> {
                val bytes = body(PANEL_MAX_VIDEO) ?: return bad("Video en çok 20 MB olabilir")
                if (!looksLikeMp4(bytes.copyOfRange(0, minOf(16, bytes.size)))) return bad("Yalnızca MP4 video")
                val name = "telefon-${TrueTime.dateTime().withNano(0).toString().replace(':', '-')}.mp4"
                container.introLibrary.import(ByteArrayInputStream(bytes), name, bytes.size.toLong())
                    .fold({ ok() }, { bad("Video eklenemedi") })
            }
            req.method == "GET" && req.path == "/api/update" -> {
                val text = when (val u = container.updater.state.value) {
                    is UpdateState.Ready -> "${u.info.versionName} indirildi — ünitede üstteki yazıya dokunup Kur deyin"
                    is UpdateState.Available -> "${u.info.versionName} var"
                    is UpdateState.Downloading -> "${u.info.versionName} indiriliyor %${u.percent}"
                    is UpdateState.UpToDate -> "Güncel (${BuildConfig.VERSION_NAME})"
                    is UpdateState.Failed -> "Kontrol edilemedi: ${u.reason}"
                    UpdateState.Checking -> "Kontrol ediliyor…"
                    UpdateState.Idle -> "Kurulu: ${BuildConfig.VERSION_NAME}"
                }
                ok(text)
            }
            req.method == "POST" && req.path == "/api/update/check" -> {
                container.scope.launch { container.updater.check(downloadIfNew = true) }
                ok()
            }
            req.method == "GET" && req.path == "/api/media" -> ok(volumeText())
            req.method == "POST" && req.path == "/api/media" -> {
                val action = body(PANEL_MAX_JSON)?.decodeToString()?.let(::parseMediaAction) ?: return bad("Geçersiz düğme")
                media(action)
                ok(volumeText())
            }
            req.method == "GET" && req.path == "/api/reminders" -> reply(out, 200, "application/json; charset=utf-8", remindersJson().toByteArray())
            req.method == "POST" && req.path == "/api/reminders" -> {
                val text = body(PANEL_MAX_JSON)?.decodeToString() ?: return bad("Boş")
                val changed = io.github.yorulmazsinan.kokpit.car.applyReminders(container.reminders.reminders.value, text) ?: return bad("Tarih ya da kilometre geçersiz")
                container.reminders.set(changed)
                ok()
            }
            req.method == "POST" && req.path == "/api/close" -> { ok(); close() }
            else -> reply(out, 404, "text/plain", "not found".toByteArray())
        }
    }

    private fun newSession(): String =
        ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }.also { sessions += it }

    private val audio get() = context.getSystemService(android.media.AudioManager::class.java)

    private fun volumeText(): String = runCatching {
        "${audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)}/${audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)}"
    }.getOrDefault("")

    /** The passenger remote: volume, play/pause and tracks (whatever is playing on the unit). */
    private fun media(action: String) {
        val am = audio
        val flags = android.media.AudioManager.FLAG_SHOW_UI
        when (action) {
            "vol_up" -> am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, flags)
            "vol_down" -> am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, flags)
            "mute" -> am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_TOGGLE_MUTE, flags)
            "play" -> container.playPause()
            "next", "prev" -> {
                val next = action == "next"
                if (container.nowPlaying.currentPackage() != null) {
                    if (next) container.nowPlaying.next() else container.nowPlaying.previous()
                } else {
                    val code = if (next) android.view.KeyEvent.KEYCODE_MEDIA_NEXT else android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    val t = android.os.SystemClock.uptimeMillis()
                    am.dispatchMediaKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, code, 0))
                    am.dispatchMediaKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, code, 0))
                }
            }
        }
    }

    private fun dueJson(): JsonArray {
        val due = io.github.yorulmazsinan.kokpit.car.dueReminders(container.reminders.reminders.value, TrueTime.date(), container.car.fuel.value.maxOfOrNull { it.odometerKm })
        return JsonArray(due.map { d -> buildJsonObject { put("text", d.text); put("overdue", d.overdue) } })
    }

    private fun remindersJson(): String {
        val r = container.reminders.reminders.value
        return buildJsonObject {
            io.github.yorulmazsinan.kokpit.car.ReminderKind.entries.forEach { put(it.key, r.dates[it.key].orEmpty()) }
            put("oilKm", r.oilKm?.toString().orEmpty())
            put("tires", r.tires)
            put("lastKm", container.car.fuel.value.maxOfOrNull { it.odometerKm })
            put("due", dueJson())
        }.toString()
    }

    private fun state(): String {
        val fuel = container.car.fuel.value
        val stats = fuelStats(fuel, YearMonth.from(TrueTime.date()))
        fun placeText(kind: PlaceKind) = container.car.place(kind).let { it.address ?: if (it.hasPoint) "Konum kayıtlı" else "" }
        return buildJsonObject {
            put("version", BuildConfig.VERSION_NAME)
            put("time", TrueTime.dateTime().withNano(0).toString())
            put("selected", container.wallpaper.selected.value)
            put("photos", JsonArray(container.wallpaper.photos.value.map(::JsonPrimitive)))
            put("home", placeText(PlaceKind.HOME))
            put("work", placeText(PlaceKind.WORK))
            put("lastKm", fuel.maxOfOrNull { it.odometerKm })
            put("avgL100", stats.averageL100)
            put("monthCost", stats.monthCost)
            put("fuelCount", fuel.size)
            put("due", dueJson())
        }.toString()
    }

    private fun tripsJson(): String {
        val trips = container.trips.trips.value
        val totals = io.github.yorulmazsinan.kokpit.car.kmTotals(trips, TrueTime.date())
        val zone = java.time.ZoneId.systemDefault()
        return buildJsonObject {
            put("today", totals.todayKm); put("week", totals.weekKm); put("month", totals.monthKm); put("total", totals.totalKm)
            put("trips", JsonArray(trips.take(100).map { t ->
                buildJsonObject {
                    put("start", t.startMs)
                    put("when", java.time.Instant.ofEpochMilli(t.startMs).atZone(zone).toLocalDateTime().withNano(0).toString())
                    put("km", t.km); put("minutes", t.minutes)
                }
            }))
        }.toString()
    }

    private fun fuelJson(): String {
        val zone = java.time.ZoneId.systemDefault()
        return JsonArray(container.car.fuel.value.map { f ->
            buildJsonObject {
                put("time", f.timeMs)
                put("date", java.time.Instant.ofEpochMilli(f.timeMs).atZone(zone).toLocalDate().toString())
                put("km", f.odometerKm); put("liters", f.liters); put("price", f.totalPrice); put("full", f.full)
            }
        }).toString()
    }

    private companion object {
        /** How often the Bluetooth door checks it is still open. */
        const val DOOR_CHECK_MS = 20_000L
        /** How long "Bağlan" waits for the tap on the unit. */
        const val KNOCK_WAIT_MS = 60_000L
        /** After "Reddet", the same phone waits this long before asking again. */
        const val KNOCK_COOLDOWN_MS = 30_000L
    }
}
