package io.github.yorulmazsinan.kokpit.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TrackSessionTest {
    private val cLat = 40.95
    private val cLon = 29.40
    private val r = 300.0 // circular track, ~1885 m

    /** A point on the circle at [angle] radians, east/north metres turned into degrees. */
    private fun at(t: Long, angle: Double, kmh: Double = 100.0): TrackFix {
        val k = 111_320.0
        val lat = cLat + r * sin(angle) / k
        val lon = cLon + r * cos(angle) / (k * cos(Math.toRadians(cLat)))
        return TrackFix(t, lat, lon, kmh)
    }

    @Test
    fun lapsAreTimedFinerThanTheFixRate() {
        val s = TrackSession()
        // 1 Hz fixes, one lap every 67.3 s; the line is set at angle 0 at t=0.
        val lapMs = 67_300.0
        s.setLine(at(0, 0.0))
        var t = 1_000L
        while (t <= 210_000L) {
            s.onFix(at(t, 2 * Math.PI * t / lapMs))
            t += 1_000
        }
        assertEquals(3, s.state.laps.size)
        s.state.laps.forEach { assertTrue("lap $it", kotlin.math.abs(it - 67_300) < 150) }
    }

    @Test
    fun wobbleAtTheLineIsNotALap() {
        val s = TrackSession()
        s.setLine(at(0, 0.0))
        for (i in 1..30) s.onFix(at(i * 1_000L, if (i % 2 == 0) 0.01 else -0.01, 5.0))
        assertTrue(s.state.laps.isEmpty())
    }

    @Test
    fun zeroToHundredInterpolated() {
        val s = TrackSession()
        s.onFix(TrackFix(0, cLat, cLon, 0.0))
        s.onFix(TrackFix(1_000, cLat, cLon, 10.0))
        // +12 km/h a second from 10 at t=1 s → 100 at 8.5 s; launch counted from the last standing fix (t=0).
        var v = 10.0
        var t = 1_000L
        while (v < 110) { t += 1_000; v += 12; s.onFix(TrackFix(t, cLat, cLon, v)) }
        assertEquals(8_500L, s.state.zeroToHundredMs)
        assertEquals(8_500L, s.state.bestZeroToHundredMs)
        assertTrue(s.state.maxKmh >= 100)
    }

    @Test
    fun resetKeepsTheLine() {
        val s = TrackSession()
        s.setLine(at(0, 0.0))
        s.onFix(at(1_000, 0.1, 150.0))
        s.reset(5_000)
        assertEquals(0.0, s.state.maxKmh, 0.0)
        assertEquals(5_000L, s.state.lapStartMs)
        assertTrue(s.state.line != null)
    }

    @Test
    fun formats() {
        assertEquals("1:23,45", formatLap(83_456))
        assertEquals("9,12", formatLap(9_120))
        assertEquals("—", formatLap(null))
        assertEquals("−0,50", formatDelta(-500))
        assertEquals("+1,00", formatDelta(1_000))
        assertEquals(23.5, parseTemperature("23.5°C")!!, 0.0)
        assertEquals(-4.0, parseTemperature("-4 C")!!, 0.0)
        assertNull(parseTemperature("--"))
        assertEquals("Açık", formatSignal(Signal.REVERSE, 1.0))
        assertEquals("1,25", formatSignal(Signal.BOOST, 1.25))
        assertEquals("—", formatSignal(Signal.RPM, null))
    }

    @Test
    fun signalStateLearnsWhatIsLive() {
        val a = SignalState().next(null)
        assertTrue(!a.answered)
        val b = a.next(0.0).next(0.0)
        assertTrue(b.answered && !b.changed)
        assertTrue(b.next(1.0).changed)
    }
}
