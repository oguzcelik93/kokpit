package io.github.yorulmazsinan.kokpit.carplay

import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.dock.resolveDockTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarPlayTest {
    @Test
    fun firmwareCarPlayIsPreferredOverZlink() {
        val installed = setOf("com.zjinnova.zlink", "com.ts.carplayapp")
        assertEquals(EngineKind.NATIVE, preferredEngine(installed)?.kind)
        assertEquals("com.ts.carplayapp/com.autochips.carplayapp.LoadingActivity", resolveDockTarget(DockSlot.CARPLAY, installed, null))
    }

    @Test
    fun zlinkWhenNothingElse() {
        assertEquals(EngineKind.ZLINK, preferredEngine(setOf("com.zjinnova.zlink"))?.kind)
        assertNull(preferredEngine(emptySet()))
    }

    @Test
    fun pickedScreenMapsBackToItsEngine() {
        val engine = engineFor("com.zjinnova.zlink/com.zjinnova.zlink.MainActivity", setOf("com.zjinnova.zlink"))
        assertEquals(EngineKind.ZLINK, engine?.kind)
    }
}
