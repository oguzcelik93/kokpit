package io.github.yorulmazsinan.kokpit.home.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.system.BRIGHTNESS_MAX
import io.github.yorulmazsinan.kokpit.system.BRIGHTNESS_MIN
import io.github.yorulmazsinan.kokpit.system.Brightness
import io.github.yorulmazsinan.kokpit.system.brightnessPercent
import io.github.yorulmazsinan.kokpit.system.nextBrightness
import io.github.yorulmazsinan.kokpit.system.safeStart
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import io.github.yorulmazsinan.kokpit.ui.theme.LocalCockpitStyle
import kotlinx.coroutines.delay

/**
 * One-tap brightness: night / − / slider / + / day, over whatever screen is open. Changes
 * apply while dragging; the panel closes by itself after a few idle seconds or on a tap
 * outside it.
 */
@Composable
fun BrightnessPanel(onDismiss: () -> Unit, initial: Int? = null, canWriteOverride: Boolean? = null) {
    val context = LocalContext.current
    val canWrite = canWriteOverride ?: remember { Brightness.canWrite(context) }
    var value by remember { mutableIntStateOf(initial ?: Brightness.get(context) ?: 128) }
    // Every touch restarts the idle timer.
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(touched) {
        delay(6_000)
        onDismiss()
    }
    fun apply(v: Int) {
        value = v.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
        Brightness.set(context, value)
        touched++
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .testTag("brightness_panel"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(LocalCockpitStyle.current.cardRadius),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier
                .padding(top = 24.dp)
                .widthIn(max = 760.dp)
                .fillMaxWidth(0.85f)
                // Swallow taps so they don't reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { touched++ },
        ) {
            if (!canWrite) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.quick_brightness_permission),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { safeStart(context, Brightness.permissionIntent(context)); onDismiss() }) {
                        Text(stringResource(R.string.quick_allow))
                    }
                }
                return@Surface
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PanelButton(Icons.Outlined.DarkMode, stringResource(R.string.brightness_night), "brightness_night") { apply(BRIGHTNESS_MIN) }
                PanelButton(Icons.Outlined.BrightnessLow, stringResource(R.string.brightness_down), "brightness_down") { apply(nextBrightness(value, up = false)) }
                Slider(
                    value = value.toFloat(),
                    onValueChange = { apply(it.toInt()) },
                    valueRange = BRIGHTNESS_MIN.toFloat()..BRIGHTNESS_MAX.toFloat(),
                    modifier = Modifier.weight(1f).testTag("brightness_slider"),
                )
                Text(
                    "%${brightnessPercent(value)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.widthIn(min = 52.dp),
                )
                PanelButton(Icons.Outlined.BrightnessHigh, stringResource(R.string.brightness_up), "brightness_up") { apply(nextBrightness(value, up = true)) }
                PanelButton(Icons.Outlined.LightMode, stringResource(R.string.brightness_day), "brightness_day") { apply(BRIGHTNESS_MAX) }
            }
        }
    }
}

@Composable
private fun PanelButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(Dimens.MinTouch).testTag(tag)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp))
    }
}
