package io.github.yorulmazsinan.kokpit.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerSignalsTest {
    @Test
    fun autochipsQuickBootMeansIgnition() {
        assertEquals(PowerSignal.ON, powerSignalFor("autochips.intent.action.QB_POWERON"))
        assertEquals(PowerSignal.OFF, powerSignalFor("autochips.intent.action.QB_POWEROFF"))
    }

    @Test
    fun otherVendorPowerBroadcasts() {
        assertEquals(PowerSignal.ON, powerSignalFor("comm.power.POWER_ON"))
        assertEquals(PowerSignal.OFF, powerSignalFor("comm.power.POWER_OFF"))
        assertEquals(PowerSignal.OFF, powerSignalFor("com.cusc.action.ACC_OFF"))
    }

    @Test
    fun screenEventsAreNotIgnition() {
        assertNull(powerSignalFor("android.intent.action.SCREEN_ON"))
        assertNull(powerSignalFor(null))
    }

    @Test
    fun sleepDurationSurvivesProcessRestart() {
        assertEquals(40 * 60_000L, sleptFor(storedSleepAt = 10 * 60_000L, nowElapsed = 50 * 60_000L))
        assertNull(sleptFor(storedSleepAt = null, nowElapsed = 1000L))
        assertNull("after a real reboot elapsed time restarts", sleptFor(storedSleepAt = 50_000L, nowElapsed = 1000L))
    }

    @Test
    fun heartbeatGapMeansTheUnitSlept() {
        assertEquals(10 * 60_000L, heartbeatGap(lastBeat = 60_000L, nowElapsed = 11 * 60_000L))
        assertNull("a normal beat", heartbeatGap(lastBeat = 60_000L, nowElapsed = 76_000L))
        assertNull("first beat", heartbeatGap(lastBeat = null, nowElapsed = 76_000L))
        assertNull("reboot resets the clock", heartbeatGap(lastBeat = 900_000L, nowElapsed = 20_000L))
    }

    @Test
    fun oneIgnitionIsHandledOnce() {
        assertEquals(false, isRepeatWake(lastWake = null, nowElapsed = 5_000L))
        assertEquals(true, isRepeatWake(lastWake = 5_000L, nowElapsed = 30_000L))
        assertEquals(false, isRepeatWake(lastWake = 5_000L, nowElapsed = 5_000L + WAKE_DEBOUNCE_MS))
        assertEquals(false, isRepeatWake(lastWake = 900_000L, nowElapsed = 10_000L))
    }

    @Test
    fun noIntroWhileMoving() {
        assertEquals(true, introAllowedAt(null))
        assertEquals(true, introAllowedAt(0))
        assertEquals(false, introAllowedAt(45))
    }
}
