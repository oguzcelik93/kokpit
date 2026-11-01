package io.github.yorulmazsinan.kokpit.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.BuildConfig
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.dock.DockSlot
import io.github.yorulmazsinan.kokpit.home.ThemeMode
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.SettingsPicker
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import io.github.yorulmazsinan.kokpit.ui.components.ToggleRow
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import io.github.yorulmazsinan.kokpit.ui.theme.ThemeAccent
import kotlinx.coroutines.launch

private const val DAVX5 = "at.bitfire.davdroid"
private const val REPO_URL = "https://github.com/yorulmazsinan/kokpit-car-launcher"

/** Choice rows stay compact instead of stretching across the whole screen. */

@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onPickApp: (DockSlot) -> Unit,
    onOpenIntro: () -> Unit,
    onOpenSystemTools: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPreview: (String) -> Unit = {},
    onOpenCarPlay: () -> Unit = {},
    onOpenPerformance: () -> Unit = {},
    onOpenRecovery: () -> Unit = {},
    onOpenPlaces: () -> Unit = {},
    onOpenFuel: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
    onOpenBtPhones: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val dock by container.dock.targets.collectAsStateWithLifecycle()
    fun update(transform: (KokpitSettings) -> KokpitSettings) {
        scope.launch { container.settings.update(transform) }
    }

    ScreenScaffold(stringResource(R.string.settings_title), onBack) {
        LazyColumn {
            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                val modes = listOf(ThemeMode.DARK to R.string.theme_dark, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.AUTO to R.string.theme_auto)
                SettingsPicker(modes, modes.first { it.first == settings.themeMode }, { (mode, _) -> update { it.copy(themeMode = mode) } }) { stringResource(it.second) }
            }
            item { HelpText(stringResource(R.string.theme_auto_help)) }
            item {
                val accents = ThemeAccent.entries
                SettingsPicker(accents, settings.accent, { accent -> update { it.copy(accent = accent) } }) { stringResource(it.label) }
            }

            item { SectionHeader(stringResource(R.string.clock_section)) }
            item {
                val source = when (io.github.yorulmazsinan.kokpit.time.TrueTime.source) {
                    io.github.yorulmazsinan.kokpit.time.TimeSource.GPS -> stringResource(R.string.clock_source_gps)
                    io.github.yorulmazsinan.kokpit.time.TimeSource.INTERNET -> stringResource(R.string.clock_source_internet)
                    io.github.yorulmazsinan.kokpit.time.TimeSource.SYSTEM -> stringResource(R.string.clock_source_system)
                }
                val shown = io.github.yorulmazsinan.kokpit.time.TrueTime.dateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", java.util.Locale.getDefault()))
                ListRow(shown, stringResource(R.string.clock_status, source, java.util.TimeZone.getDefault().id)) {
                    container.timeSync.syncFromInternet()
                    safeStart(context, Intent(android.provider.Settings.ACTION_DATE_SETTINGS))
                }
            }
            item { HelpText(stringResource(R.string.clock_help)) }

            autoBrightnessSettings(container, settings, ::update)

            item { SectionHeader(stringResource(R.string.settings_car)) }
            item { ListRow(stringResource(R.string.places_title), stringResource(R.string.places_settings_help), onClick = onOpenPlaces) }
            item { ListRow(stringResource(R.string.bt_phones_title), stringResource(R.string.bt_phones_settings_help), onClick = onOpenBtPhones) }
            item { ListRow(stringResource(R.string.logbook_title), stringResource(R.string.logbook_settings_help), onClick = onOpenFuel) }
            item {
                ToggleRow(stringResource(R.string.wake_to_kokpit), settings.wakeToKokpit, stringResource(R.string.wake_to_kokpit_help)) { on ->
                    update { it.copy(wakeToKokpit = on) }
                }
            }
            item {
                ToggleRow(stringResource(R.string.keep_home), settings.keepHome, stringResource(R.string.keep_home_help)) { on ->
                    update { it.copy(keepHome = on) }
                }
            }
            if (settings.keepHome && !io.github.yorulmazsinan.kokpit.system.HomeGuard.hasUsageAccess(context)) {
                item {
                    ListRow(stringResource(R.string.keep_home_permission), stringResource(R.string.keep_home_permission_help)) {
                        safeStart(context, io.github.yorulmazsinan.kokpit.system.HomeGuard.usageAccessIntent())
                    }
                }
            }
            item {
                ToggleRow(stringResource(R.string.auto_resume), settings.autoResume, stringResource(R.string.auto_resume_help)) { on ->
                    update { it.copy(autoResume = on) }
                }
            }
            item {
                ToggleRow(stringResource(R.string.idle_screen), settings.idleScreen, stringResource(R.string.idle_screen_help)) { on ->
                    update { it.copy(idleScreen = on) }
                }
            }
            if (settings.idleScreen) {
                item {
                    val options = listOf(1, 2, 5, 10)
                    SettingsPicker(options, options.minBy { kotlin.math.abs(it - settings.idleMinutes) }, { m -> update { it.copy(idleMinutes = m) } }) {
                        stringResource(R.string.idle_after_minutes, it)
                    }
                }
            }

            assistantSettings(container, settings.assistant) { change -> update { it.copy(assistant = change(it.assistant)) } }

            item { SectionHeader(stringResource(R.string.settings_dock)) }
            DockSlot.entries.filter { it != DockSlot.APPS }.forEach { slot ->
                item(key = "dock_${slot.name}") {
                    val target = dock[slot]
                    val label = target?.let(container.installedApps::label) ?: stringResource(R.string.settings_not_set)
                    ListRow(stringResource(slot.label), label) { onPickApp(slot) }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_connection)) }
            item {
                ToggleRow(stringResource(R.string.settings_auto_wifi), settings.autoWifi, stringResource(R.string.settings_auto_wifi_help)) { on ->
                    update { it.copy(autoWifi = on) }
                }
            }
            item {
                var ssid by remember(settings.hotspotSsid) { mutableStateOf(settings.hotspotSsid.orEmpty()) }
                var password by remember(settings.hotspotPassword) { mutableStateOf(settings.hotspotPassword.orEmpty()) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(ssid, { ssid = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.settings_hotspot_name)) }, singleLine = true)
                    OutlinedTextField(
                        password, { password = it }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.settings_hotspot_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Button(
                        shape = KokpitButtonShape,
                        onClick = {
                            scope.launch {
                                container.settings.update {
                                    it.copy(hotspotSsid = ssid.trim().ifEmpty { null }, hotspotPassword = password.ifEmpty { null }, hotspotPasswordUnreadable = false)
                                }
                                container.wifi.reconnect(container.scope)
                            }
                        },
                        modifier = Modifier.heightIn(min = Dimens.MinTouch),
                    ) { Text(stringResource(R.string.save)) }
                }
            }
            if (settings.hotspotPasswordUnreadable) {
                item { HelpText(stringResource(R.string.settings_password_reenter)) }
            }
            item { HelpText(stringResource(R.string.settings_iphone_hotspot_help)) }
            item {
                ListRow(stringResource(R.string.settings_bt_auto_connect), stringResource(R.string.settings_bt_auto_connect_help)) {
                    dock[DockSlot.BLUETOOTH]?.let(container.installedApps::launchIntent)?.let { safeStart(context, it) }
                        ?: onPickApp(DockSlot.BLUETOOTH)
                }
            }

            item { SectionHeader(stringResource(R.string.dock_carplay)) }
            item { ListRow(stringResource(R.string.carplay_title), stringResource(R.string.carplay_settings_help), onClick = onOpenCarPlay) }
            item {
                ToggleRow(stringResource(R.string.settings_carplay_usb), settings.autoCarPlayOnUsb, stringResource(R.string.settings_carplay_usb_help)) { on ->
                    update { it.copy(autoCarPlayOnUsb = on) }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_intro)) }
            item { ListRow(stringResource(R.string.settings_intro_manage), onClick = onOpenIntro) }

            item { SectionHeader(stringResource(R.string.settings_calendar)) }
            item { HelpText(stringResource(R.string.settings_calendar_help)) }
            item {
                ListRow(stringResource(R.string.settings_open_davx5)) {
                    val intent = container.installedApps.launchIntent(DAVX5)
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$DAVX5"))
                    safeStart(context, intent)
                }
            }

            item { SectionHeader(stringResource(R.string.settings_preview)) }
            item {
                ToggleRow(stringResource(R.string.settings_use_kokpit_screens), settings.useKokpitScreens, stringResource(R.string.settings_use_kokpit_screens_help)) { on ->
                    update { it.copy(useKokpitScreens = on) }
                }
            }
            item { ListRow(stringResource(R.string.phone_title)) { onOpenPreview("phone") } }
            item { ListRow(stringResource(R.string.bt_music_title)) { onOpenPreview("music") } }
            item { ListRow(stringResource(R.string.radio_title)) { onOpenPreview("radio") } }

            item { SectionHeader(stringResource(R.string.settings_system)) }
            item { ListRow(stringResource(R.string.performance_title), stringResource(R.string.performance_settings_help), onClick = onOpenPerformance) }
            item { ListRow(stringResource(R.string.recovery_title), stringResource(R.string.recovery_settings_help), onClick = onOpenRecovery) }
            item { ListRow(stringResource(R.string.system_tools_title), onClick = onOpenSystemTools) }
            item { ListRow(stringResource(R.string.diagnostics_title), onClick = onOpenDiagnostics) }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item { ListRow(stringResource(R.string.update_title), stringResource(R.string.update_settings_help), onClick = onOpenUpdate) }
            item { ListRow("Kokpit ${BuildConfig.VERSION_NAME}", stringResource(R.string.settings_license)) { } }
            item { ListRow(stringResource(R.string.settings_source_code), REPO_URL) { safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) } }
        }
    }
}

