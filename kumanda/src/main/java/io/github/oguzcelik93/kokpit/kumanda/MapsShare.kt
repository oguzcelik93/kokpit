package io.github.oguzcelik93.kokpit.kumanda

import java.net.URLDecoder

/** A place shared from a maps app: a point when the link has one, and a name to show. */
data class SharedPlace(val lat: Double?, val lon: Double?, val label: String)

private val URL = Regex("""(https?://\S+|geo:\S+)""")
private val PIN = Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)""")
private val AT = Regex("""@(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)""")
private val PAIR = Regex("""^(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""")

private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

private fun valid(lat: Double, lon: Double) = lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)

private fun query(url: String): Map<String, String> =
    url.substringAfter('?', "").substringBefore('#').split('&').filter { '=' in it }
        .associate { it.substringBefore('=') to decode(it.substringAfter('=')) }

/** The first link in a shared text. */
fun firstLink(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')')

/** A short link that only says where it goes after following it (needs the phone's internet). */
fun isShortMapsLink(url: String): Boolean {
    val host = url.substringAfter("://").substringBefore('/').lowercase()
    return host == "maps.app.goo.gl" || host == "goo.gl" || host == "g.co" || host == "yandex.com.tr" && "/-/" in url
}

/** Coordinates in a maps link (Google, Waze, Yandex, Apple, geo:), or null. */
fun coordinatesIn(url: String): Pair<Double, Double>? {
    fun pair(a: String?, b: String?): Pair<Double, Double>? {
        val lat = a?.toDoubleOrNull() ?: return null
        val lon = b?.toDoubleOrNull() ?: return null
        return (lat to lon).takeIf { valid(lat, lon) }
    }
    PIN.find(url)?.let { m -> pair(m.groupValues[1], m.groupValues[2])?.let { return it } }
    if (url.startsWith("geo:")) {
        PAIR.find(url.removePrefix("geo:"))?.let { m -> pair(m.groupValues[1], m.groupValues[2])?.let { return it } }
        query(url)["q"]?.let { q -> PAIR.find(q)?.let { m -> pair(m.groupValues[1], m.groupValues[2])?.let { return it } } }
        return null
    }
    val q = query(url)
    // Yandex writes longitude first.
    if ("yandex" in url) (q["pt"] ?: q["ll"])?.split(',')?.let { p -> pair(p.getOrNull(1), p.getOrNull(0))?.let { return it } }
    q["to"]?.removePrefix("ll.")?.let { t -> PAIR.find(t)?.let { m -> pair(m.groupValues[1], m.groupValues[2])?.let { return it } } }
    for (key in listOf("q", "query", "destination", "daddr", "ll", "center")) {
        q[key]?.let { v -> PAIR.find(v.removePrefix("loc:"))?.let { m -> pair(m.groupValues[1], m.groupValues[2])?.let { return it } } }
    }
    AT.find(url)?.let { m -> pair(m.groupValues[1], m.groupValues[2])?.let { return it } }
    return null
}

/** A place name in a maps link (`/place/Galata+Kulesi/`, `q=…` that isn't coordinates), or null. */
fun nameIn(url: String): String? {
    Regex("""/place/([^/@?]+)""").find(url)?.let { return decode(it.groupValues[1].replace('+', ' ')).trim().takeIf(String::isNotEmpty) }
    val q = query(url)
    for (key in listOf("q", "query", "text", "destination")) {
        val v = q[key]?.replace('+', ' ')?.trim() ?: continue
        if (v.isNotEmpty() && PAIR.find(v) == null) return v
    }
    return null
}

/**
 * Makes the route to send from a share: [text] is what the maps app shared, [resolved] the
 * address its short link led to (if any). The label is the shared name, the link's place
 * name, or the text itself; no point and no name → null.
 */
fun sharedPlace(text: String, resolved: String?): SharedPlace? {
    val link = firstLink(text)
    val urls = listOfNotNull(resolved, link)
    val point = urls.firstNotNullOfOrNull(::coordinatesIn)
    val written = text.lines().map { URL.replace(it, "").trim() }.filter { it.isNotEmpty() && it.none(Char::isISOControl) }
        .joinToString(", ").take(200).trim(' ', ',')
    val label = written.ifEmpty { urls.firstNotNullOfOrNull(::nameIn).orEmpty() }.take(200)
    if (point == null && label.isEmpty()) return null
    return SharedPlace(point?.first, point?.second, label)
}
