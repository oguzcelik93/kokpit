package io.github.yorulmazsinan.kokpit.system

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

fun exportedActivities(pm: PackageManager, packageName: String): List<ComponentName> {
    val info = try {
        pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
    } catch (e: PackageManager.NameNotFoundException) {
        return emptyList()
    }
    return info.activities.orEmpty()
        .filter { it.exported && it.enabled }
        .map { ComponentName(it.packageName, it.name) }
        .sortedBy { it.className }
}

/** System (firmware) packages, where hidden vendor and settings screens live. */
fun systemPackages(pm: PackageManager): List<String> =
    pm.getInstalledApplications(0)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
        .map { it.packageName }
        .sortedWith(compareBy({ it != "com.android.settings" }, { it }))
