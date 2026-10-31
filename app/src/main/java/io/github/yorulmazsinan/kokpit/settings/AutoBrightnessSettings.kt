package io.github.yorulmazsinan.kokpit.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.system.BRIGHTNESS_MAX
import io.github.yorulmazsinan.kokpit.system.BRIGHTNESS_MIN
import io.github.yorulmazsinan.kokpit.system.Brightness
import io.github.yorulmazsinan.kokpit.system.brightnessPercent
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.components.HelpText
import io.github.yorulmazsinan.kokpit.ui.components.ListRow
import io.github.yorulmazsinan.kokpit.ui.components.SectionHeader
import io.github.yorulmazsinan.kokpit.ui.components.ToggleRow
import kotlinx.coroutines.launch

/** Settings → Brightness: automatic day/night levels and the automatic Night Panel. */
fun LazyListScope.autoBrightnessSettings(
    container: AppContainer,
    settings: KokpitSettings,
    update: ((KokpitSettings) -> KokpitSettings) -> Unit,
) {
    item { SectionHeader(stringResource(R.string.auto_brightness_section)) }
    item {
        ToggleRow(stringResource(R.string.auto_brightness), settings.autoBrightness, stringResource(R.string.auto_brightness_help)) { on ->
            update { it.copy(autoBrightness = on) }
            container.scope.launch { container.autoBrightness.reapply() }
        }
    }
    if (settings.autoBrightness) {
        item {
            val context = LocalContext.current
            if (!Brightness.canWrite(context)) {
                ListRow(stringResource(R.string.quick_brightness_permission)) { safeStart(context, Brightness.permissionIntent(context)) }
            }
        }
        item(key = "day_level") {
            LevelSlider(stringResource(R.string.brightness_day_level), settings.dayBrightness) { v ->
                update { it.copy(dayBrightness = v) }
                container.scope.launch { container.autoBrightness.reapply() }
            }
        }
        item(key = "night_level") {
            LevelSlider(stringResource(R.string.brightness_night_level), settings.nightBrightness) { v ->
                update { it.copy(nightBrightness = v) }
                container.scope.launch { container.autoBrightness.reapply() }
            }
        }
    }
    item {
        ToggleRow(stringResource(R.string.auto_night_panel), settings.autoNightPanel, stringResource(R.string.auto_night_panel_help)) { on ->
            update { it.copy(autoNightPanel = on) }
        }
    }
    item { HelpText(stringResource(R.string.auto_brightness_note)) }
}

@androidx.compose.runtime.Composable
private fun LevelSlider(label: String, value: Int, onDone: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.width(150.dp))
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onDone(v.toInt()) },
            valueRange = BRIGHTNESS_MIN.toFloat()..BRIGHTNESS_MAX.toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text("%${brightnessPercent(v.toInt())}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.width(60.dp).padding(start = 12.dp))
    }
}
