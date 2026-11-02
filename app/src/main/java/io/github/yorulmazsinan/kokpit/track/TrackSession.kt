package io.github.yorulmazsinan.kokpit.track

import kotlin.math.cos
import kotlin.math.sqrt

/** One GPS point on track: elapsed-realtime ms, position, speed (km/h). */
data class TrackFix(val timeMs: Long, val lat: Double, val lon: Double, val speedKmh: Double)

/** The start/finish line: a point the lap is timed through. */
data class StartLine(val lat: Double, val lon: Double)

/** A lap shorter than this (ms) is a GPS wobble at the line, not a lap. */
const val MIN_LAP_MS = 20_000L

/** Passing within this many metres of the line point counts as crossing it … */
const val LINE_RADIUS_M = 30.0

/** … once the car has been at least this far away since the last crossing. */
const val ARM_DISTANCE_M = 150.0

/** Metres east/north of [origin] (flat-earth, plenty for a race track). */
fun localMeters(origin: StartLine, lat: Double, lon: Double): Pair<Double, Double> {
    val k = 111_320.0
    return (lon - origin.lon) * k * cos(Math.toRadians(origin.lat)) to (lat - origin.lat) * k
}

/**
 * Where on the segment a→b the car passed closest to the line point, as a fraction 0..1,
 * and how close (m). Lets a 1 Hz GPS time a lap far finer than a second.
 */
fun closestApproach(line: StartLine, a: TrackFix, b: TrackFix): Pair<Double, Double> {
    val (ax, ay) = localMeters(line, a.lat, a.lon)
    val (bx, by) = localMeters(line, b.lat, b.lon)
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
    val px = ax + t * dx
    val py = ay + t * dy
    return t to sqrt(px * px + py * py)
}

/** What the track screen shows. Times in ms; null = not yet. */
data class TrackState(
    val line: StartLine? = null,
    val lapStartMs: Long? = null,
    val laps: List<Long> = emptyList(),
    val maxKmh: Double = 0.0,
    val zeroToHundredMs: Long? = null,
    val bestZeroToHundredMs: Long? = null,
) {
    val bestLap: Long? get() = laps.minOrNull()
    val lastLap: Long? get() = laps.lastOrNull()
}

/**
 * Pist modu's timing, fed one GPS fix at a time. Laps: "Çizgi burası" starts the clock at
 * the car's spot; each later pass through it (after going [ARM_DISTANCE_M] away) closes a
 * lap, timed at the interpolated closest point. 0–100: armed while standing, measured from
 * the moment the car moves until it reaches 100 km/h (interpolated).
 */
class TrackSession(initial: TrackState = TrackState()) {
    var state: TrackState = initial
        private set

    private var last: TrackFix? = null
    private var armed = false
    private var launchMs: Long? = null
    private var readyToLaunch = false

    /** "Başlangıç çizgisi burası": the line is where the car is, and the lap clock starts now. */
    fun setLine(at: TrackFix) {
        state = state.copy(line = StartLine(at.lat, at.lon), lapStartMs = at.timeMs, laps = emptyList())
        armed = false
        last = at
    }

    /** "Sıfırla": laps, maximum and 0–100 start over; the line stays. */
    fun reset(nowMs: Long) {
        state = TrackState(line = state.line, lapStartMs = state.line?.let { nowMs })
        armed = false
        launchMs = null
        readyToLaunch = false
    }

    fun onFix(f: TrackFix) {
        val prev = last
        last = f
        state = state.copy(maxKmh = maxOf(state.maxKmh, f.speedKmh))
        zeroToHundred(prev, f)
        val line = state.line ?: return
        if (prev == null) return
        val (_, distNow) = closestApproach(line, f, f)
        if (distNow > ARM_DISTANCE_M) armed = true
        if (!armed) return
        val (t, closest) = closestApproach(line, prev, f)
        // The closest point must lie inside this segment (not at its far end, where the car is still approaching).
        if (closest > LINE_RADIUS_M || t >= 1.0) return
        val crossMs = prev.timeMs + Math.round(t * (f.timeMs - prev.timeMs))
        val start = state.lapStartMs
        if (start == null) {
            state = state.copy(lapStartMs = crossMs)
        } else if (crossMs - start >= MIN_LAP_MS) {
            state = state.copy(laps = state.laps + (crossMs - start), lapStartMs = crossMs)
        } else return
        armed = false
    }

    private fun zeroToHundred(prev: TrackFix?, f: TrackFix) {
        if (f.speedKmh < 2.0) {
            readyToLaunch = true
            launchMs = null
            return
        }
        if (prev == null) return
        if (readyToLaunch && launchMs == null) {
            launchMs = prev.timeMs
            readyToLaunch = false
        }
        val start = launchMs ?: return
        if (prev.speedKmh < 100.0 && f.speedKmh >= 100.0) {
            val frac = (100.0 - prev.speedKmh) / (f.speedKmh - prev.speedKmh)
            val at = prev.timeMs + Math.round(frac * (f.timeMs - prev.timeMs))
            val run = at - start
            state = state.copy(zeroToHundredMs = run, bestZeroToHundredMs = minOf(run, state.bestZeroToHundredMs ?: Long.MAX_VALUE))
            launchMs = null
        }
    }
}

/** 83456 → "1:23,45"; 9120 → "9,12". */
fun formatLap(ms: Long?): String {
    if (ms == null || ms < 0) return "—"
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1000
    val hundredths = (ms % 1000) / 10
    return if (minutes > 0) "%d:%02d,%02d".format(minutes, seconds, hundredths) else "%d,%02d".format(seconds, hundredths)
}

/** Live gap to the best lap at the same elapsed time can't be known without a reference trace; this is the plain lap delta. */
fun formatDelta(ms: Long?): String = when {
    ms == null -> ""
    ms < 0 -> "−" + formatLap(-ms)
    else -> "+" + formatLap(ms)
}
