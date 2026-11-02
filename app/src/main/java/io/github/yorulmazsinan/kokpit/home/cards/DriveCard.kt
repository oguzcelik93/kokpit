package io.github.yorulmazsinan.kokpit.home.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.drive.TripSnapshot
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitFonts
import io.github.yorulmazsinan.kokpit.ui.theme.LocalCockpitStyle
import androidx.compose.foundation.layout.padding
import kotlin.math.roundToInt

private const val GAUGE_MAX_KMH = 180f
private const val SWEEP = 240f
private const val START = 150f

/** Centred 0–180 km/h arc speedometer. */
@Composable
fun SpeedCard(
    trip: TripSnapshot,
    permissionMissing: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMaps: () -> Unit = {},
    onNightPanel: (() -> Unit)? = null,
) {
    val saab = LocalCockpitStyle.current.saab
    KokpitCard(modifier.clickable(enabled = !permissionMissing, onClick = onOpenMaps)) {
        if (permissionMissing) {
            Row(Modifier.fillMaxSize().clickable(onClick = onRequestPermissions), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GpsNotFixed, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.location_permission), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@KokpitCard
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            trip.averageKmh?.let { avg ->
                Text(
                    stringResource(R.string.average_speed_short, avg),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Sits 3 dp higher without moving the gauge below it.
                    modifier = Modifier.offset(y = (-3).dp).testTag("average_speed"),
                )
            }
            val speed = if (trip.hasFix) trip.speedKmh else null
            if (saab) SaabSpeedometer(speed, Modifier.fillMaxWidth().weight(1f)) else SpeedGauge(speed, Modifier.fillMaxWidth().weight(1f))
        }
        if (saab && onNightPanel != null) {
            Text(
                stringResource(R.string.night_panel),
                style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 12.sp, letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .testTag("night_panel_button")
                    .clickable(onClick = onNightPanel)
                    .padding(6.dp),
            )
        }
    }
}

/** Shortcuts on the heading strip. */
class CarShortcuts(
    val onHome: () -> Unit = {},
    val onWork: () -> Unit = {},
    val onHomeLong: () -> Unit = {},
    val onWorkLong: () -> Unit = {},
    val onParking: () -> Unit = {},
    val onFuel: () -> Unit = {},
    val onTrack: (() -> Unit)? = null,
)

/**
 * Heading (arrow, name, degrees) plus one-tap Home / Work navigation, parking spot and fuel
 * log. On narrow screens the heading shortens so the buttons always fit.
 */
@Composable
fun CompassCard(trip: TripSnapshot, modifier: Modifier = Modifier, onClick: () -> Unit = {}, shortcuts: CarShortcuts? = null) {
    KokpitCard(modifier.testTag("compass_card")) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 430.dp
            val medium = maxWidth >= 330.dp
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (shortcuts == null) Arrangement.Center else Arrangement.Start,
                ) {
                    val bearing = trip.bearingDeg
                    val compass = trip.compass
                    Icon(
                        Icons.Outlined.Navigation,
                        null,
                        tint = if (bearing != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp).rotate(bearing ?: 0f),
                    )
                    Spacer(Modifier.width(10.dp))
                    if (compass != null && bearing != null) {
                        if (wide || shortcuts == null) {
                            Text(stringResource(compass.fullLabel), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Spacer(Modifier.width(10.dp))
                            Text("${bearing.roundToInt() % 360}°", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (medium) {
                            Text(stringResource(compass.label), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    } else if (wide || medium || shortcuts == null) {
                        Text("—", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (shortcuts != null) {
                    StripButton(Icons.Outlined.Home, stringResource(R.string.place_home), "strip_home", shortcuts.onHome, shortcuts.onHomeLong)
                    StripButton(Icons.Outlined.Work, stringResource(R.string.place_work), "strip_work", shortcuts.onWork, shortcuts.onWorkLong)
                    StripButton(Icons.Outlined.LocalParking, stringResource(R.string.parking_title), "strip_parking", shortcuts.onParking, null)
                    StripButton(Icons.Outlined.LocalGasStation, stringResource(R.string.fuel_title), "strip_fuel", shortcuts.onFuel, null)
                    shortcuts.onTrack?.let { StripButton(Icons.Outlined.Flag, "Pist modu", "strip_track", it, null) }
                }
            }
        }
    }
}

@Composable
private fun StripButton(icon: ImageVector, label: String, tag: String, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    Box(
        Modifier
            .size(width = 54.dp, height = 56.dp)
            .testTag(tag)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = label),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
    }
}

data class GaugeGeometry(val centerX: Float, val centerY: Float, val radius: Float)

/**
 * The 240° arc is open at the bottom: its visible height is 1.5 × radius. Centre that
 * visible shape, not the full circle, so the gaps above and below are equal.
 */
fun gaugeGeometry(width: Float, height: Float, stroke: Float): GaugeGeometry {
    val radius = minOf((height - stroke) / 1.5f, (width - stroke) / 2f)
    val visibleHeight = radius * 1.5f + stroke
    val top = (height - visibleHeight) / 2f
    return GaugeGeometry(centerX = width / 2f, centerY = top + stroke / 2f + radius, radius = radius)
}

@Composable
private fun SpeedGauge(speedKmh: Int?, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val stroke = minOf(w, h) * 0.07f
        val g = gaugeGeometry(w, h, stroke)
        Canvas(Modifier.fillMaxSize()) {
            val topLeft = Offset(g.centerX - g.radius, g.centerY - g.radius)
            val arcSize = Size(g.radius * 2, g.radius * 2)
            drawArc(track, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val fraction = ((speedKmh ?: 0) / GAUGE_MAX_KMH).coerceIn(0f, 1f)
            if (fraction > 0f) {
                drawArc(fill, START, SWEEP * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(
            Modifier.fillMaxSize().offset(y = with(density) { (g.centerY - h / 2f).toDp() }),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                speedKmh?.toString() ?: "–",
                style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 56.sp, lineHeight = 56.sp),
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag("speed_value"),
            )
            Text(
                if (speedKmh != null) stringResource(R.string.speed_unit) else stringResource(R.string.gps_searching),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
