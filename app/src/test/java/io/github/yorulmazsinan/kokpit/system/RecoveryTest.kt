package io.github.yorulmazsinan.kokpit.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryTest {
    @Test
    fun threeCrashesInsideWindowIsALoop() {
        val now = 1_000_000L
        assertTrue(isCrashLoop(listOf(now - 100_000, now - 50_000, now), now))
    }

    @Test
    fun oldCrashesDoNotCount() {
        val now = 1_000_000L
        assertFalse(isCrashLoop(listOf(now - CRASH_WINDOW_MS - 1, now - 50_000, now), now))
        assertEquals(2, recentCrashes(listOf(now - CRASH_WINDOW_MS - 1, now - 50_000, now), now).size)
    }

    @Test
    fun triggerFilesMatchWithOrWithoutExtension() {
        assertEquals(UsbTrigger.RECOVER, triggerFor("KOKPIT-KURTARMA"))
        assertEquals(UsbTrigger.RECOVER, triggerFor("kokpit-kurtarma.txt"))
        assertEquals(UsbTrigger.RESET, triggerFor("KOKPIT-SIFIRLA.TXT"))
        assertEquals(UsbTrigger.NORMAL, triggerFor("Kokpit-Normal"))
        assertNull(triggerFor("KOKPIT-KURTARMA-SONUC.txt"))
        assertNull(triggerFor("music.mp3"))
    }
}
