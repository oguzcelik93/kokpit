package io.github.yorulmazsinan.kokpit.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.yorulmazsinan.kokpit.assistant.AiProvider
import io.github.yorulmazsinan.kokpit.assistant.AssistantSettings
import io.github.yorulmazsinan.kokpit.home.ThemeMode
import io.github.yorulmazsinan.kokpit.intro.IntroMode
import io.github.yorulmazsinan.kokpit.intro.IntroSettings
import io.github.yorulmazsinan.kokpit.radio.RadioPreset
import io.github.yorulmazsinan.kokpit.radio.RadioPresetCodec
import io.github.yorulmazsinan.kokpit.ui.theme.ThemeAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class KokpitSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: ThemeAccent = ThemeAccent.SAAB_93,
    val hotspotSsid: String? = null,
    val hotspotPassword: String? = null,
    val hotspotPasswordUnreadable: Boolean = false,
    val autoWifi: Boolean = true,
    val autoCarPlayOnUsb: Boolean = false,
    val intro: IntroSettings = IntroSettings(),
    val radioFrequency: Int = 984,
    val radioPresets: List<RadioPreset?> = emptyList(),
    val useKokpitScreens: Boolean = true,
    val assistant: AssistantSettings = AssistantSettings(),
    val autoBrightness: Boolean = true,
    val dayBrightness: Int = 230,
    val nightBrightness: Int = 45,
    val autoNightPanel: Boolean = false,
    val autoResume: Boolean = true,
    val idleScreen: Boolean = true,
    val idleMinutes: Int = 2,
    val autoUpdate: Boolean = true,
    val wakeToKokpit: Boolean = true,
    val keepHome: Boolean = true,
)

class SettingsRepository(
    private val store: DataStore<Preferences>,
    private val secrets: SecretBox,
    scope: CoroutineScope,
) {
    val settings: StateFlow<KokpitSettings> =
        store.data.map(::read).stateIn(scope, SharingStarted.Eagerly, KokpitSettings())

    suspend fun current(): KokpitSettings = read(store.data.first())

    suspend fun update(transform: (KokpitSettings) -> KokpitSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences) = KokpitSettings(
        themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
        accent = p[ACCENT]?.let { runCatching { ThemeAccent.valueOf(it) }.getOrNull() } ?: ThemeAccent.SAAB_93,
        hotspotSsid = p[SSID],
        hotspotPassword = p[PASSWORD]?.let(secrets::decrypt),
        hotspotPasswordUnreadable = p[PASSWORD] != null && p[PASSWORD]?.let(secrets::decrypt) == null,
        autoWifi = p[AUTO_WIFI] ?: true,
        autoCarPlayOnUsb = p[AUTO_CARPLAY] ?: false,
        radioFrequency = p[RADIO_FREQ] ?: 984,
        useKokpitScreens = p[KOKPIT_SCREENS] ?: true,
        autoBrightness = p[AUTO_BRIGHTNESS] ?: true,
        dayBrightness = p[DAY_BRIGHTNESS] ?: 230,
        nightBrightness = p[NIGHT_BRIGHTNESS] ?: 45,
        autoNightPanel = p[AUTO_NIGHT_PANEL] ?: false,
        autoResume = p[AUTO_RESUME] ?: true,
        idleScreen = p[IDLE_SCREEN] ?: true,
        idleMinutes = p[IDLE_MINUTES] ?: 2,
        autoUpdate = p[AUTO_UPDATE] ?: true,
        wakeToKokpit = p[WAKE_TO_KOKPIT] ?: true,
        keepHome = p[KEEP_HOME] ?: true,
        radioPresets = RadioPresetCodec.decode(p[RADIO_PRESETS]),
        intro = IntroSettings(
            mode = p[INTRO_MODE]?.let { runCatching { IntroMode.valueOf(it) }.getOrNull() } ?: IntroMode.SEASONAL,
            selectedId = p[INTRO_SELECTED],
            everyWake = p[INTRO_EVERY_WAKE] ?: true,
            withSound = p[INTRO_SOUND] ?: false,
            lastPlayedIndex = p[INTRO_LAST_INDEX] ?: -1,
        ),
        assistant = AssistantSettings(
            provider = p[AI_PROVIDER]?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() } ?: AiProvider.CLAUDE,
            keys = AiProvider.entries.mapNotNull { provider -> p[aiKey(provider)]?.let(secrets::decrypt)?.let { provider to it } }.toMap(),
            models = AiProvider.entries.mapNotNull { provider -> p[aiModel(provider)]?.let { provider to it } }.toMap(),
            keepListening = p[AI_KEEP_LISTENING] ?: true,
        ),
    )

    private fun write(p: MutablePreferences, s: KokpitSettings) {
        p[THEME] = s.themeMode.name
        p[ACCENT] = s.accent.name
        p.setOrRemove(SSID, s.hotspotSsid)
        when {
            s.hotspotPassword != null -> runCatching { secrets.encrypt(s.hotspotPassword) }
                .onSuccess { p[PASSWORD] = it }
                .onFailure { p.remove(PASSWORD) }
            !s.hotspotPasswordUnreadable -> p.remove(PASSWORD)
        }
        p[AUTO_WIFI] = s.autoWifi
        p[RADIO_FREQ] = s.radioFrequency
        p[KOKPIT_SCREENS] = s.useKokpitScreens
        p[AUTO_BRIGHTNESS] = s.autoBrightness
        p[DAY_BRIGHTNESS] = s.dayBrightness
        p[NIGHT_BRIGHTNESS] = s.nightBrightness
        p[AUTO_NIGHT_PANEL] = s.autoNightPanel
        p[AUTO_RESUME] = s.autoResume
        p[IDLE_SCREEN] = s.idleScreen
        p[IDLE_MINUTES] = s.idleMinutes
        p[AUTO_UPDATE] = s.autoUpdate
        p[WAKE_TO_KOKPIT] = s.wakeToKokpit
        p[KEEP_HOME] = s.keepHome
        p[RADIO_PRESETS] = RadioPresetCodec.encode(s.radioPresets)
        p[AUTO_CARPLAY] = s.autoCarPlayOnUsb
        p[INTRO_MODE] = s.intro.mode.name
        p.setOrRemove(INTRO_SELECTED, s.intro.selectedId)
        p[INTRO_EVERY_WAKE] = s.intro.everyWake
        p[INTRO_SOUND] = s.intro.withSound
        p[INTRO_LAST_INDEX] = s.intro.lastPlayedIndex
        p[AI_PROVIDER] = s.assistant.provider.name
        p[AI_KEEP_LISTENING] = s.assistant.keepListening
        AiProvider.entries.forEach { provider ->
            val key = s.assistant.keys[provider]?.trim()?.takeIf { it.isNotEmpty() }
            // A key that cannot be sealed is dropped rather than written in the clear.
            if (key == null) p.remove(aiKey(provider)) else runCatching { secrets.encrypt(key) }.onSuccess { p[aiKey(provider)] = it }.onFailure { p.remove(aiKey(provider)) }
            p.setOrRemove(aiModel(provider), s.assistant.models[provider]?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("theme_accent")
        val SSID = stringPreferencesKey("hotspot_ssid")
        val PASSWORD = stringPreferencesKey("hotspot_password_sealed")
        val AUTO_WIFI = booleanPreferencesKey("auto_wifi")
        val RADIO_FREQ = intPreferencesKey("radio_frequency")
        val KOKPIT_SCREENS = booleanPreferencesKey("use_kokpit_screens")
        val AUTO_BRIGHTNESS = booleanPreferencesKey("auto_brightness")
        val DAY_BRIGHTNESS = intPreferencesKey("day_brightness")
        val NIGHT_BRIGHTNESS = intPreferencesKey("night_brightness")
        val AUTO_NIGHT_PANEL = booleanPreferencesKey("auto_night_panel")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val IDLE_SCREEN = booleanPreferencesKey("idle_screen")
        val IDLE_MINUTES = intPreferencesKey("idle_minutes")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val WAKE_TO_KOKPIT = booleanPreferencesKey("wake_to_kokpit")
        val KEEP_HOME = booleanPreferencesKey("keep_home")
        val RADIO_PRESETS = stringPreferencesKey("radio_presets")
        val AUTO_CARPLAY = booleanPreferencesKey("auto_carplay_usb")
        val INTRO_MODE = stringPreferencesKey("intro_mode")
        val INTRO_SELECTED = stringPreferencesKey("intro_selected")
        // v2: the default became "every start"; the old key held an untouched false for everyone.
        val INTRO_EVERY_WAKE = booleanPreferencesKey("intro_every_wake_v2")
        val INTRO_SOUND = booleanPreferencesKey("intro_sound")
        val INTRO_LAST_INDEX = intPreferencesKey("intro_last_index")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_KEEP_LISTENING = booleanPreferencesKey("ai_keep_listening")
        fun aiKey(provider: AiProvider) = stringPreferencesKey("ai_key_sealed_${provider.name.lowercase()}")
        fun aiModel(provider: AiProvider) = stringPreferencesKey("ai_model_${provider.name.lowercase()}")
    }
}
