package io.github.yorulmazsinan.kokpit.system

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

class DiagnosticsCollector(private val context: Context) {
    fun collect(): DiagnosticsReport {
        val pm = context.packageManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val defaultHome = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?.takeIf { it != "android" }
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName }.distinct().sorted()
        val homes = pm.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.distinct().sorted()

        return DiagnosticsReport(
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE,
            model = Build.MODEL,
            hardware = Build.HARDWARE,
            display = Build.DISPLAY,
            widthPx = maxOf(metrics.widthPixels, metrics.heightPixels),
            heightPx = minOf(metrics.widthPixels, metrics.heightPixels),
            densityDpi = metrics.densityDpi,
            wifi5GhzSupported = wifi?.is5GHzBandSupported,
            bluetoothAdapterPresent = BluetoothAdapter.getDefaultAdapter() != null,
            defaultHomePackage = defaultHome,
            launchablePackages = launchable,
            homePackages = homes,
            systemPackages = systemPackages(pm),
        )
    }
}
