package io.github.yorulmazsinan.kokpit.time

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import io.github.yorulmazsinan.kokpit.system.Performance
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Feeds [TrueTime] from GPS satellites (no internet needed) and from an HTTPS server's clock. */
class TimeSync(private val context: Context, private val http: OkHttpClient, private val scope: CoroutineScope) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastGpsSyncElapsed = 0L
    private var nmea: OnNmeaMessageListener? = null

    private val clockReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: android.content.Intent) = TrueTime.onSystemClockSet(context)
    }

    fun start() {
        TrueTime.init(context)
        // CarPlay, NTP or the driver setting the system clock fixes Kokpit's at once.
        androidx.core.content.ContextCompat.registerReceiver(
            context, clockReceiver,
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_TIME_CHANGED)
                addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            },
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                TrueTime.remember(context)
            }
        }
        // With the ADB-granted permission, let Android keep its own clock right too.
        if (Performance.canWriteSecureSettings(context)) {
            runCatching {
                Settings.Global.putInt(context.contentResolver, Settings.Global.AUTO_TIME, 1)
                Settings.Global.putInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 1)
            }
        }
        listenGps()
        syncFromInternet()
    }

    /** Call when a network comes up. */
    fun syncFromInternet() {
        scope.launch {
            val utc = withContext(Dispatchers.IO) { runCatching { serverTime() }.getOrNull() } ?: return@launch
            TrueTime.update(context, utc, TimeSource.INTERNET)
        }
    }

    /** Date header of a tiny HTTPS response, corrected by half the round trip. */
    private fun serverTime(): Long? {
        val start = SystemClock.elapsedRealtime()
        val request = Request.Builder().url("https://www.google.com/generate_204").head().build()
        http.newCall(request).execute().use { response ->
            val header = response.header("Date") ?: return null
            val rtt = SystemClock.elapsedRealtime() - start
            val date = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            return date + rtt / 2 + 500 // the header is truncated to the second
        }
    }

    @SuppressLint("MissingPermission")
    private fun listenGps() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val listener = OnNmeaMessageListener { message, _ ->
            val now = SystemClock.elapsedRealtime()
            // Once every ten minutes is plenty; internet time, when present, is just as good.
            if (now - lastGpsSyncElapsed < 10 * 60_000L && lastGpsSyncElapsed != 0L) return@OnNmeaMessageListener
            val utc = parseRmcUtc(message) ?: return@OnNmeaMessageListener
            lastGpsSyncElapsed = now
            TrueTime.update(context, utc, TimeSource.GPS)
        }
        if (runCatching { lm.addNmeaListener(listener, handler) }.getOrDefault(false)) nmea = listener
        // Some units never deliver NMEA to apps; a GPS fix's own timestamp is the second way.
        runCatching {
            // A full object, not a lambda: Android 9's LocationListener has no default methods.
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60_000L, 0f, object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    val now = SystemClock.elapsedRealtime()
                    if (location.time >= EARLIEST_PLAUSIBLE_MS && (lastGpsSyncElapsed == 0L || now - lastGpsSyncElapsed >= 10 * 60_000L)) {
                        lastGpsSyncElapsed = now
                        TrueTime.update(context, location.time, TimeSource.GPS)
                    }
                }

                @Deprecated("Android 9 still calls it")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }, Looper.getMainLooper())
        }
    }

    /** After the location permission was granted. */
    fun refreshPermission() {
        if (nmea == null) listenGps()
    }
}
