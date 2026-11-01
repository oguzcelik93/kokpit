package io.github.yorulmazsinan.kokpit.time

import io.github.yorulmazsinan.kokpit.connect.PairedPhone
import io.github.yorulmazsinan.kokpit.connect.connectionOrder
import io.github.yorulmazsinan.kokpit.intro.INTRO_REPEAT_GUARD_MS
import io.github.yorulmazsinan.kokpit.intro.introRecentlyPlayed
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrueTimeTest {
    @Test
    fun rmcGivesUtc() {
        val ms = parseRmcUtc("\$GNRMC,203512.40,A,4836.1234,N,00841.2345,E,0.0,,011126,,,A*7C")
        assertEquals(Instant.parse("2026-11-01T20:35:12.400Z").toEpochMilli(), ms)
        assertEquals(Instant.parse("2026-11-01T20:35:12Z").toEpochMilli(), parseRmcUtc("\$GPRMC,203512,A,1,N,2,E,0,0,011126,,"))
    }

    @Test
    fun noFixNoTime() {
        assertNull(parseRmcUtc("\$GPRMC,203512.00,V,,,,,,,011126,,,N*53"))
        assertNull(parseRmcUtc("\$GPGGA,203512.00,4836.1,N,00841.2,E,1,08,0.9,545.4,M,46.9,M,,*47"))
        assertNull(parseRmcUtc("\$GPRMC,2035,A,1,N,2,E,0,0,0111,,"))
        assertNull(parseRmcUtc("garbage"))
    }

    @Test
    fun smallDifferencesAreIgnored() {
        assertEquals(0L, clockOffset(1_000_000, 999_000))
        // Unit woke up on 1 August, real time is 1 November.
        val real = Instant.parse("2026-11-01T20:58:00Z").toEpochMilli()
        val unit = Instant.parse("2026-08-01T02:06:00Z").toEpochMilli()
        assertEquals(real - unit, clockOffset(real, unit))
    }

    @Test
    fun recentPhoneFirst() {
        val order = connectionOrder(
            listOf(PairedPhone("A", "Telefon 1", false, 100), PairedPhone("B", "Telefon 2", false, 900), PairedPhone("C", "Yeni", false, 0)),
        )
        assertEquals(listOf("B", "A", "C"), order.map { it.address })
    }

    @Test
    fun introPlaysOnce() {
        assertFalse(introRecentlyPlayed(null, 5_000))
        assertTrue(introRecentlyPlayed(1_000, 1_000 + INTRO_REPEAT_GUARD_MS - 1))
        assertFalse(introRecentlyPlayed(1_000, 1_000 + INTRO_REPEAT_GUARD_MS))
        assertFalse(introRecentlyPlayed(9_000, 5_000)) // reboot
    }
}
