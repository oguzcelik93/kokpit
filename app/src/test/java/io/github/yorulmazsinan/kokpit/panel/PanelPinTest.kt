package io.github.yorulmazsinan.kokpit.panel

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PanelPinTest {
    private var now = 1_000_000L
    private fun pin() = PanelPin(ApplicationProvider.getApplicationContext()) { now }

    @Test
    fun weakPinsAreRefused() {
        listOf("0000", "1111", "1234", "4321", "123", "12345", "12a4").forEach { assertFalse(it, isAcceptablePin(it)) }
        assertTrue(isAcceptablePin("4829"))
    }

    @Test
    fun setOnceThenCheck() {
        val p = pin()
        assertFalse(p.isSet)
        assertTrue(p.create("4829"))
        assertTrue(p.isSet)
        assertFalse("second phone can't overwrite", p.create("5930"))
        assertEquals(PinResult.Ok, p.check("4829"))
        assertEquals(PinResult.Wrong(4), p.check("0001"))
    }

    @Test
    fun fiveWrongLockAndLocksGrow() {
        val p = pin()
        p.create("4829")
        repeat(4) { p.check("1111") }
        val locked = p.check("1111")
        assertTrue(locked is PinResult.Locked)
        assertEquals(now + 5 * 60_000L, (locked as PinResult.Locked).untilMs)
        assertTrue("even the right PIN waits", p.check("4829") is PinResult.Locked)
        now += 6 * 60_000L
        repeat(4) { p.check("1111") }
        assertEquals(now + 10 * 60_000L, (p.check("1111") as PinResult.Locked).untilMs)
        now += 11 * 60_000L
        assertEquals(PinResult.Ok, p.check("4829"))
    }

    @Test
    fun resetOnlyFromTheUnit() {
        val p = pin()
        p.create("4829")
        p.reset()
        assertFalse(p.isSet)
        assertTrue(p.create("5930"))
    }

    @Test
    fun lockTimes() {
        assertEquals(5 * 60_000L, lockMillis(0))
        assertEquals(40 * 60_000L, lockMillis(3))
        assertEquals(60 * 60_000L, lockMillis(9))
    }

    @Test
    fun wifiQrEscapes() {
        assertEquals("WIFI:T:WPA;S:AndroidShare_1234;P:ab\\;c\\:d\\\\e;;", wifiQr("AndroidShare_1234", "ab;c:d\\e"))
    }
}
