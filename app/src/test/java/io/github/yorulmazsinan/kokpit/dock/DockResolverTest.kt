package io.github.yorulmazsinan.kokpit.dock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockResolverTest {
    @Test
    fun usesOverrideWhenInstalled() {
        val installed = setOf("com.zjinnova.zlink", "com.example.autokit")
        assertEquals("com.example.autokit", resolveDockTarget(DockSlot.CARPLAY, installed, override = "com.example.autokit"))
    }

    @Test
    fun fallsBackWhenOverrideNotInstalled() {
        val installed = setOf("ru.yandex.yandexnavi", "com.waze")
        assertEquals("ru.yandex.yandexnavi", resolveDockTarget(DockSlot.MAPS, installed, override = "com.removed.app"))
    }

    @Test
    fun nullWhenNothingInstalled() {
        assertNull(resolveDockTarget(DockSlot.CARPLAY, emptySet(), override = null))
    }

    @Test
    fun appsSlotAlwaysNull() {
        assertNull(resolveDockTarget(DockSlot.APPS, setOf("com.anything"), override = "com.anything"))
    }

    @Test
    fun assistantSlotHasNoCandidatesAndIsNotInDock() {
        assertNull(resolveDockTarget(DockSlot.ASSISTANT, setOf("com.openai.chatgpt"), override = null))
        assertEquals("com.openai.chatgpt", resolveDockTarget(DockSlot.ASSISTANT, setOf("com.openai.chatgpt"), override = "com.openai.chatgpt"))
        assertEquals(6, DockSlot.entries.count { it.inDock })
    }

    @Test
    fun componentOverrideIsKeptWhenItsPackageIsInstalled() {
        val radio = "com.ts.MainUI/com.ts.main.radio.RadioActivity"
        assertEquals(radio, resolveDockTarget(DockSlot.RADIO, setOf("com.ts.MainUI"), override = radio))
        assertNull(resolveDockTarget(DockSlot.RADIO, setOf("com.other"), override = radio))
    }

    @Test
    fun headUnitCarPlayOpensPhoneLinkNotEasyConnection() {
        assertEquals(
            "com.ts.carplayapp/com.autochips.carplayapp.LoadingActivity",
            resolveDockTarget(DockSlot.CARPLAY, setOf("net.easyconn", "com.ts.carplayapp"), override = null),
        )
    }

    @Test
    fun headUnitVendorScreensAreDefaults() {
        val installed = setOf("com.ts.MainUI")
        assertEquals("com.ts.MainUI/com.ts.main.radio.RadioMainActivity", resolveDockTarget(DockSlot.RADIO, installed, null))
        assertEquals("com.ts.MainUI/com.ts.bt.BtMusicActivity", resolveDockTarget(DockSlot.BLUETOOTH, installed, null))
        assertEquals("com.ts.MainUI/com.ts.bt.BtConnectActivity", resolveDockTarget(DockSlot.PHONE, installed, null))
    }

    @Test
    fun targetIntentLaunchesComponentOrPackage() {
        assertEquals(DockTarget.Component("com.ts.MainUI", "com.ts.main.radio.RadioActivity"), parseDockTarget("com.ts.MainUI/com.ts.main.radio.RadioActivity"))
        assertEquals(DockTarget.Package("com.ts.carplayapp"), parseDockTarget("com.ts.carplayapp"))
    }
}
