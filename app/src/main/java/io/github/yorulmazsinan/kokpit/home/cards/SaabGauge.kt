package io.github.yorulmazsinan.kokpit.home.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.drive.TripSnapshot
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitFonts
import io.github.yorulmazsinan.kokpit.ui.theme.SaabColors
import kotlin.math.cos
import kotlin.math.sin

/** Saab 9-3 speedometer scale: 0–240 km/h over a 240° sweep, open at the bottom. */
const val SAAB_MAX_KMH = 240f
private const val SAAB_SWEEP = 240f
private const val SAAB_START = 150f

/** Angle (degrees, Compose convention: 0° = 3 o'clock, clockwise) for [kmh] on the Saab dial. */
fun saabNeedleAngle(kmh: Float): Float = SAAB_START + SAAB_SWEEP * (kmh.coerceIn(0f, SAAB_MAX_KMH) / SAAB_MAX_KMH)

/**
 * Analogue Saab-style speedometer: green-lit ticks and numerals on black, orange needle
 * with a soft glow, black hub. [dim] lowers brightness for the Night Panel.
 */
@Composable
fun SaabSpeedometer(speedKmh: Int?, modifier: Modifier = Modifier, dim: Boolean = false) {
    val needleKmh by animateFloatAsState(
        targetValue = (speedKmh ?: 0).toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 60f),
        label = "saab_needle",
    )
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(fontFamily = KokpitFonts.condensed, color = SaabColors.Green) }
    BoxWithConstraints(modifier.alpha(if (dim) 0.75f else 1f)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val stroke = minOf(w, h) * 0.02f
        val g = gaugeGeometry(w, h, stroke * 3)
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(g.centerX, g.centerY)
            val r = g.radius
            // Bezel: an arc a little wider than the scale, open at the bottom like the real dial.
            drawArc(
                SaabColors.Bezel, SAAB_START - 8f, SAAB_SWEEP + 16f, false,
                Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(stroke),
            )
            // Ticks every 10 km/h, numerals every 20.
            val labelSize = (r * 0.12f).coerceAtLeast(9f)
            for (v in 0..SAAB_MAX_KMH.toInt() step 10) {
                val major = v % 20 == 0
                val a = Math.toRadians(saabNeedleAngle(v.toFloat()).toDouble())
                val outer = r * 0.95f
                val inner = if (major) r * 0.80f else r * 0.87f
                val c = cos(a).toFloat()
                val s = sin(a).toFloat()
                drawLine(
                    SaabColors.Green,
                    Offset(center.x + c * inner, center.y + s * inner),
                    Offset(center.x + c * outer, center.y + s * outer),
                    strokeWidth = if (major) stroke * 1.2f else stroke * 0.6f,
                    cap = StrokeCap.Butt,
                )
                if (major && (v % 40 == 0 || r > 120f * density)) {
                    val text = measurer.measure(v.toString(), labelStyle.copy(fontSize = (labelSize / density).sp))
                    val lr = r * 0.66f
                    drawText(
                        text,
                        topLeft = Offset(
                            center.x + c * lr - text.size.width / 2f,
                            center.y + s * lr - text.size.height / 2f,
                        ),
                    )
                }
            }
            // Needle with glow, starting a little behind the hub like the real one.
            val a = Math.toRadians(saabNeedleAngle(needleKmh).toDouble())
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            val tip = Offset(center.x + c * r * 0.90f, center.y + s * r * 0.90f)
            val tail = Offset(center.x - c * r * 0.14f, center.y - s * r * 0.14f)
            drawLine(SaabColors.NeedleGlow, tail, tip, strokeWidth = stroke * 3.2f, cap = StrokeCap.Round)
            drawLine(SaabColors.Needle, tail, tip, strokeWidth = stroke * 1.3f, cap = StrokeCap.Round)
            drawCircle(SaabColors.Black, r * 0.11f, center)
            drawCircle(SaabColors.Bezel, r * 0.11f, center, style = Stroke(stroke * 0.8f))
        }
        // Digital read-out in the lower opening, like the SID line under the dial.
        Column(
            Modifier.fillMaxSize().padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom,
        ) {
            Text(
                speedKmh?.toString() ?: "–",
                style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 30.sp, lineHeight = 30.sp),
                color = SaabColors.GreenBright,
                modifier = Modifier.testTag("speed_value"),
            )
            Text(
                if (speedKmh != null) stringResource(R.string.speed_unit) else stringResource(R.string.gps_searching),
                style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 13.sp),
                color = SaabColors.GreenDim,
            )
        }
    }
}

/**
 * Night Panel: everything goes dark except a dim clock, so nothing glows in the driver's eyes
 * on a dark road. Any tap brings the full cockpit back.
 */
@Composable
fun NightPanel(onExit: () -> Unit) {
    val now by androidx.compose.runtime.produceState(io.github.yorulmazsinan.kokpit.time.TrueTime.time()) {
        while (true) {
            value = io.github.yorulmazsinan.kokpit.time.TrueTime.time()
            kotlinx.coroutines.delay(15_000)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(SaabColors.Black)
            .testTag("night_panel")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onExit),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
            style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 120.sp, lineHeight = 120.sp),
            color = SaabColors.GreenDim.copy(alpha = 0.7f),
        )
        Text(
            stringResource(R.string.night_panel),
            style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 12.sp, letterSpacing = 3.sp),
            color = SaabColors.GreenDeep,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
    }
}
