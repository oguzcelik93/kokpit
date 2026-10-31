package io.github.yorulmazsinan.kokpit.car

import android.content.Context
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One ignition-on → ignition-off drive. */
@Serializable
data class TripRecord(val startMs: Long, val endMs: Long, val meters: Double) {
    val km: Double get() = meters / 1000.0
    val minutes: Long get() = (endMs - startMs) / 60_000
    val averageKmh: Double? get() = if (endMs - startMs >= 60_000) km / ((endMs - startMs) / 3_600_000.0) else null
}

/** The drive in progress; checkpointed so a power cut without a sleep signal loses at most a minute. */
@Serializable
data class OpenTrip(val startMs: Long, val startMeters: Double, val lastMs: Long, val lastMeters: Double)

data class KmTotals(val todayKm: Double, val weekKm: Double, val monthKm: Double, val totalKm: Double, val trips: Int)

/** Trips shorter than this (GPS wander, moving the car a few metres) are not logged. */
const val MIN_TRIP_METERS = 200.0

fun closeTrip(open: OpenTrip, endMs: Long, meters: Double): TripRecord? {
    val driven = (meters - open.startMeters).coerceAtLeast(0.0)
    return if (driven < MIN_TRIP_METERS) null else TripRecord(open.startMs, endMs, driven)
}

fun kmTotals(trips: List<TripRecord>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): KmTotals {
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun day(t: TripRecord) = Instant.ofEpochMilli(t.startMs).atZone(zone).toLocalDate()
    return KmTotals(
        todayKm = trips.filter { day(it) == today }.sumOf { it.km },
        weekKm = trips.filter { !day(it).isBefore(weekStart) && !day(it).isAfter(today) }.sumOf { it.km },
        monthKm = trips.filter { day(it).year == today.year && day(it).month == today.month }.sumOf { it.km },
        totalKm = trips.sumOf { it.km },
        trips = trips.size,
    )
}

/**
 * Km log fed by the ignition: [begin] on wake (or process start), [checkpoint] every minute,
 * [end] on sleep. Distances come from the GPS trip tracker.
 */
class TripLog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("trips", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(TripRecord.serializer())

    private val _trips = MutableStateFlow(
        prefs.getString(KEY_TRIPS, null)?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList(),
    )
    val trips: StateFlow<List<TripRecord>> = _trips.asStateFlow()

    private var open: OpenTrip? = prefs.getString(KEY_OPEN, null)?.let { runCatching { json.decodeFromString(OpenTrip.serializer(), it) }.getOrNull() }

    /** A new drive starts; a drive left open (power cut, process restart) is closed at its last checkpoint. */
    fun begin(meters: Double, nowMs: Long = System.currentTimeMillis()) {
        open?.let { record(closeTrip(it, it.lastMs, it.lastMeters)) }
        setOpen(OpenTrip(nowMs, meters, nowMs, meters))
    }

    fun checkpoint(meters: Double, nowMs: Long = System.currentTimeMillis()) {
        val o = open ?: return
        setOpen(o.copy(lastMs = nowMs, lastMeters = meters), commit = false)
    }

    /** Ignition off: returns the finished trip, if it was long enough to log. */
    fun end(meters: Double, nowMs: Long = System.currentTimeMillis()): TripRecord? {
        val o = open ?: return null
        val trip = closeTrip(o, nowMs, meters)
        record(trip)
        setOpen(null)
        return trip
    }

    fun delete(trip: TripRecord) = save(_trips.value - trip)

    private fun record(trip: TripRecord?) {
        if (trip != null) save((listOf(trip) + _trips.value).take(MAX_TRIPS))
    }

    private fun save(list: List<TripRecord>) {
        prefs.edit().putString(KEY_TRIPS, json.encodeToString(listSerializer, list)).commit()
        _trips.value = list
    }

    private fun setOpen(value: OpenTrip?, commit: Boolean = true) {
        open = value
        val edit = prefs.edit()
        if (value == null) edit.remove(KEY_OPEN) else edit.putString(KEY_OPEN, json.encodeToString(OpenTrip.serializer(), value))
        if (commit) edit.commit() else edit.apply()
    }

    private companion object {
        const val KEY_TRIPS = "trips"
        const val KEY_OPEN = "open_trip"
        const val MAX_TRIPS = 1000
    }
}
