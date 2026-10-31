package io.github.yorulmazsinan.kokpit.system

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SystemShortcutsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun screenLockShortcutComesFirst() {
        assertEquals(DevicePolicyManager.ACTION_SET_NEW_PASSWORD, systemShortcuts().first().intent.action)
    }

    @Test
    fun containsExpectedActions() {
        val actions = systemShortcuts().map { it.intent.action }.toSet()
        listOf(
            Settings.ACTION_SETTINGS,
            Settings.ACTION_WIFI_SETTINGS,
            Settings.ACTION_BLUETOOTH_SETTINGS,
            Settings.ACTION_APPLICATION_SETTINGS,
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            Settings.ACTION_DATE_SETTINGS,
            Settings.ACTION_LOCALE_SETTINGS,
        ).forEach { assertTrue("missing $it", it in actions) }
    }

    @Test
    fun resolvableDropsUnresolved() {
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "com.android.settings.Settings"
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(settingsIntent, info)

        val result = systemShortcuts().resolvable(app.packageManager)

        assertEquals(listOf(Settings.ACTION_SETTINGS), result.map { it.intent.action })
    }

    @Test
    fun safeStartReturnsFalseWhenNothingHandles() {
        shadowOf(app).checkActivities(true)
        val started = safeStart(app, Intent().setComponent(ComponentName("no.such", "no.such.Activity")))
        assertFalse(started)
    }
}
