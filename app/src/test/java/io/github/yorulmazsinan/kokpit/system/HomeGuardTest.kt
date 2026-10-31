package io.github.yorulmazsinan.kokpit.system

import io.github.yorulmazsinan.kokpit.connect.SCREEN_WAKE_MS
import io.github.yorulmazsinan.kokpit.connect.powerSignalFor
import io.github.yorulmazsinan.kokpit.connect.PowerSignal
import io.github.yorulmazsinan.kokpit.connect.screenOffMeansIgnition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGuardTest {
    @Test
    fun guardStopsAfterThreePerMinute() {
        val now = 1_000_000L
        assertTrue(guardAllowed(emptyList(), now))
        assertTrue(guardAllowed(listOf(now - 10_000, now - 5_000), now))
        assertFalse(guardAllowed(listOf(now - 30_000, now - 10_000, now - 5_000), now))
        assertTrue(guardAllowed(listOf(now - 90_000, now - 70_000, now - 5_000), now))
    }

    @Test
    fun screenOffCountsAsIgnitionOnlyWhenLong() {
        assertNull(screenOffMeansIgnition(null, 100_000))
        assertNull(screenOffMeansIgnition(90_000, 100_000))
        assertEquals(SCREEN_WAKE_MS, screenOffMeansIgnition(100_000 - SCREEN_WAKE_MS, 100_000))
        assertNull(screenOffMeansIgnition(200_000, 100_000)) // reboot reset the clock
    }

    @Test
    fun mediatekFastBootSignals() {
        assertEquals(PowerSignal.ON, powerSignalFor("android.intent.action.ACTION_BOOT_IPO"))
        assertEquals(PowerSignal.OFF, powerSignalFor("android.intent.action.ACTION_SHUTDOWN_IPO"))
    }
}
