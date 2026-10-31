package io.github.yorulmazsinan.kokpit.car

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The car's own notebook: parking spot, home/work and the fuel log. SharedPreferences with
 * commit(), because the parking spot is written in the last moments before the unit sleeps.
 */
class CarRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("car", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _home = MutableStateFlow(readPlace(KEY_HOME))
    val home: StateFlow<Place> = _home.asStateFlow()
    private val _work = MutableStateFlow(readPlace(KEY_WORK))
    val work: StateFlow<Place> = _work.asStateFlow()
    private val _parked = MutableStateFlow(read(KEY_PARKED) { json.decodeFromString(ParkedSpot.serializer(), it) })
    val parked: StateFlow<ParkedSpot?> = _parked.asStateFlow()
    private val _fuel = MutableStateFlow(read(KEY_FUEL) { json.decodeFromString(ListSerializer(FuelEntry.serializer()), it) } ?: emptyList())
    val fuel: StateFlow<List<FuelEntry>> = _fuel.asStateFlow()

    fun setHome(place: Place) = savePlace(KEY_HOME, place).also { _home.value = place }
    fun setWork(place: Place) = savePlace(KEY_WORK, place).also { _work.value = place }

    fun park(lat: Double, lon: Double, timeMs: Long = System.currentTimeMillis()) {
        val spot = ParkedSpot(lat, lon, timeMs)
        prefs.edit().putString(KEY_PARKED, json.encodeToString(ParkedSpot.serializer(), spot)).commit()
        _parked.value = spot
    }

    fun addFuel(entry: FuelEntry) = saveFuel(_fuel.value + entry)
    fun removeFuel(entry: FuelEntry) = saveFuel(_fuel.value - entry)

    private fun saveFuel(list: List<FuelEntry>) {
        val sorted = list.sortedByDescending { it.timeMs }
        prefs.edit().putString(KEY_FUEL, json.encodeToString(ListSerializer(FuelEntry.serializer()), sorted)).apply()
        _fuel.value = sorted
    }

    private fun readPlace(key: String): Place = read(key) { json.decodeFromString(Place.serializer(), it) } ?: Place()

    private fun savePlace(key: String, place: Place) {
        if (place.isSet) prefs.edit().putString(key, json.encodeToString(Place.serializer(), place)).apply()
        else prefs.edit().remove(key).apply()
    }

    private fun <T> read(key: String, decode: (String) -> T): T? =
        prefs.getString(key, null)?.let { runCatching { decode(it) }.getOrNull() }

    private companion object {
        const val KEY_HOME = "place_home"
        const val KEY_WORK = "place_work"
        const val KEY_PARKED = "parked"
        const val KEY_FUEL = "fuel_log"
    }
}
