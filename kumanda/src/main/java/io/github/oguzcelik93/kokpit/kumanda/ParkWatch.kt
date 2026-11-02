package io.github.oguzcelik93.kokpit.kumanda

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The car was "parked" only if the phone had been with it at least this long. */
const val MIN_DRIVE_MS = 2 * 60_000L

/** A last-known position older than this is not where the car is. */
const val MAX_FIX_AGE_MS = 10 * 60_000L

/** Only a drive, not a moment's Bluetooth blip, ends in a parking notification. */
fun wasDriving(connectedAtMs: Long, nowMs: Long): Boolean = connectedAtMs > 0 && nowMs - connectedAtMs >= MIN_DRIVE_MS

/** Link a phone's maps app opens. */
fun parkLink(lat: Double, lon: Double): String = "https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, lat, lon)

/**
 * "Arabam nerede?": when the phone's Bluetooth link to the unit drops after a drive (ignition
 * off), the phone notes where *it* is — the unit has no GPS fix, the phone does — and shows
 * "Aracınız burada park edildi · Haritada aç". Off until the user turns it on in Kumanda;
 * the location stays on the phone.
 */
object ParkWatch {
    private const val PREFS = "park"
    private const val CHANNEL = "park"

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("on", false)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("on", on).apply()

    /** The last parking spot: (lat, lon, time) or null. */
    fun last(context: Context): Triple<Double, Double, Long>? {
        val p = prefs(context)
        if (!p.contains("lat")) return null
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return null
        return Triple(lat, lon, p.getLong("time", 0L))
    }

    fun hasLocation(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Android 10+: the drop happens with Kumanda closed, which needs "Her zaman izin ver". */
    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun onLink(context: Context, intent: Intent, done: () -> Unit) {
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val unit = context.getSharedPreferences("unit", Context.MODE_PRIVATE).getString("last_unit", null)
        if (device == null || unit == null || device.address != unit) return done()
        val p = prefs(context)
        val now = System.currentTimeMillis()
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> { p.edit().putLong("connected_at", now).apply(); done() }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val connectedAt = p.getLong("connected_at", 0L)
                p.edit().remove("connected_at").apply()
                if (!enabled(context) || !wasDriving(connectedAt, now) || !hasLocation(context)) return done()
                locate(context) { loc ->
                    if (loc != null) {
                        p.edit().putString("lat", loc.latitude.toString()).putString("lon", loc.longitude.toString()).putLong("time", now).apply()
                        notify(context, loc.latitude, loc.longitude, now)
                    }
                    done()
                }
            }
            else -> done()
        }
    }

    /** A fresh fix if one comes within a few seconds, else a recent last-known one. */
    @SuppressLint("MissingPermission")
    private fun locate(context: Context, result: (Location?) -> Unit) {
        val lm = context.getSystemService(LocationManager::class.java) ?: return result(null)
        val providers = listOfNotNull(
            if (Build.VERSION.SDK_INT >= 31) LocationManager.FUSED_PROVIDER else null,
            LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
        ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        fun lastKnown(): Location? = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < MAX_FIX_AGE_MS }
            .minByOrNull { it.accuracy }
        if (Build.VERSION.SDK_INT < 30 || providers.isEmpty()) return result(lastKnown())
        val main = Handler(Looper.getMainLooper())
        val cancel = CancellationSignal()
        var answered = false
        fun answer(l: Location?) {
            if (answered) return
            answered = true
            cancel.cancel()
            result(l ?: lastKnown())
        }
        runCatching { lm.getCurrentLocation(providers.first(), cancel, context.mainExecutor) { answer(it) } }.onFailure { answer(null) }
        main.postDelayed({ answer(null) }, 7_000)
    }

    private fun notify(context: Context, lat: Double, lon: Double, timeMs: Long) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.park_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW, Uri.parse(parkLink(lat, lon))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = SimpleDateFormat("HH:mm", Locale("tr")).format(Date(timeMs))
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(context.getString(R.string.park_title))
            .setContentText(context.getString(R.string.park_text, time))
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, context.getString(R.string.park_open), open).build())
            .build()
        nm.notify(1, n)
    }
}

/** The phone's Bluetooth link to the remembered unit came up or went down. */
class ParkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        var finished = false
        val done = { if (!finished) { finished = true; pending.finish() } }
        runCatching { ParkWatch.onLink(context.applicationContext, intent, done) }.onFailure { done() }
    }
}
