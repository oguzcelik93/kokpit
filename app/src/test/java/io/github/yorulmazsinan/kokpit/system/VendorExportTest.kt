package io.github.yorulmazsinan.kokpit.system

import org.junit.Assert.assertEquals
import org.junit.Test

class VendorExportTest {
    @Test
    fun picksHeadUnitVendorPackagesOnly() {
        val all = listOf(
            "com.android.settings", "com.ts.MainUI", "com.ts.carplayapp", "com.autochips.carplay",
            "net.easyconn", "com.google.android.youtube", "com.suding.speedplay", "com.mediatek.filemanager",
        )
        assertEquals(
            listOf("com.autochips.carplay", "com.suding.speedplay", "com.ts.MainUI", "com.ts.carplayapp", "net.easyconn"),
            vendorPackagesToExport(all),
        )
    }

    @Test
    fun exportFileNameIsPackagePlusApk() {
        assertEquals("com.ts.MainUI.apk", exportFileName("com.ts.MainUI"))
    }
}
