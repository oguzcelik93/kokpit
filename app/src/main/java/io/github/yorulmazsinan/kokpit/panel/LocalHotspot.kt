package io.github.yorulmazsinan.kokpit.panel

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Escapes `\ ; , : "` for the Wi-Fi QR format. */
fun wifiQrEscape(text: String): String = buildString {
    for (c in text) {
        if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
        append(c)
    }
}

/** `WIFI:T:WPA;S:<ssid>;P:<password>;;` — phone cameras join the network when they read it. */
fun wifiQr(ssid: String, password: String): String = "WIFI:T:WPA;S:${wifiQrEscape(ssid)};P:${wifiQrEscape(password)};;"

/**
 * The unit's own Wi-Fi when there is no hotspot: Android's local-only hotspot (no internet,
 * random name and password chosen by Android, WPA2). Lives only while the panel needs it.
 */
class LocalHotspot(context: Context) {
    data class Network(val ssid: String, val password: String)

    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private val _network = MutableStateFlow<Network?>(null)
    val network: StateFlow<Network?> = _network.asStateFlow()

    /** [onResult] gets the network, or null with a reason when the unit can't host one. */
    @SuppressLint("MissingPermission")
    fun start(onResult: (Network?, String?) -> Unit) {
        _network.value?.let { return onResult(it, null) }
        val w = wifi ?: return onResult(null, "Wi-Fi yok")
        runCatching {
            w.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    val net = credentials(r)
                    _network.value = net
                    onResult(net, if (net == null) "Ağ bilgisi okunamadı" else null)
                }

                override fun onFailed(reason: Int) {
                    onResult(null, when (reason) {
                        ERROR_TETHERING_DISALLOWED -> "Ünite paylaşımlı ağa izin vermiyor"
                        ERROR_INCOMPATIBLE_MODE -> "Ünitenin Wi-Fi'ı şu an başka bir modda (hotspot açık mı?)"
                        ERROR_NO_CHANNEL -> "Uygun Wi-Fi kanalı yok"
                        else -> "Ağ kurulamadı ($reason)"
                    })
                }

                override fun onStopped() {
                    reservation = null
                    _network.value = null
                }
            }, Handler(Looper.getMainLooper()))
        }.onFailure { onResult(null, it.message ?: "Ağ kurulamadı (konum açık mı?)") }
    }

    fun stop() {
        runCatching { reservation?.close() }
        reservation = null
        _network.value = null
    }

    private fun credentials(r: WifiManager.LocalOnlyHotspotReservation): Network? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            val c = r.softApConfiguration
            @Suppress("DEPRECATION")
            Network(c.ssid ?: return null, c.passphrase ?: return null)
        } else {
            @Suppress("DEPRECATION")
            val c = r.wifiConfiguration ?: return null
            @Suppress("DEPRECATION")
            Network(c.SSID?.trim('"') ?: return null, c.preSharedKey?.trim('"') ?: return null)
        }
    }.getOrNull()
}
