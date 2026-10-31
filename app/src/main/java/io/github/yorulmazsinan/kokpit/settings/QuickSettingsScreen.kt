package io.github.yorulmazsinan.kokpit.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.DesktopAccessDisabled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.home.ThemeMode
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.components.SegmentedPicker
import io.github.yorulmazsinan.kokpit.ui.components.HeaderClock
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuickSettingsState(
    val muted: Boolean? = null,
    /** 0–255, null when the unit does not report it. */
    val brightness: Int? = null,
    val canSetBrightness: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val wifiNetwork: String? = null,
    val hotspot: String? = null,
)

class QuickSettingsActions(
    val onVolumeDown: () -> Unit = {},
    val onVolumeUp: () -> Unit = {},
    val onMute: () -> Unit = {},
    val onEqualizer: () -> Unit = {},
    val onBrightness: (Int) -> Unit = {},
    val onAllowBrightness: () -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onConnectHotspot: () -> Unit = {},
    val onWifiSettings: () -> Unit = {},
    val onScreenOff: () -> Unit = {},
    val onAllSettings: () -> Unit = {},
)

/** The everyday controls in Kokpit's look, so the unit's own menus are rarely needed. */
@Composable
fun QuickSettingsScreen(container: AppContainer, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    var system by remember { mutableStateOf(readSystem(context)) }
    var muted by remember { mutableStateOf<Boolean?>(null) }
    val common = container.headUnit.common
    fun vendor(action: () -> Unit) = scope.launch {
        withContext(Dispatchers.IO) {
            action()
            muted = common?.isMuted()
        }
    }
    LaunchedEffect(Unit) { muted = withContext(Dispatchers.IO) { common?.isMuted() } }
    // Back from the permission screen or Wi-Fi settings: read again.
    LifecycleResumeEffect(Unit) {
        system = readSystem(context)
        onPauseOrDispose { }
    }
    QuickSettingsContent(
        state = QuickSettingsState(
            muted = muted,
            brightness = system.brightness,
            canSetBrightness = system.canWrite,
            themeMode = settings.themeMode,
            wifiNetwork = system.wifi,
            hotspot = settings.hotspotSsid,
        ),
        actions = QuickSettingsActions(
            onVolumeDown = { vendor { common?.volumeDown() } },
            onVolumeUp = { vendor { common?.volumeUp() } },
            onMute = { vendor { common?.mute() } },
            onEqualizer = { vendor { common?.openEqualizer() } },
            onBrightness = { value ->
                runCatching { Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value) }
                system = system.copy(brightness = value)
            },
            onAllowBrightness = {
                safeStart(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            },
            onThemeMode = { mode -> scope.launch { container.settings.update { it.copy(themeMode = mode) } } },
            onConnectHotspot = { container.wifi.reconnect(container.scope) },
            onWifiSettings = { safeStart(context, Intent(Settings.ACTION_WIFI_SETTINGS)) },
            onScreenOff = { vendor { common?.screenOff() } },
            onAllSettings = onOpenSettings,
        ),
        onBack = onBack,
        vendorAvailable = common != null,
    )
}

private data class SystemReadings(val brightness: Int?, val canWrite: Boolean, val wifi: String?)

private fun readSystem(context: Context): SystemReadings {
    val brightness = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()
    val canWrite = runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
    @Suppress("DEPRECATION")
    val wifi = runCatching {
        val manager = context.applicationContext.getSystemService(WifiManager::class.java)
        manager.connectionInfo?.ssid?.trim('"')?.takeIf { manager.isWifiEnabled && it.isNotBlank() && it != "<unknown ssid>" }
    }.getOrNull()
    return SystemReadings(brightness, canWrite, wifi)
}

@Composable
fun QuickSettingsContent(state: QuickSettingsState, actions: QuickSettingsActions, onBack: () -> Unit, vendorAvailable: Boolean = true) {
    ScreenScaffold(stringResource(R.string.quick_title), onBack, actions = { HeaderClock() }) {
        Column(
            Modifier.fillMaxSize().padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                Card(stringResource(R.string.quick_volume), Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val tile = Modifier.weight(1f).fillMaxHeight()
                        TileButton(Icons.AutoMirrored.Outlined.VolumeDown, stringResource(R.string.quick_volume_down), vendorAvailable, actions.onVolumeDown, tile, "quick_volume_down", stacked = true)
                        TileButton(
                            Icons.AutoMirrored.Outlined.VolumeOff,
                            stringResource(if (state.muted == true) R.string.quick_unmute else R.string.quick_mute),
                            vendorAvailable,
                            actions.onMute,
                            tile,
                            "quick_mute",
                            lit = state.muted == true,
                            stacked = true,
                        )
                        TileButton(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.quick_volume_up), vendorAvailable, actions.onVolumeUp, tile, "quick_volume_up", stacked = true)
                    }
                    TileButton(Icons.Outlined.Equalizer, stringResource(R.string.quick_equalizer), vendorAvailable, actions.onEqualizer, Modifier.fillMaxWidth(), "quick_equalizer")
                }
                Card(stringResource(R.string.quick_brightness), Modifier.weight(1f).fillMaxHeight()) {
                    if (state.canSetBrightness && state.brightness != null) {
                        var value by remember(state.brightness) { mutableStateOf(state.brightness.toFloat()) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.BrightnessLow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Slider(
                                value = value,
                                onValueChange = { value = it },
                                onValueChangeFinished = { actions.onBrightness(value.toInt()) },
                                valueRange = 10f..255f,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).testTag("quick_brightness"),
                            )
                            Icon(Icons.Outlined.BrightnessHigh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(stringResource(R.string.quick_brightness_permission), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TileButton(Icons.Outlined.BrightnessHigh, stringResource(R.string.quick_allow), true, actions.onAllowBrightness, Modifier.fillMaxWidth(), "quick_allow_brightness")
                    }
                    Spacer(Modifier.weight(1f))
                    val modes = listOf(ThemeMode.DARK to R.string.theme_dark, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.AUTO to R.string.theme_auto)
                    SegmentedPicker(modes, modes.first { it.first == state.themeMode }, { actions.onThemeMode(it.first) }, { stringResource(it.second) })
                    TileButton(Icons.Outlined.DesktopAccessDisabled, stringResource(R.string.quick_screen_off), vendorAvailable, actions.onScreenOff, Modifier.fillMaxWidth(), "quick_screen_off")
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                Card(stringResource(R.string.quick_wifi), Modifier.weight(2f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Wifi, null, tint = if (state.wifiNetwork != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            state.wifiNetwork ?: stringResource(R.string.quick_wifi_none),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.hotspot != null) {
                            TileButton(Icons.Outlined.PhonelinkSetup, stringResource(R.string.quick_connect_phone), true, actions.onConnectHotspot, Modifier, "quick_connect_phone")
                        }
                        TileButton(Icons.Outlined.Settings, stringResource(R.string.quick_wifi_settings), true, actions.onWifiSettings, Modifier, "quick_wifi_settings")
                    }
                }
                TileButton(Icons.Outlined.Settings, stringResource(R.string.quick_all_settings), true, actions.onAllSettings, Modifier.weight(1f).fillMaxHeight(), "quick_all_settings", stacked = true)
            }
        }
    }
}

@Composable
private fun Card(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(Dimens.CardRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun TileButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    tag: String,
    lit: Boolean = false,
    stacked: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.MinTouch).testTag(tag),
        shape = RoundedCornerShape(10.dp),
        // Filled like the assistant's settings button; a switched-on tile (mute) goes brighter.
        color = when {
            !enabled -> MaterialTheme.colorScheme.background
            lit -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        border = if (enabled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        val tint = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            lit -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }
        if (stacked) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, color = tint, maxLines = 1)
            }
        } else {
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, color = tint, maxLines = 1)
            }
        }
    }
}

