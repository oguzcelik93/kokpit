package io.github.yorulmazsinan.kokpit.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.home.cards.KokpitCard
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TR = Locale.forLanguageTag("tr")
private fun Double.km1() = String.format(TR, "%.1f", this)

fun formatMinutes(minutes: Long): String = if (minutes < 60) "$minutes dk" else "${minutes / 60} sa ${minutes % 60} dk"

/** Km log: totals for today / week / month and every drive, newest first. */
@Composable
fun TripsContent(log: TripLog) {
    val trips by log.trips.collectAsStateWithLifecycle()
    val totals = remember(trips) { kmTotals(trips, LocalDate.now()) }
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            Stat(stringResource(R.string.trips_today), totals.todayKm.km1(), "km", Modifier.weight(1f))
            Stat(stringResource(R.string.trips_week), totals.weekKm.km1(), "km", Modifier.weight(1f))
            Stat(stringResource(R.string.trips_month), totals.monthKm.km1(), "km", Modifier.weight(1f))
            Stat(stringResource(R.string.trips_total), String.format(TR, "%,.0f", totals.totalKm), "km", Modifier.weight(1f))
        }
        KokpitCard(Modifier.fillMaxWidth().weight(1f)) {
            if (trips.isEmpty()) {
                Text(stringResource(R.string.trips_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@KokpitCard
            }
            val fmt = remember { DateTimeFormatter.ofPattern("d MMM EEE · HH:mm", TR) }
            val end = remember { DateTimeFormatter.ofPattern("HH:mm", TR) }
            LazyColumn {
                items(trips, key = { it.startMs }) { t ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${t.km.km1()} km · ${formatMinutes(t.minutes)}" + (t.averageKmh?.let { " · ort. ${it.toInt()} km/s" } ?: ""),
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                            )
                            val zone = ZoneId.systemDefault()
                            Text(
                                Instant.ofEpochMilli(t.startMs).atZone(zone).format(fmt) + "–" + Instant.ofEpochMilli(t.endMs).atZone(zone).format(end),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { log.delete(t) }) { Text(stringResource(R.string.intro_delete), color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
            }
        }
    }
}
