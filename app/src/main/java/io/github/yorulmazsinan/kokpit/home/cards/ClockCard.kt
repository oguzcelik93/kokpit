package io.github.yorulmazsinan.kokpit.home.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.calendar.CalendarState
import io.github.yorulmazsinan.kokpit.calls.CallEntry
import io.github.yorulmazsinan.kokpit.calls.CallType
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitFonts
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import io.github.yorulmazsinan.kokpit.weather.Weather
import io.github.yorulmazsinan.kokpit.weather.WeatherCondition
import io.github.yorulmazsinan.kokpit.weather.WeatherProblem
import io.github.yorulmazsinan.kokpit.weather.WeatherState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ClockCard(
    now: LocalDateTime,
    weather: WeatherState,
    calendar: CalendarState,
    recentCalls: List<CallEntry>,
    onOpenPhone: () -> Unit,
    onAssistant: () -> Unit,
    onRefreshWeather: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    onRecovery: () -> Unit = {},
    onBrightness: () -> Unit = {},
) {
    KokpitCard(modifier) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Long-press on the clock is the hidden way into Recovery, even when settings are broken.
                Column(Modifier.weight(1f).testTag("home_clock").combinedClickable(onClick = onOpenCalendar, onLongClick = onRecovery)) {
                    StyledClock(now)
                    Spacer(Modifier.height(6.dp))
                    if (!io.github.yorulmazsinan.kokpit.time.TrueTime.sure) {
                        Text(
                            stringResource(R.string.clock_estimate_note),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.testTag("clock_estimate"),
                        )
                    }
                    Text(
                        now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
                            .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onBrightness, modifier = Modifier.size(Dimens.MinTouch).testTag("home_brightness")) {
                    Icon(
                        Icons.Outlined.LightMode,
                        contentDescription = stringResource(R.string.quick_brightness),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(Dimens.MinTouch).testTag("home_settings")) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onAssistant, modifier = Modifier.size(Dimens.MinTouch)) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = stringResource(R.string.dock_assistant),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            WeatherRow(weather, onRefreshWeather)
            if (recentCalls.isNotEmpty()) RecentCallsRow(recentCalls, onOpenPhone)
            CalendarRow(calendar, now, onRequestPermissions, onOpenCalendar)
        }
    }
}

@Composable
private fun WeatherRow(state: WeatherState, onRefresh: () -> Unit) {
    val weather: Weather? = when (state) {
        is WeatherState.Fresh -> state.weather
        is WeatherState.Stale -> state.weather
        is WeatherState.NoData -> null
    }
    Row(Modifier.clickable(onClick = onRefresh), verticalAlignment = Alignment.CenterVertically) {
        if (weather == null) {
            Icon(Icons.Outlined.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            val problem = (state as? WeatherState.NoData)?.problem
            Text(
                stringResource(if (problem == WeatherProblem.NO_LOCATION) R.string.weather_waiting_location else R.string.weather_needs_internet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Row
        }
        val condition = WeatherCondition.fromWmo(weather.code)
        Icon(condition.icon(weather.isDay), null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(14.dp))
        Text("${weather.temperatureC.roundToInt()}°", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(condition.label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            val detail = stringResource(R.string.weather_high_low, weather.maxC.roundToInt(), weather.minC.roundToInt())
            val updated = if (state is WeatherState.Stale) {
                " · " + stringResource(
                    R.string.weather_updated_at,
                    Instant.ofEpochMilli(weather.fetchedAtEpochMs).atZone(ZoneId.systemDefault()).format(timeFormat),
                )
            } else ""
            Text(detail + updated, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarRow(state: CalendarState, now: LocalDateTime, onRequestPermissions: () -> Unit, onOpen: () -> Unit) {
    when (state) {
        CalendarState.Empty -> Unit
        CalendarState.PermissionMissing -> CalendarLine(Modifier.clickable(onClick = onRequestPermissions)) {
            Text(stringResource(R.string.calendar_permission), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is CalendarState.Next -> CalendarLine(Modifier.clickable(onClick = onOpen)) {
            val begin = Instant.ofEpochMilli(state.event.beginMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val minutes = java.time.Duration.between(now, begin).toMinutes()
            val whenText = if (minutes <= 0) stringResource(R.string.calendar_now)
            else stringResource(R.string.calendar_in, relativeDuration(minutes))
            Text(
                "${begin.format(timeFormat)}  ${state.event.title}  ·  $whenText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CalendarLine(modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.testTag("calendar_row")) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            content()
        }
    }
}

fun WeatherCondition.icon(isDay: Boolean): ImageVector = when (this) {
    WeatherCondition.CLEAR -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    WeatherCondition.PARTLY_CLOUDY -> Icons.Outlined.WbCloudy
    WeatherCondition.CLOUDY -> Icons.Outlined.Cloud
    WeatherCondition.FOG -> Icons.Outlined.Dehaze
    WeatherCondition.DRIZZLE -> Icons.Outlined.Grain
    WeatherCondition.RAIN, WeatherCondition.SHOWERS -> Icons.Outlined.Umbrella
    WeatherCondition.SNOW -> Icons.Outlined.AcUnit
    WeatherCondition.THUNDER -> Icons.Outlined.Thunderstorm
}

@Composable
private fun StyledClock(now: LocalDateTime) {
    Text(
        now.format(timeFormat),
        style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 92.sp, lineHeight = 92.sp),
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun RecentCallsRow(calls: List<CallEntry>, onOpenPhone: () -> Unit) {
    Column(Modifier.testTag("recent_calls").clickable(onClick = onOpenPhone)) {
        Text(stringResource(R.string.recent_calls), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        calls.forEach { call ->
            Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (call.type) {
                    CallType.MISSED -> Icons.Outlined.CallMissed to MaterialTheme.colorScheme.error
                    CallType.OUTGOING -> Icons.Outlined.CallMade to MaterialTheme.colorScheme.onSurfaceVariant
                    CallType.INCOMING -> Icons.Outlined.CallReceived to MaterialTheme.colorScheme.primary
                }
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(call.displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
