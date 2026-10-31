package io.github.yorulmazsinan.kokpit.car

import io.github.yorulmazsinan.kokpit.home.cards.idleOffset
import io.github.yorulmazsinan.kokpit.home.cards.shouldIdle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripAndPlaceTest {
    private fun at(d: Int, h: Int) = LocalDateTime.of(2026, 10, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun shortHopsAreNotTrips() {
        val open = OpenTrip(0, 1_000.0, 0, 1_000.0)
        assertNull(closeTrip(open, 60_000, 1_150.0))
        assertEquals(5_000.0, closeTrip(open, 600_000, 6_000.0)!!.meters, 0.0)
        assertNull(closeTrip(open, 600_000, 500.0)) // tracker reset: never negative
    }

    @Test
    fun tripNumbers() {
        val t = TripRecord(0, 30 * 60_000L, 25_000.0)
        assertEquals(25.0, t.km, 0.0)
        assertEquals(30, t.minutes)
        assertEquals(50.0, t.averageKmh!!, 0.001)
        assertEquals("1 sa 5 dk", formatMinutes(65))
    }

    @Test
    fun totalsByDayWeekMonth() {
        // 31 Oct 2026 is a Saturday; the week started Monday 26 Oct.
        val trips = listOf(
            TripRecord(at(31, 9), at(31, 10), 10_000.0),
            TripRecord(at(27, 9), at(27, 10), 20_000.0),
            TripRecord(at(20, 9), at(20, 10), 40_000.0),
            TripRecord(LocalDateTime.of(2026, 9, 30, 9, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), 0, 80_000.0),
        )
        val k = kmTotals(trips, LocalDate.of(2026, 10, 31), ZoneOffset.UTC)
        assertEquals(10.0, k.todayKm, 0.0)
        assertEquals(30.0, k.weekKm, 0.0)
        assertEquals(70.0, k.monthKm, 0.0)
        assertEquals(150.0, k.totalKm, 0.0)
    }

    @Test
    fun assistantPlaceWords() {
        assertEquals(PlaceKind.HOME, savedPlaceFor("eve"))
        assertEquals(PlaceKind.HOME, savedPlaceFor("Evime."))
        assertEquals(PlaceKind.WORK, savedPlaceFor("İşe"))
        assertEquals(PlaceKind.WORK, savedPlaceFor("işyerime"))
        assertEquals(PlaceKind.WORK, savedPlaceFor("ofis"))
        assertNull(savedPlaceFor("Ev Eşyaları Mağazası Kadıköy"))
        assertNull(savedPlaceFor("en yakın benzinlik"))
    }

    @Test
    fun lastSourceRoundTrip() {
        listOf(LastSource.None, LastSource.Radio, LastSource.Bluetooth, LastSource.App("com.spotify.music")).forEach {
            assertEquals(it, LastSource.decode(it.encode()))
        }
        assertEquals(LastSource.None, LastSource.decode(null))
        assertEquals(LastSource.None, LastSource.decode("app:"))
    }

    @Test
    fun idleRules() {
        assertTrue(shouldIdle(true, true, 120_000, 2, null))
        assertTrue(shouldIdle(true, true, 120_000, 2, 0))
        assertFalse(shouldIdle(true, true, 119_000, 2, 0))
        assertFalse(shouldIdle(true, false, 600_000, 2, 0))
        assertFalse(shouldIdle(true, true, 600_000, 2, 40))
        assertFalse(shouldIdle(false, true, 600_000, 2, 0))
        assertEquals(0 to 0, idleOffset(0))
        assertEquals(idleOffset(1), idleOffset(7))
    }
}
