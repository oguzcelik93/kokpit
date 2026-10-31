package io.github.yorulmazsinan.kokpit.drive

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

class LocationSource(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** GPS fixes once a second; empty when permission is missing or there is no GPS. */
    @SuppressLint("MissingPermission")
    fun fixes(): Flow<Fix> {
        if (!hasPermission() || LocationManager.GPS_PROVIDER !in manager.allProviders) return emptyFlow()
        return callbackFlow {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    trySend(location.toFix())
                }

                @Deprecated("Required on API < 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            awaitClose { manager.removeUpdates(listener) }
        }
    }

    /** Best last-known position from GPS or network, for coarse uses like weather. */
    @SuppressLint("MissingPermission")
    fun lastKnown(): Pair<Double, Double>? {
        if (!hasPermission()) return null
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { it.latitude to it.longitude }
    }
}

private fun Location.toFix() = Fix(
    elapsedMs = elapsedRealtimeNanos / 1_000_000,
    lat = latitude,
    lon = longitude,
    accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
    speedMps = if (hasSpeed()) speed else null,
    bearingDeg = if (hasBearing()) bearing else null,
)
