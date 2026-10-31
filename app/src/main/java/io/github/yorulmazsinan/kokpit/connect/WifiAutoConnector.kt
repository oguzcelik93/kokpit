package io.github.yorulmazsinan.kokpit.connect

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.settings.KokpitSettings
import io.github.yorulmazsinan.kokpit.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * On wake, gets the unit onto the user's phone hotspot. Android 8.1–9 lets an app switch
 * Wi-Fi on and join a network; Android 10+ only allows a network suggestion, so there we
 * add one and, if Wi-Fi is off, post a notification that opens the Wi-Fi panel.
 */
class WifiAutoConnector(
    private val context: Context,
    private val settings: SettingsRepository,
    private val retry: RetryPolicy = RetryPolicy(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var job: Job? = null

    /** Starts (or restarts) the connect routine; safe to call repeatedly. */
    fun reconnect(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) { onWake() }
    }

    private suspend fun onWake() {
        val wifi = wifi ?: return
        val s = settings.current()
        val ssid = s.hotspotSsid
        if (!s.autoWifi || ssid == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            suggest(wifi, s, ssid)
            return
        }
        val started = clock()
        while (true) {
            if (runCatching { connectLegacy(wifi, ssid, s.hotspotPassword) }.getOrDefault(false)) return
            val wait = retry.nextAttemptDelay(started, clock()) ?: return
            delay(wait)
        }
    }

    /** One attempt; true once connected to [ssid]. */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun connectLegacy(wifi: WifiManager, ssid: String, password: String?): Boolean {
        if (!wifi.isWifiEnabled) wifi.isWifiEnabled = true
        val existing = wifi.configuredNetworks.orEmpty().firstOrNull { it.SSID == quoted(ssid) }
        val id = existing?.networkId ?: wifi.addNetwork(
            WifiConfiguration().apply {
                SSID = quoted(ssid)
                if (password.isNullOrEmpty()) {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                } else {
                    preSharedKey = quoted(password)
                }
            },
        )
        if (id == -1) return false
        val info = wifi.connectionInfo
        // Compare by network id: without location permission 8.1 reports "<unknown ssid>".
        if (info?.networkId == id && info.supplicantState == SupplicantState.COMPLETED) return true
        wifi.enableNetwork(id, true)
        return false
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun suggest(wifi: WifiManager, s: KokpitSettings, ssid: String) {
        val builder = WifiNetworkSuggestion.Builder().setSsid(ssid)
        s.hotspotPassword?.takeIf { it.isNotEmpty() }?.let(builder::setWpa2Passphrase)
        runCatching { wifi.addNetworkSuggestions(listOf(builder.build())) }
        if (!wifi.isWifiEnabled) {
            val panel = PendingIntent.getActivity(
                context, 0, Intent(Settings.Panel.ACTION_WIFI), PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, KokpitService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.wifi_off_title))
                .setContentText(context.getString(R.string.wifi_off_text))
                .setContentIntent(panel)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(WIFI_NOTIFICATION_ID, notification)
        }
    }

    private fun quoted(value: String) = "\"$value\""

    private companion object {
        const val WIFI_NOTIFICATION_ID = 2
    }
}
