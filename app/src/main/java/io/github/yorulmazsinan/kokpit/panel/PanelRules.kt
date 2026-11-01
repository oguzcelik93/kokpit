package io.github.yorulmazsinan.kokpit.panel

import io.github.yorulmazsinan.kokpit.car.FuelEntry
import io.github.yorulmazsinan.kokpit.car.parseDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
