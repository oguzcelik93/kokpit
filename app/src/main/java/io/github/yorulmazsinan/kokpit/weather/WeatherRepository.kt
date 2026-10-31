package io.github.yorulmazsinan.kokpit.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Keeps the last good forecast on disk so the home screen has something to show when the
 * unit wakes up before the phone hotspot does. Staleness uses wall-clock time because the
 * cache must survive reboots; a clock that is behind the fetch time counts as stale.
 */
class WeatherRepository(
    private val client: OpenMeteoClient,
    private val store: DataStore<Preferences>,
    private val location: () -> Pair<Double, Double>?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<WeatherState>(WeatherState.NoData(WeatherProblem.NO_LOCATION))
    val state: StateFlow<WeatherState> = _state.asStateFlow()
    private var cached: Weather? = null

    suspend fun refresh() {
        if (cached == null) cached = readCache()
        // Show what we have right away; the network may take its full timeout on wake-up.
        cached?.let { _state.value = classify(it) }
        val position = location()
        if (position != null) {
            try {
                val fresh = client.fetch(position.first, position.second).copy(fetchedAtEpochMs = clock())
                writeCache(fresh)
                cached = fresh
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline, server trouble or an unexpected response: keep the cached forecast.
            }
        }
        _state.value = cached?.let(::classify)
            ?: WeatherState.NoData(if (position == null) WeatherProblem.NO_LOCATION else WeatherProblem.NO_NETWORK)
    }

    /** Refreshes now and every [intervalMs] after. */
    fun start(scope: CoroutineScope, intervalMs: Long = 30 * 60_000L, retryMs: Long = 2 * 60_000L) {
        scope.launch {
            while (isActive) {
                refresh()
                // Until the first forecast arrives (no GPS fix or hotspot yet), try again soon.
                delay(if (_state.value is WeatherState.NoData) retryMs else intervalMs)
            }
        }
    }

    private fun classify(weather: Weather): WeatherState {
        val age = clock() - weather.fetchedAtEpochMs
        return if (age in 0..STALE_AFTER_MS) WeatherState.Fresh(weather) else WeatherState.Stale(weather)
    }

    private suspend fun readCache(): Weather? {
        val raw = store.data.first()[CACHE_KEY] ?: return null
        return runCatching { json.decodeFromString(CachedWeather.serializer(), raw).toWeather() }.getOrNull()
    }

    private suspend fun writeCache(weather: Weather) {
        store.edit { it[CACHE_KEY] = json.encodeToString(CachedWeather.serializer(), CachedWeather.from(weather)) }
    }

    private companion object {
        const val STALE_AFTER_MS = 3 * 3_600_000L
        val CACHE_KEY = stringPreferencesKey("weather_cache")
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class CachedWeather(
    val temperatureC: Double,
    val code: Int,
    val isDay: Boolean,
    val maxC: Double,
    val minC: Double,
    val sunrise: String,
    val sunset: String,
    val fetchedAtEpochMs: Long,
) {
    fun toWeather() = Weather(
        temperatureC, code, isDay, maxC, minC,
        java.time.LocalTime.parse(sunrise), java.time.LocalTime.parse(sunset), fetchedAtEpochMs,
    )

    companion object {
        fun from(w: Weather) = CachedWeather(
            w.temperatureC, w.code, w.isDay, w.maxC, w.minC,
            w.sunriseLocal.toString(), w.sunsetLocal.toString(), w.fetchedAtEpochMs,
        )
    }
}
