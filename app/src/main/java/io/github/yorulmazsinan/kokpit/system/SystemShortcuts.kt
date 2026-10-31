package io.github.yorulmazsinan.kokpit.system

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import io.github.yorulmazsinan.kokpit.R

data class SystemShortcut(val id: String, @StringRes val label: Int, val intent: Intent)

/** System screens the stock firmware may hide from its menus; screen lock first. */
fun systemShortcuts(): List<SystemShortcut> = listOf(
    SystemShortcut("screen_lock", R.string.shortcut_screen_lock, Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)),
    SystemShortcut("all_settings", R.string.shortcut_all_settings, Intent(Settings.ACTION_SETTINGS)),
    SystemShortcut("wifi", R.string.shortcut_wifi, Intent(Settings.ACTION_WIFI_SETTINGS)),
    SystemShortcut("bluetooth", R.string.shortcut_bluetooth, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)),
    SystemShortcut("apps", R.string.shortcut_apps, Intent(Settings.ACTION_APPLICATION_SETTINGS)),
    SystemShortcut("default_apps", R.string.shortcut_default_apps, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
    SystemShortcut("developer", R.string.shortcut_developer, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)),
    SystemShortcut("date", R.string.shortcut_date, Intent(Settings.ACTION_DATE_SETTINGS)),
    SystemShortcut("language", R.string.shortcut_language, Intent(Settings.ACTION_LOCALE_SETTINGS)),
)

fun List<SystemShortcut>.resolvable(pm: PackageManager): List<SystemShortcut> =
    filter { it.intent.resolveActivity(pm) != null }

/** The first HOME activity that isn't Kokpit, i.e. the stock launcher. */
fun vendorHomeShortcut(pm: PackageManager, ownPackage: String): SystemShortcut? {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val other = pm.queryIntentActivities(home, 0)
        .map { it.activityInfo }
        .firstOrNull { it.packageName != ownPackage }
        ?: return null
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .setComponent(ComponentName(other.packageName, other.name))
    return SystemShortcut("vendor_home", R.string.shortcut_vendor_home, intent)
}

/** Starts [intent]; on failure shows a short message instead of crashing. */
fun safeStart(context: Context, intent: Intent): Boolean {
    val toStart = if (context is Activity) intent else Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(toStart)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.start_failed, Toast.LENGTH_SHORT).show()
        false
    } catch (e: SecurityException) {
        Toast.makeText(context, R.string.start_failed, Toast.LENGTH_SHORT).show()
        false
    }
}
