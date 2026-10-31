package io.github.yorulmazsinan.kokpit.car

import java.net.URLEncoder
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant
import kotlinx.serialization.Serializable

/** A saved destination: GPS point, a typed address, or both (the point wins). */
@Serializable
data class Place(val lat: Double? = null, val lon: Double? = null, val address: String? = null) {
    val hasPoint: Boolean get() = lat != null && lon != null
    val isSet: Boolean get() = hasPoint || !address.isNullOrBlank()
}

@Serializable
data class ParkedSpot(val lat: Double, val lon: Double, val timeMs: Long)

@Serializable
data class FuelEntry(
    val timeMs: Long,
    val odometerKm: Int,
    val liters: Double,
    val totalPrice: Double,
    /** Tank filled to the top: consumption is only exact between two full fill-ups. */
    val full: Boolean = true,
)

data class FuelStats(
    /** L/100 km over the whole log (between the first and last full fill-up). */
    val averageL100: Double?,
    /** L/100 km since the previous full fill-up. */
    val lastL100: Double?,
    val monthCost: Double,
    val costPerKm: Double?,
    val pricePerLiter: Double?,
    val totalKm: Int?,
)

/**
 * Fill-up maths. Liters of a fill-up refill what was burnt since the previous one, so
 * consumption between two full fill-ups = liters added after the first (up to and
 * including the second) ÷ km driven.
 */
fun fuelStats(entries: List<FuelEntry>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): FuelStats {
    val sorted = entries.sortedBy { it.odometerKm }
    val fullIdx = sorted.indices.filter { sorted[it].full }
    fun l100(from: Int, to: Int): Double? {
        val km = sorted[to].odometerKm - sorted[from].odometerKm
        if (km <= 0) return null
        val liters = (from + 1..to).sumOf { sorted[it].liters }
        return liters / km * 100.0
    }
    val average = if (fullIdx.size >= 2) l100(fullIdx.first(), fullIdx.last()) else null
    val last = if (fullIdx.size >= 2) l100(fullIdx[fullIdx.size - 2], fullIdx.last()) else null
    val monthCost = entries.filter { YearMonth.from(Instant.ofEpochMilli(it.timeMs).atZone(zone)) == month }.sumOf { it.totalPrice }
    val totalKm = if (sorted.size >= 2) sorted.last().odometerKm - sorted.first().odometerKm else null
    // The first fill-up's money was spent on fuel burnt before the log started.
    val costPerKm = if (totalKm != null && totalKm > 0) sorted.drop(1).sumOf { it.totalPrice } / totalKm else null
    val liters = entries.sumOf { it.liters }
    val pricePerLiter = if (liters > 0) entries.sumOf { it.totalPrice } / liters else null
    return FuelStats(average, last, monthCost, costPerKm, pricePerLiter, totalKm)
}

/** Parses "45,6" or "45.6" (Turkish keyboards type a comma). */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

/** How to start navigation in a given maps app. */
data class NavSpec(val uri: String, val packageName: String?)

fun navigationSpec(place: Place, mapsPackage: String?, label: String): NavSpec? {
    if (!place.isSet) return null
    val lat = place.lat
    val lon = place.lon
    val address = place.address?.trim().orEmpty()
    val enc = urlEncode(address)
    return when (mapsPackage) {
        "com.waze" -> NavSpec(
            if (lat != null && lon != null) "https://waze.com/ul?ll=$lat,$lon&navigate=yes" else "https://waze.com/ul?q=$enc&navigate=yes",
            "com.waze",
        )
        "ru.yandex.yandexnavi" -> NavSpec(
            if (lat != null && lon != null) "yandexnavi://build_route_on_map?lat_to=$lat&lon_to=$lon" else "yandexnavi://map_search?text=$enc",
            mapsPackage,
        )
        "com.google.android.apps.maps" -> NavSpec(
            "google.navigation:q=" + if (lat != null && lon != null) "$lat,$lon" else enc,
            mapsPackage,
        )
        else -> NavSpec(
            if (lat != null && lon != null) "geo:$lat,$lon?q=$lat,$lon(${urlEncode(label)})" else "geo:0,0?q=$enc",
            mapsPackage,
        )
    }
}

private fun urlEncode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

/** Link a phone opens in its maps app (QR code content). */
fun mapsLink(lat: Double, lon: Double): String = "https://maps.google.com/?q=%.6f,%.6f".format(java.util.Locale.US, lat, lon)

enum class DayPhase { DAY, NIGHT }

/** Without weather the sun is assumed up 07:00–19:00. */
fun dayPhase(now: LocalTime, sunrise: LocalTime?, sunset: LocalTime?): DayPhase {
    val up = sunrise ?: LocalTime.of(7, 0)
    val down = sunset ?: LocalTime.of(19, 0)
    return if (now >= up && now < down) DayPhase.DAY else DayPhase.NIGHT
}

private val HOME_WORDS = setOf("ev", "eve", "evim", "evime", "evimize", "ev adresi", "ev adresim", "home", "my home")
private val WORK_WORDS = setOf("iş", "işe", "işim", "işime", "iş yeri", "iş yerim", "iş yerime", "işyeri", "işyerim", "işyerime", "ofis", "ofise", "ofisim", "ofisime", "büro", "work", "office")

/** "eve", "Evime", "işyerime", "ofis" → the saved place; anything else is a real destination. */
fun savedPlaceFor(destination: String): PlaceKind? {
    val d = destination.trim().lowercase(java.util.Locale.forLanguageTag("tr")).trimEnd('.', '!', '?').removeSuffix(" git").trim()
    return when (d) {
        in HOME_WORDS -> PlaceKind.HOME
        in WORK_WORDS -> PlaceKind.WORK
        else -> null
    }
}
