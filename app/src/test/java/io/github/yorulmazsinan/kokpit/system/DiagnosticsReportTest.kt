package io.github.yorulmazsinan.kokpit.system

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {
    private val sample = DiagnosticsReport(
        sdkInt = 27,
        release = "8.1.0",
        model = "8227L",
        hardware = "mt8227l",
        display = "alps-mp-o1.mp5",
        widthPx = 1024,
        heightPx = 600,
        densityDpi = 160,
        wifi5GhzSupported = false,
        bluetoothAdapterPresent = true,
        defaultHomePackage = "com.ts.launcher",
        launchablePackages = listOf("com.zjinnova.zlink", "com.spotify.music"),
        homePackages = listOf("com.ts.launcher", "io.github.yorulmazsinan.kokpit"),
        systemPackages = listOf("com.android.settings", "com.ts.bt"),
    )

    @Test
    fun toTextContainsAllFields() {
        val text = sample.toText()
        listOf(
            "sdk=27", "release=8.1.0", "model=8227L", "hardware=mt8227l", "build=alps-mp-o1.mp5",
            "screen=1024x600", "dpi=160", "wifi5GHz=false", "bt=true", "defaultHome=com.ts.launcher",
        ).forEach { assertTrue("missing $it", it in text) }
        val lines = text.lines()
        listOf("com.zjinnova.zlink", "com.spotify.music", "com.ts.launcher", "com.android.settings", "com.ts.bt")
            .forEach { pkg -> assertTrue("missing line $pkg", lines.any { it.trim() == pkg }) }
    }

    @Test
    fun toTextMarksUnknown5Ghz() {
        assertTrue("wifi5GHz=unknown" in sample.copy(wifi5GhzSupported = null).toText())
    }
}
