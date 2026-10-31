package io.github.yorulmazsinan.kokpit.drive

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A location fix stamped with `SystemClock.elapsedRealtime()`, never wall-clock time. */
data class Fix(
    val elapsedMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val speedMps: Float?,
    val bearingDeg: Float?,
)

data class TripSnapshot(
    val speedKmh: Int?,
    val compass: CompassPoint?,
    val averageKmh: Int?,
    val hasFix: Boolean,
    val bearingDeg: Float? = null,
)

/**
 * Average speed = distance ÷ time the unit was awake. Stops while awake count (like the
 * car's trip computer); time asleep does not; a sleep longer than [resetAfterSleepMs] starts
 * a new trip.
 */
class TripTracker(private val resetAfterSleepMs: Long = 30 * 60_000L) {
    var distanceMeters = 0.0
        private set
    private var awakeAccumulatedMs = 0L
    private var awakeSinceMs: Long? = null
    private var sleptAtMs: Long? = null
    private var lastAccepted: Fix? = null
    private var lastFix: Fix? = null
    private var lastSpeedMps: Float? = null

    fun onWake(elapsedMs: Long) {
        val slept = sleptAtMs
        if (slept != null && elapsedMs - slept > resetAfterSleepMs) reset()
        awakeSinceMs = elapsedMs
        sleptAtMs = null
        lastAccepted = null
        lastFix = null
    }

    fun onSleep(elapsedMs: Long) {
        awakeSinceMs?.let { awakeAccumulatedMs += elapsedMs - it }
        awakeSinceMs = null
        sleptAtMs = elapsedMs
    }

    fun onFix(fix: Fix) {
        if (awakeSinceMs == null || fix.accuracyM > MAX_ACCURACY_M) return
        val previous = lastAccepted
        var computedSpeed: Float? = null
        if (previous != null) {
            val meters = haversineMeters(previous.lat, previous.lon, fix.lat, fix.lon)
            val seconds = (fix.elapsedMs - previous.elapsedMs) / 1000.0
            if (seconds <= 0) return
            val kmh = meters / seconds * 3.6
            if (kmh > MAX_PLAUSIBLE_KMH) return
            val moving = fix.speedMps?.let { it >= MIN_MOVING_MPS } ?: (meters >= maxOf(previous.accuracyM, fix.accuracyM))
            if (!moving) {
                // Parked or idling: GPS wanders a few meters; keep the anchor, add nothing.
                lastFix = fix
                lastSpeedMps = fix.speedMps ?: 0f
                return
            }
            distanceMeters += meters
            computedSpeed = (meters / seconds).toFloat()
        }
        lastAccepted = fix
        lastFix = fix
        lastSpeedMps = fix.speedMps ?: computedSpeed
    }

    /** Last accepted position, for coarse uses like weather when no last-known location exists. */
    fun lastPosition(): Pair<Double, Double>? = lastFix?.let { it.lat to it.lon }

    fun snapshot(nowElapsedMs: Long): TripSnapshot {
        val awakeMs = awakeAccumulatedMs + (awakeSinceMs?.let { nowElapsedMs - it } ?: 0L)
        val average = if (awakeMs < MIN_AWAKE_FOR_AVERAGE_MS) null
        else (distanceMeters / awakeMs * 3600.0).roundToInt()
        val fix = lastFix
        val hasFix = fix != null && nowElapsedMs - fix.elapsedMs <= STALE_FIX_MS
        return TripSnapshot(
            speedKmh = if (hasFix) lastSpeedMps?.let { (it * 3.6f).roundToInt() } else null,
            compass = if (hasFix) fix?.bearingDeg?.let(CompassPoint::fromBearing) else null,
            averageKmh = average,
            hasFix = hasFix,
            bearingDeg = if (hasFix) fix?.bearingDeg else null,
        )
    }

    private fun reset() {
        distanceMeters = 0.0
        awakeAccumulatedMs = 0L
        lastSpeedMps = null
    }

    private companion object {
        const val MAX_ACCURACY_M = 50f
        const val MAX_PLAUSIBLE_KMH = 250.0
        const val MIN_MOVING_MPS = 1f
        const val STALE_FIX_MS = 5_000L
        const val MIN_AWAKE_FOR_AVERAGE_MS = 60_000L
        const val EARTH_RADIUS_M = 6_371_000.0

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(a))
        }
    }
}
