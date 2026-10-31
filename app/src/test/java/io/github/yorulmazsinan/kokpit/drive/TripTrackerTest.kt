package io.github.yorulmazsinan.kokpit.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L
private const val METERS_PER_DEG_LAT = 6_371_000.0 * Math.PI / 180.0

class TripTrackerTest {
    private val tracker = TripTracker()
    private var lat = 41.0

    /** Drives north at a constant pace, one fix per second, from [fromMs] to [toMs]. */
    private fun drive(fromMs: Long, toMs: Long, meters: Double) {
        val steps = ((toMs - fromMs) / 1000).toInt()
        val stepDeg = meters / steps / METERS_PER_DEG_LAT
        for (i in 1..steps) {
            lat += stepDeg
            tracker.onFix(Fix(fromMs + i * 1000L, lat, 29.0, accuracyM = 5f, speedMps = null, bearingDeg = 0f))
        }
    }

    private fun stand(fromMs: Long, toMs: Long) {
        var t = fromMs + 1000
        while (t <= toMs) {
            tracker.onFix(Fix(t, lat, 29.0, accuracyM = 5f, speedMps = 0f, bearingDeg = null))
            t += 1000
        }
    }

    @Test
    fun averageCountsStoppedTimeWhileAwake() {
        tracker.onWake(0)
        drive(0, 10 * MIN, 10_000.0)
        stand(10 * MIN, 20 * MIN)
        assertEquals(30, tracker.snapshot(20 * MIN).averageKmh)
    }

    @Test
    fun sleepUnder30MinIsExcludedAndTripContinues() {
        tracker.onWake(0)
        drive(0, 10 * MIN, 10_000.0)
        tracker.onSleep(10 * MIN)
        tracker.onWake(25 * MIN)
        drive(25 * MIN, 35 * MIN, 10_000.0)
        assertEquals(60, tracker.snapshot(35 * MIN).averageKmh)
    }

    @Test
    fun sleepOver30MinResetsTrip() {
        tracker.onWake(0)
        drive(0, 10 * MIN, 10_000.0)
        tracker.onSleep(10 * MIN)
        tracker.onWake(45 * MIN)
        assertNull(tracker.snapshot(45 * MIN + 1000).averageKmh)
    }

    @Test
    fun ignoresInaccurateAndImpossibleFixes() {
        tracker.onWake(0)
        drive(0, 10 * MIN, 10_000.0)
        val t = 10 * MIN
        val before = tracker.distanceMeters
        tracker.onFix(Fix(t + 1000, lat + 0.01, 29.0, accuracyM = 80f, speedMps = null, bearingDeg = null))
        tracker.onFix(Fix(t + 2000, lat + 2_000 / METERS_PER_DEG_LAT, 29.0, accuracyM = 5f, speedMps = null, bearingDeg = null))
        tracker.onFix(Fix(t + 3000, lat, 29.0, accuracyM = 5f, speedMps = 0f, bearingDeg = null))
        assertEquals(60, tracker.snapshot(10 * MIN).averageKmh)
        assertEquals(before, tracker.distanceMeters, 0.001)
    }

    @Test
    fun staleFixMeansNoSpeed() {
        tracker.onWake(0)
        tracker.onFix(Fix(1000, lat, 29.0, accuracyM = 5f, speedMps = 10f, bearingDeg = 90f))
        val fresh = tracker.snapshot(2000)
        assertTrue(fresh.hasFix)
        assertEquals(36, fresh.speedKmh)
        assertEquals(CompassPoint.E, fresh.compass)
        assertEquals(90f, fresh.bearingDeg)
        val stale = tracker.snapshot(7000)
        assertFalse(stale.hasFix)
        assertNull(stale.speedKmh)
    }

    @Test
    fun stationaryJitterAddsNoDistance() {
        tracker.onWake(0)
        val random = java.util.Random(42)
        for (i in 1..600) {
            val dLat = (random.nextDouble() - 0.5) * 10 / METERS_PER_DEG_LAT
            val dLon = (random.nextDouble() - 0.5) * 10 / METERS_PER_DEG_LAT
            val accuracy = 5f + random.nextFloat() * 15f
            tracker.onFix(Fix(i * 1000L, lat + dLat, 29.0 + dLon, accuracy, speedMps = 0.3f, bearingDeg = null))
        }
        assertTrue("jitter added ${tracker.distanceMeters} m", tracker.distanceMeters < 50.0)
    }

    @Test
    fun slowCrawlStillCounts() {
        tracker.onWake(0)
        for (i in 1..120) {
            tracker.onFix(Fix(i * 1000L, lat + i * 2.0 / METERS_PER_DEG_LAT, 29.0, accuracyM = 5f, speedMps = 2f, bearingDeg = 0f))
        }
        assertEquals(238.0, tracker.distanceMeters, 10.0)
    }

    @Test
    fun secondWakeWithoutSleepDoesNotReset() {
        tracker.onWake(0)
        drive(0, 10 * MIN, 10_000.0)
        tracker.onSleep(10 * MIN)
        tracker.onWake(11 * MIN)
        tracker.onWake(50 * MIN)
        assertTrue(tracker.distanceMeters > 9_000)
    }
}
