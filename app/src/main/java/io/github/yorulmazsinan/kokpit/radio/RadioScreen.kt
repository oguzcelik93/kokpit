package io.github.yorulmazsinan.kokpit.radio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.ui.components.HeaderClock
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitFonts
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How much of the band the dial shows at once, in 0.1 MHz steps (8 MHz). */
private const val DIAL_WINDOW_STEPS = 80
private const val HOLD_TO_SAVE_MS = 700L

/**
 * Kokpit's radio: the station's name leads (RDS, or the name saved with the preset), the
 * frequency sits under it, and a dial below can be dragged like a tuning ruler. Presets
 * are tapped to tune and held to save the current station into that slot.
 */
@Composable
fun RadioScreen(
    frequency: Int,
    presets: List<RadioPreset?>,
    stationName: String?,
    onTune: (Int) -> Unit,
    onSavePreset: (slot: Int) -> Unit,
    onOpenVendorRadio: () -> Unit,
    onBack: () -> Unit,
    connected: Boolean = false,
    onSeek: (Int) -> Unit = {},
) {
    // The frequency under the needle while the dial is dragged; NaN otherwise.
    var dragged by remember { mutableFloatStateOf(Float.NaN) }
    val shown = if (dragged.isNaN()) frequency else dragged.roundToInt().coerceIn(FmBand.MIN, FmBand.MAX)
    ScreenScaffold(stringResource(R.string.radio_title), onBack, actions = { HeaderClock() }) {
        Column(
            Modifier.fillMaxSize().padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
        ) {
            if (!connected) Surface(
                onClick = onOpenVendorRadio,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("radio_notice"),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.radio_not_connected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(
                shape = RoundedCornerShape(Dimens.CardRadius),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                Column(Modifier.fillMaxSize().padding(Dimens.CardPadding)) {
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        RoundButton(Icons.Outlined.FastRewind, R.string.radio_seek_down, "radio_seek_down") { onSeek(-1) }
                        StationTitle(shown, stationName.takeIf { shown == frequency }, Modifier.weight(1f))
                        RoundButton(Icons.Outlined.FastForward, R.string.radio_seek_up, "radio_seek_up") { onSeek(+1) }
                    }
                    Row(Modifier.fillMaxWidth().height(84.dp), verticalAlignment = Alignment.CenterVertically) {
                        StepButton(Icons.Outlined.ChevronLeft, R.string.radio_down, "radio_down") { onTune(FmBand.step(frequency, -1)) }
                        TuningDial(
                            frequency = frequency,
                            presets = presets.mapNotNull { it?.frequency },
                            onDrag = { dragged = it },
                            onRelease = { f -> dragged = Float.NaN; if (f != frequency) onTune(f) },
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp),
                        )
                        StepButton(Icons.Outlined.ChevronRight, R.string.radio_up, "radio_up") { onTune(FmBand.step(frequency, +1)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (0 until RADIO_PRESET_SLOTS).forEach { slot ->
                    val preset = presets.getOrNull(slot)
                    PresetTile(
                        preset = preset,
                        selected = preset?.frequency == frequency,
                        onTap = { preset?.let { onTune(it.frequency) } },
                        onSave = { onSavePreset(slot) },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("radio_preset_$slot"),
                    )
                }
            }
        }
    }
}

@Composable
private fun StationTitle(frequency: Int, name: String?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (name != null) {
            Text(
                name,
                style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 60.sp, lineHeight = 64.sp),
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("radio_station_name"),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    FmBand.format(frequency),
                    style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 30.sp, lineHeight = 32.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("radio_frequency"),
                )
                Text(" MHz", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    FmBand.format(frequency),
                    style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 84.sp, lineHeight = 84.sp),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("radio_frequency"),
                )
                Text(" MHz", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
    }
}

/**
 * A ruler that slides under a fixed needle: whole MHz carry a label, half MHz a longer
 * tick, saved stations a dot. Dragging follows the finger and tunes on release.
 */
@Composable
private fun TuningDial(
    frequency: Int,
    presets: List<Int>,
    onDrag: (Float) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier,
) {
    val tick = MaterialTheme.colorScheme.onSurfaceVariant
    val needle = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val current by rememberUpdatedState(frequency)
    // Fractional frequency under the needle during a drag; NaN when the dial follows [frequency].
    var position by remember { mutableFloatStateOf(Float.NaN) }
    val centre = if (position.isNaN()) frequency.toFloat() else position
    Canvas(
        modifier
            .testTag("radio_dial")
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .pointerInput(Unit) {
                val pxPerStep = size.width.toFloat() / DIAL_WINDOW_STEPS
                detectHorizontalDragGestures(
                    onDragStart = { position = current.toFloat() },
                    onDragEnd = {
                        val target = position.roundToInt().coerceIn(FmBand.MIN, FmBand.MAX)
                        position = Float.NaN
                        onRelease(target)
                    },
                    onDragCancel = { position = Float.NaN; onRelease(current) },
                ) { change, dx ->
                    change.consume()
                    // Moving the ruler right brings lower frequencies under the needle.
                    position = (position - dx / pxPerStep).coerceIn(FmBand.MIN.toFloat(), FmBand.MAX.toFloat())
                    onDrag(position)
                }
            }
            .drawWithContent {
                drawContent()
                // Fade both ends so the ruler reads as running on past the edges.
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.18f to Color.Black, 0.82f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        val pxPerStep = size.width / DIAL_WINDOW_STEPS
        val baseline = size.height * 0.62f
        val first = (centre - DIAL_WINDOW_STEPS / 2f).toInt() - 1
        val last = (centre + DIAL_WINDOW_STEPS / 2f).toInt() + 1
        for (f in first..last) {
            if (f < FmBand.MIN || f > FmBand.MAX) continue
            val x = size.width / 2f + (f - centre) * pxPerStep
            val whole = f % 10 == 0
            val length = when {
                whole -> size.height * 0.34f
                f % 5 == 0 -> size.height * 0.24f
                else -> size.height * 0.13f
            }
            drawLine(
                tick.copy(alpha = if (whole) 0.9f else 0.45f),
                Offset(x, baseline - length),
                Offset(x, baseline),
                strokeWidth = if (whole) 2.5f else 1.5f,
                cap = StrokeCap.Round,
            )
            if (whole) {
                val text = measurer.measure((f / 10).toString(), labelStyle)
                drawText(text, topLeft = Offset(x - text.size.width / 2f, baseline + 6.dp.toPx()))
            }
            if (f in presets) drawCircle(needle, radius = 3.5.dp.toPx(), center = Offset(x, baseline - size.height * 0.46f))
        }
        val cx = size.width / 2f
        drawLine(needle, Offset(cx, 2.dp.toPx()), Offset(cx, baseline + 2.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: Int, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(72.dp).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, stringResource(label), modifier = Modifier.size(32.dp)) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: Int, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(64.dp).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, stringResource(label), modifier = Modifier.size(32.dp)) }
    }
}

/** Tap tunes; holding fills the tile from the left and saves the current station there. */
@Composable
private fun PresetTile(
    preset: RadioPreset?,
    selected: Boolean,
    onTap: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier,
) {
    val fill = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val tap by rememberUpdatedState(onTap)
    val save by rememberUpdatedState(onSave)
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val description = preset?.let { listOfNotNull(it.name, FmBand.format(it.frequency)).joinToString(", ") }
        ?: stringResource(R.string.radio_empty_preset)
    val saveLabel = stringResource(R.string.radio_save)
    val saved = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        save()
        scope.launch { fill.snapTo(1f); fill.animateTo(0f, tween(500)) }
        Unit
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .semantics {
                contentDescription = description
                onClick { tap(); true }
                onLongClick(saveLabel) { saved(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val filling = scope.launch { fill.animateTo(1f, tween(HOLD_TO_SAVE_MS.toInt(), easing = LinearEasing)) }
                    var released = false
                    val beforeHold = withTimeoutOrNull(HOLD_TO_SAVE_MS) { released = waitForUpOrCancellation() != null }
                    filling.cancel()
                    if (beforeHold == null) {
                        saved()
                        waitForUpOrCancellation()
                    } else {
                        scope.launch { fill.animateTo(0f, tween(150)) }
                        if (released) tap()
                    }
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).drawBehind {
                drawRect(fillColor, size = Size(size.width * fill.value, size.height))
            },
            contentAlignment = Alignment.Center,
        ) {
            val onTile = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            when {
                preset == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.radio_hold_to_save), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                preset.name != null -> Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(preset.name, style = MaterialTheme.typography.labelLarge, color = onTile, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        FmBand.format(preset.frequency),
                        style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 18.sp),
                        color = if (selected) onTile else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Text(FmBand.format(preset.frequency), style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 22.sp), color = onTile)
            }
        }
    }
}
