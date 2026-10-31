package io.github.yorulmazsinan.kokpit.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAdbTest {
    @Test
    fun onlyPlainPackageNamesPass() {
        assertTrue(isSafePackageName("com.mediatek.mtklogger"))
        assertTrue(isSafePackageName("io.github.oguzcelik93.kokpit93"))
        assertFalse(isSafePackageName("com.x; reboot"))
        assertFalse(isSafePackageName("com.x && rm -rf /sdcard"))
        assertFalse(isSafePackageName("com.x\nreboot"))
        assertFalse(isSafePackageName("\$(reboot)"))
        assertFalse(isSafePackageName("nodots"))
        assertFalse(isSafePackageName(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun commandsRefuseAnythingElse() {
        AdbCommands.disable("com.x;reboot")
    }

    @Test
    fun commandsAreFixed() {
        assertEquals("pm disable-user --user 0 com.android.egg", AdbCommands.disable("com.android.egg"))
        assertEquals("pm enable com.android.egg", AdbCommands.enable("com.android.egg"))
        assertEquals("pm grant a.b android.permission.WRITE_SECURE_SETTINGS", AdbCommands.grantSecureSettings("a.b"))
    }

    @Test
    fun packageListParsing() {
        assertEquals(setOf("com.a.b", "com.c.d"), parsePackageList("package:com.a.b\r\npackage:com.c.d\n\npackage:bad name\n"))
    }
}
