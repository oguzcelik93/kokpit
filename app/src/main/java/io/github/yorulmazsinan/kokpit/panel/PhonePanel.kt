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
 * Phone control panel: while the driver keeps it open, a small web app on the hotspot
 * network lets the phone manage photos, fuel log, home/work and read the report — with a
 * phone keyboard and screen instead of the slow unit. Safety:
 * - opened only from the unit (the QR on screen means you are in the car); listens only
 *   on the unit's hotspot address, never the internet;
 * - a fresh 128-bit token per opening; closes after [PANEL_IDLE_MS] idle or [PANEL_MAX_MS];
 * - a fixed list of actions (no installs, no shell, no settings, no file browsing); every
 *   input is checked; photos must decode as pictures.
 */
class PhonePanel(private val context: Context, private val container: AppContainer) {
    data class Status(val url: String, val lastActivityMs: Long)

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status.asStateFlow()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var token: String = ""

    /** Opens the panel; returns its link (for the QR), or null without a network. */
    fun open(): String? {
        close()
        val ip = localIpv4() ?: return null
        token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val socket = runCatching { ServerSocket(0, 8, InetAddress.getByName(ip)) }.getOrNull() ?: return null
        server = socket
        val url = "http://$ip:${socket.localPort}/?t=$token"
        val opened = System.currentTimeMillis()
        _status.value = Status(url, opened)
        Thread {
            try {
                socket.soTimeout = 1_000
                while (!socket.isClosed) {
                    val now = System.currentTimeMillis()
                    val last = _status.value?.lastActivityMs ?: break
                    if (now - last > PANEL_IDLE_MS || now - opened > PANEL_MAX_MS) break
                    val client = runCatching { socket.accept() }.getOrNull() ?: continue
                    runCatching { handle(client) }
                }
            } finally {
                runCatching { socket.close() }
                if (server === socket) {
                    server = null
                    _status.value = null
                }
            }
        }.apply { isDaemon = true; name = "kokpit-panel" }.start()
        return url
    }

    fun close() {
        runCatching { server?.close() }
        server = null
        _status.value = null
    }

    private fun handle(client: Socket) = client.use { s ->
        s.soTimeout = 30_000
        val input = BufferedInputStream(s.getInputStream())
        val out = s.getOutputStream()
        val head = readHead(input) ?: return
        val req = parsePanelRequest(head) ?: return
        val given = req.query["t"] ?: req.headers["x-kokpit-token"]
        if (!tokenMatches(token, given)) return reply(out, 403, "text/plain", "forbidden".toByteArray())
        _status.value = _status.value?.copy(lastActivityMs = System.currentTimeMillis())

        fun body(limit: Int): ByteArray? {
            val length = req.headers["content-length"]?.toIntOrNull() ?: return null
            if (length <= 0 || length > limit) return null
            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(bytes, read, length - read)
                if (n < 0) return null
                read += n
            }
            return bytes
        }
        fun ok(text: String = "ok") = reply(out, 200, "text/plain; charset=utf-8", text.toByteArray())
        fun bad(text: String) = reply(out, 400, "text/plain; charset=utf-8", text.toByteArray())

        val photo = photoAction(req.path)
        when {
            req.method == "GET" && req.path == "/" -> reply(out, 200, "text/html; charset=utf-8", panelPage(token).toByteArray())
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
            req.method == "POST" && req.path == "/api/close" -> { ok(); close() }
            else -> reply(out, 404, "text/plain", "not found".toByteArray())
        }
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

    private fun readHead(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8_192) {
            val c = input.read()
            if (c < 0) return null
            sb.append(c.toChar())
            if (sb.endsWith("\r\n\r\n")) return sb.toString()
        }
        return null
    }

    private fun reply(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val reason = mapOf(200 to "OK", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 413 to "Payload Too Large")[code] ?: "Error"
        out.write("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }
}
