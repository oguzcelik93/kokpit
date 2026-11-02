package io.github.yorulmazsinan.kokpit.connect

import android.content.Context
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.widget.Toast
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.carplay.CARPLAY_ENGINES
import io.github.yorulmazsinan.kokpit.system.HomeGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The apps that put the phone on the screen (ZLink and the other CarPlay/Android Auto engines). */
val PROJECTION_PACKAGES: Set<String> = CARPLAY_ENGINES.map { it.packageName }.toSet()

/** Whether the app now at the front is one of them (from the last half hour's app switches). */
fun isProjectionApp(foreground: String?): Boolean = foreground != null && foreground in PROJECTION_PACKAGES

/**
 * Wireless Android Auto / CarPlay and the phone's hotspot don't mix: the phone must join the
 * unit's own Wi-Fi, which it can't while it is a hotspot itself. So while ZLink & co. are at
 * the front, Kokpit stops pulling the unit onto the hotspot, and once per wake it says, over
 * ZLink, to turn the phone's hotspot off. Needs usage access (the same as "Ana ekranı koru").
 */
object PhoneProjection {
    fun active(context: Context): Boolean =
        HomeGuard.hasUsageAccess(context) && isProjectionApp(HomeGuard.foregroundPackage(context, 30 * 60_000L))

    /** Watches for ZLink coming to the front while the unit sits on a hotspot. */
    fun start(context: Context, scope: CoroutineScope, awake: () -> Boolean) {
        val app = context.applicationContext
        scope.launch {
            var was = false
            var warned = false
            while (true) {
                delay(CHECK_MS)
                if (!awake()) { was = false; warned = false; continue }
                val now = withContext(Dispatchers.IO) { active(app) }
                if (now && !was && !warned && onSomeWifi(app)) {
                    warned = true
                    Toast.makeText(app, R.string.projection_hotspot_hint, Toast.LENGTH_LONG).show()
                }
                was = now
            }
        }
    }

    /** The unit is a Wi-Fi client right now (in the car, that means the phone's hotspot). */
    private fun onSomeWifi(context: Context): Boolean = runCatching {
        @Suppress("DEPRECATION")
        context.getSystemService(WifiManager::class.java)?.connectionInfo?.supplicantState == SupplicantState.COMPLETED
    }.getOrDefault(false)

    private const val CHECK_MS = 4_000L
}
