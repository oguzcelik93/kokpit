package io.github.yorulmazsinan.kokpit.panel

import io.github.yorulmazsinan.kokpit.car.FuelEntry
import io.github.yorulmazsinan.kokpit.car.parseDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream

/** The panel closes after this long without a request … */
const val PANEL_IDLE_MS = 15 * 60_000L

/** … and after this long in any case. */
const val PANEL_MAX_MS = 60 * 60_000L

/** Largest request body: one phone photo. */
const val PANEL_MAX_BODY = 25 * 1024 * 1024

/** Small JSON bodies (fuel, address) must stay small. */
const val PANEL_MAX_JSON = 4 * 1024

data class PanelRequest(val method: String, val path: String, val query: Map<String, String>, val headers: Map<String, String>)

/** `POST /api/photo/123/select?t=abc HTTP/1.1` + header lines → request, or null when malformed. */
fun parsePanelRequest(head: String): PanelRequest? {
    val lines = head.split("\r\n").filter { it.isNotEmpty() }
    val parts = lines.firstOrNull()?.split(' ') ?: return null
    if (parts.size < 2) return null
    val target = parts[1]
    val query = target.substringAfter('?', "").split('&').filter { '=' in it }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    val headers = lines.drop(1).filter { ':' in it }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
    return PanelRequest(parts[0].uppercase(), target.substringBefore('?'), query, headers)
}

/** Album ids are millisecond timestamps: digits only, so no path can sneak into a file name. */
fun isPhotoId(id: String): Boolean = id.length in 1..19 && id.all(Char::isDigit)

/** `/api/photo/123/rotate` → ("123", "rotate"); anything else → null. */
fun photoAction(path: String): Pair<String, String>? {
    val m = Regex("^/api/photo/([0-9]{1,19})/(select|rotate|delete)$").find(path) ?: return null
    return m.groupValues[1] to m.groupValues[2]
}

private val json = Json { ignoreUnknownKeys = true }

private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

/** `{"km":"85420","liters":"45,3","price":"2160","full":true}` → entry, or null if anything is off. */
fun parseFuel(body: String, nowMs: Long, lastOdometer: Int?): FuelEntry? = runCatching {
    val o = json.parseToJsonElement(body).jsonObject
    val km = o.text("km")?.trim()?.toIntOrNull()?.takeIf { it in 1..2_000_000 } ?: return null
    if (lastOdometer != null && km <= lastOdometer) return null
    val liters = o.text("liters")?.let(::parseDecimal)?.takeIf { it <= 200 } ?: return null
    val price = o.text("price")?.let(::parseDecimal)?.takeIf { it <= 100_000 } ?: return null
    val full = o["full"]?.jsonPrimitive?.booleanOrNull ?: true
    FuelEntry(nowMs, km, liters, price, full)
}.getOrNull()

/** `{"kind":"home","address":"…"}` → (kind, address); address trimmed, 2–200 chars, no control characters. */
fun parsePlace(body: String): Pair<String, String?>? = runCatching {
    val o = json.parseToJsonElement(body).jsonObject
    val kind = o.text("kind")?.takeIf { it == "home" || it == "work" } ?: return null
    val address = o.text("address")?.trim()
    // Hidden control characters mean a tampered request: refuse it (don't treat it as "clear").
    if (address != null && address.any(Char::isISOControl)) return null
    if (address != null && address.isNotEmpty() && address.length !in 2..200) return null
    kind to address?.ifEmpty { null }
}.getOrNull()

/** Largest welcome video the panel accepts (same as the USB import). */
const val PANEL_MAX_VIDEO = 20 * 1024 * 1024

/** MP4 files carry "ftyp" at byte 4. */
fun looksLikeMp4(head: ByteArray): Boolean =
    head.size >= 8 && String(head.copyOfRange(4, 8), Charsets.US_ASCII) == "ftyp"

/** `AA:BB:CC:DD:EE:FF` — the only form a Bluetooth address takes. */
fun isBluetoothAddress(text: String): Boolean = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$").matches(text)

/** `/api/trips/1793040000000/delete` → 1793040000000 (same for fuel). */
fun recordDelete(path: String, kind: String): Long? =
    Regex("^/api/$kind/([0-9]{1,19})/delete$").find(path)?.groupValues?.get(1)?.toLongOrNull()

/** Settings the panel may change, with the values each may take. Anything else is refused. */
private val BOOL_SETTINGS = setOf(
    "autoBrightness", "autoNightPanel", "idleScreen", "autoResume", "wakeToKokpit", "keepHome",
    "autoCarPlayOnUsb", "autoUpdate", "introSound", "introEveryWake",
)

/**
 * Applies a settings change from the panel, e.g. `{"idleMinutes":5,"keepHome":true}`.
 * Returns null — and changes nothing — if any key is unknown or any value out of range.
 */
fun applyPanelSettings(current: io.github.yorulmazsinan.kokpit.settings.KokpitSettings, body: String): io.github.yorulmazsinan.kokpit.settings.KokpitSettings? = runCatching {
    val o = json.parseToJsonElement(body).jsonObject
    var s = current
    for ((key, value) in o) {
        val p = value.jsonPrimitive
        if (key in BOOL_SETTINGS) {
            val b = p.booleanOrNull ?: return null
            s = when (key) {
                "autoBrightness" -> s.copy(autoBrightness = b)
                "autoNightPanel" -> s.copy(autoNightPanel = b)
                "idleScreen" -> s.copy(idleScreen = b)
                "autoResume" -> s.copy(autoResume = b)
                "wakeToKokpit" -> s.copy(wakeToKokpit = b)
                "keepHome" -> s.copy(keepHome = b)
                "autoCarPlayOnUsb" -> s.copy(autoCarPlayOnUsb = b)
                "autoUpdate" -> s.copy(autoUpdate = b)
                "introSound" -> s.copy(intro = s.intro.copy(withSound = b))
                else -> s.copy(intro = s.intro.copy(everyWake = b))
            }
            continue
        }
        s = when (key) {
            "themeMode" -> s.copy(themeMode = io.github.yorulmazsinan.kokpit.home.ThemeMode.valueOf(p.content))
            "accent" -> s.copy(accent = io.github.yorulmazsinan.kokpit.ui.theme.ThemeAccent.valueOf(p.content))
            "introMode" -> s.copy(intro = s.intro.copy(mode = io.github.yorulmazsinan.kokpit.intro.IntroMode.valueOf(p.content)))
            "dayBrightness" -> s.copy(dayBrightness = p.content.toInt().takeIf { it in 10..255 } ?: return null)
            "nightBrightness" -> s.copy(nightBrightness = p.content.toInt().takeIf { it in 10..255 } ?: return null)
            "idleMinutes" -> s.copy(idleMinutes = p.content.toInt().takeIf { it in setOf(1, 2, 5, 10) } ?: return null)
            else -> return null
        }
    }
    s
}.getOrNull()

/** The same settings as JSON, for the panel to show. */
fun panelSettingsJson(s: io.github.yorulmazsinan.kokpit.settings.KokpitSettings): String = kotlinx.serialization.json.buildJsonObject {
    put("themeMode", JsonPrimitive(s.themeMode.name))
    put("accent", JsonPrimitive(s.accent.name))
    put("introMode", JsonPrimitive(s.intro.mode.name))
    put("introSound", JsonPrimitive(s.intro.withSound))
    put("introEveryWake", JsonPrimitive(s.intro.everyWake))
    put("autoBrightness", JsonPrimitive(s.autoBrightness))
    put("dayBrightness", JsonPrimitive(s.dayBrightness))
    put("nightBrightness", JsonPrimitive(s.nightBrightness))
    put("autoNightPanel", JsonPrimitive(s.autoNightPanel))
    put("idleScreen", JsonPrimitive(s.idleScreen))
    put("idleMinutes", JsonPrimitive(s.idleMinutes))
    put("autoResume", JsonPrimitive(s.autoResume))
    put("wakeToKokpit", JsonPrimitive(s.wakeToKokpit))
    put("keepHome", JsonPrimitive(s.keepHome))
    put("autoCarPlayOnUsb", JsonPrimitive(s.autoCarPlayOnUsb))
    put("autoUpdate", JsonPrimitive(s.autoUpdate))
}.toString()

/**
 * The panel's Bluetooth service (RFCOMM). Kokpit Kumanda on the phone uses the same id
 * (kumanda/…/KokpitQr.kt); change both together.
 */
const val PANEL_BT_UUID = "6b6f6b70-6974-4b75-6d61-6e6461000001"

/** Largest request head (request line + headers). */
const val PANEL_MAX_HEAD = 8_192

/**
 * What the unit's QR carries. With Wi-Fi: the panel link for the phone's browser (Kokpit
 * Kumanda reads the same QR and takes the Bluetooth path when `bt` is present). Without
 * Wi-Fi: a `kokpit://` link only Kokpit Kumanda opens. `bt` = the unit's Bluetooth name,
 * empty when unknown, absent when the panel is not listening on Bluetooth.
 */
fun panelQr(ip: String?, port: Int?, token: String, bluetoothName: String?, bluetooth: Boolean): String? {
    val bt = if (bluetooth) "&bt=" + java.net.URLEncoder.encode(bluetoothName.orEmpty(), "UTF-8") else ""
    return when {
        ip != null && port != null -> "http://$ip:$port/?t=$token$bt"
        bluetooth -> "kokpit://panel?t=$token$bt"
        else -> null
    }
}

/** Reads up to and including the blank line after the headers; null if the stream ends or the head is too long. */
fun readPanelHead(input: InputStream): String? {
    val sb = StringBuilder()
    while (sb.length < PANEL_MAX_HEAD) {
        val c = input.read()
        if (c < 0) return null
        sb.append(c.toChar())
        if (sb.endsWith("\r\n\r\n")) return sb.toString()
    }
    return null
}

/** Reads exactly [length] bytes, or null if the stream ends first. */
fun readExactly(input: InputStream, length: Int): ByteArray? {
    val bytes = ByteArray(length)
    var read = 0
    while (read < length) {
        val n = input.read(bytes, read, length - read)
        if (n < 0) return null
        read += n
    }
    return bytes
}

/** One plain response. Every response carries Content-Length, so a kept-open Bluetooth link stays in step. */
fun writePanelReply(out: OutputStream, code: Int, type: String, body: ByteArray) {
    val reason = mapOf(200 to "OK", 400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden", 404 to "Not Found", 411 to "Length Required", 413 to "Payload Too Large")[code] ?: "Error"
    out.write("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n\r\n".toByteArray())
    out.write(body)
    out.flush()
}

/**
 * Reads one request from [input] and hands it, with its whole body, to [handler]. Used by
 * both transports (Wi-Fi: one request per connection; Bluetooth: one after another on the
 * same link). A body is read only for a request that carries the panel's [tokenOk] token,
 * and never beyond [PANEL_MAX_BODY]. Returns false when the link must be closed (stream
 * ended, malformed or refused request).
 */
fun serveOnePanelRequest(
    input: InputStream,
    out: OutputStream,
    tokenOk: (PanelRequest) -> Boolean,
    handler: (PanelRequest, ByteArray, OutputStream) -> Unit,
): Boolean {
    val head = readPanelHead(input) ?: return false
    val req = parsePanelRequest(head) ?: return false
    if (req.headers.containsKey("transfer-encoding")) {
        writePanelReply(out, 411, "text/plain", "length required".toByteArray())
        return false
    }
    val lengthText = req.headers["content-length"]
    val length = if (lengthText == null) 0L else lengthText.toLongOrNull()?.takeIf { it >= 0 } ?: return false
    if (length > PANEL_MAX_BODY) {
        writePanelReply(out, 413, "text/plain; charset=utf-8", "Çok büyük".toByteArray())
        return false
    }
    if (length > 0 && !tokenOk(req)) {
        writePanelReply(out, 403, "text/plain", "forbidden".toByteArray())
        return false
    }
    val body = if (length == 0L) ByteArray(0) else readExactly(input, length.toInt()) ?: return false
    handler(req, body, out)
    return true
}
