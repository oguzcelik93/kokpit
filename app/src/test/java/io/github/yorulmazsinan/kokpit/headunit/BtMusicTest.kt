package io.github.yorulmazsinan.kokpit.headunit

import org.junit.Assert.assertEquals
import org.junit.Test

class BtMusicTest {
    @Test
    fun readsTheHeadUnitsAvrcpStatus() {
        assertEquals(true, avrcpPlaying(1))
        assertEquals(false, avrcpPlaying(0))
        assertEquals(false, avrcpPlaying(2))
        assertEquals(null, avrcpPlaying(null))
    }

    @Test
    fun bluetoothOrNoSessionGoesThroughTheHeadUnit() {
        assertEquals(true, usesHeadUnitMusic(null))
        assertEquals(true, usesHeadUnitMusic(BLUETOOTH_SESSION_PACKAGE))
        assertEquals(false, usesHeadUnitMusic("com.spotify.music"))
    }
}
