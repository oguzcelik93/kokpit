package io.github.yorulmazsinan.kokpit.car

import android.widget.Toast
import io.github.yorulmazsinan.kokpit.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One driver's own things, kept under their phone's Bluetooth address. */
@Serializable
data class DriverProfile(
    val home: Place = Place(),
    val work: Place = Place(),
    val wallpaper: String? = null,
    val dayBrightness: Int = 230,
    val nightBrightness: Int = 45,
    val resume: String = "none",
)

/**
 * Whose profile should be active: the active driver's phone stays while it is still
 * connected; otherwise the first connected phone in a stable order; with no phone, nobody
 * new (the last driver's things stay as they are).
 */
fun nextDriver(active: String?, connected: Set<String>): String? = when {
    connected.isEmpty() -> active
    active != null && active in connected -> active
    else -> connected.min()
}

/**
 * Driver profiles: Ev/İş, the photo, where the music was and the brightness levels follow
 * the phone the unit is connected to over Bluetooth. Nothing new to learn: whatever the
 * driver changes while their phone is connected simply becomes theirs. A phone seen for the
 * first time starts from the current settings, so turning this on changes nothing by itself.
 */
class DriverProfiles(private val container: AppContainer) {
    private val prefs = container.appContext.getSharedPreferences("profiles", android.content.Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                delay(CHECK_MS)
                if (!container.awake || !container.settings.settings.value.driverProfiles) continue
                // On the main thread, where the Bluetooth profile proxies live.
                val connected = container.btConnect.connectedPhones()
                val active = prefs.getString(KEY_ACTIVE, null)
                val next = nextDriver(active, connected) ?: continue
                if (next != active) switchTo(active, next)
            }
        }
    }

    private suspend fun switchTo(from: String?, to: String) {
        val now = snapshot()
        val saved = load(to)
        val edit = prefs.edit()
        if (from != null) edit.putString(PREFIX + from, json.encodeToString(DriverProfile.serializer(), now))
        if (saved == null) edit.putString(PREFIX + to, json.encodeToString(DriverProfile.serializer(), now))
        edit.putString(KEY_ACTIVE, to).commit()
        // The very first phone just takes over what is there; only a real change of driver says so.
        if (saved == null || from == null) return
        apply(saved)
        val name = container.btConnect.nameFor(to)
        Toast.makeText(container.appContext, "Sürücü: $name", Toast.LENGTH_SHORT).show()
    }

    private fun snapshot(): DriverProfile {
        val s = container.settings.settings.value
        return DriverProfile(
            home = container.car.home.value,
            work = container.car.work.value,
            wallpaper = container.wallpaper.selected.value,
            dayBrightness = s.dayBrightness,
            nightBrightness = s.nightBrightness,
            resume = container.resume.last.encode(),
        )
    }

    private suspend fun apply(p: DriverProfile) {
        container.car.setHome(p.home)
        container.car.setWork(p.work)
        p.wallpaper?.takeIf { container.wallpaper.photoFile(it) != null }?.let(container.wallpaper::select)
        container.resume.restore(LastSource.decode(p.resume))
        container.settings.update { it.copy(dayBrightness = p.dayBrightness, nightBrightness = p.nightBrightness) }
        container.autoBrightness.reapply()
    }

    private fun load(address: String): DriverProfile? =
        prefs.getString(PREFIX + address, null)?.let { runCatching { json.decodeFromString(DriverProfile.serializer(), it) }.getOrNull() }

    private companion object {
        const val CHECK_MS = 15_000L
        const val KEY_ACTIVE = "active"
        const val PREFIX = "p_"
    }
}
