package io.github.yorulmazsinan.kokpit.home.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitFonts
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Gentle drift so a clock left on for hours doesn't burn into the panel. */
fun idleOffset(minute: Int): Pair<Int, Int> {
    val steps = listOf(0 to 0, 14 to -8, -12 to 10, 8 to 14, -16 to -6, 4 to -14)
    return steps[minute.mod(steps.size)]
}

/** Should the idle clock show? Only on the home screen, untouched for a while, car not moving. */
fun shouldIdle(enabled: Boolean, onHome: Boolean, idleMs: Long, limitMinutes: Int, speedKmh: Int?): Boolean =
    enabled && onHome && idleMs >= limitMinutes * 60_000L && (speedKmh == null || speedKmh < 5)

/**
 * Stand-by face for a parked car: a large clock, the date and the outside temperature on
 * black. Any touch, or driving off, brings the cockpit back.
 */
@Composable
fun IdleScreen(temperatureC: Double?, onExit: () -> Unit, fixedNow: LocalDateTime? = null) {
    val now by produceState(fixedNow ?: LocalDateTime.now()) {
        if (fixedNow != null) return@produceState
        while (true) {
            value = LocalDateTime.now()
            delay(1_000L * (60 - value.second).coerceAtLeast(1))
        }
    }
    val tr = remember { Locale.forLanguageTag("tr") }
    val (dx, dy) = idleOffset(now.minute)
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .testTag("idle_screen")
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onExit),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.offset(dx.dp, dy.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 168.sp, lineHeight = 168.sp),
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(
                    now.format(DateTimeFormatter.ofPattern("d MMMM EEEE", tr)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (temperatureC != null) {
                    Spacer(Modifier.width(24.dp))
                    Text(
                        "${temperatureC.roundToInt()}°",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
