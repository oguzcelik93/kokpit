package io.github.yorulmazsinan.kokpit.car

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.home.cards.KokpitCard
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.components.SegmentedPicker
import androidx.compose.foundation.layout.widthIn
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import io.github.yorulmazsinan.kokpit.ui.theme.KokpitFonts
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TR = Locale.forLanguageTag("tr")
private fun Double.fmt(digits: Int) = String.format(TR, "%.${digits}f", this)

enum class LogbookTab(val label: Int) { FUEL(R.string.fuel_title), TRIPS(R.string.trips_title), SERVICE(R.string.logbook_service) }

/** Car logbook: fuel fill-ups and the km log of every drive. */
@Composable
fun LogbookScreen(car: CarRepository, trips: TripLog, reminders: ReminderStore, onBack: () -> Unit, initial: LogbookTab = LogbookTab.FUEL) {
    var tab by remember { mutableStateOf(initial) }
    ScreenScaffold(stringResource(R.string.logbook_title), onBack, actions = {
        SegmentedPicker(LogbookTab.entries, tab, { tab = it }, { stringResource(it.label) }, Modifier.widthIn(max = 420.dp).padding(end = 8.dp))
    }) {
        when (tab) {
            LogbookTab.FUEL -> FuelContent(car)
            LogbookTab.TRIPS -> TripsContent(trips)
            LogbookTab.SERVICE -> ServiceContent(car, reminders)
        }
    }
}

/** Fuel log: enter each fill-up from the receipt; real consumption and cost come out. */
@Composable
fun FuelScreen(car: CarRepository, onBack: () -> Unit) {
    ScreenScaffold(stringResource(R.string.fuel_title), onBack) { FuelContent(car) }
}

@Composable
private fun FuelContent(car: CarRepository) {
    val entries by car.fuel.collectAsStateWithLifecycle()
    val stats = remember(entries) { fuelStats(entries, YearMonth.now()) }
    run {
        Column(Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                Stat(stringResource(R.string.fuel_avg), stats.averageL100?.fmt(1), "L/100 km", Modifier.weight(1f))
                Stat(stringResource(R.string.fuel_last), stats.lastL100?.fmt(1), "L/100 km", Modifier.weight(1f))
                Stat(stringResource(R.string.fuel_month), stats.monthCost.takeIf { it > 0 }?.fmt(0), "₺", Modifier.weight(1f))
                Stat(stringResource(R.string.fuel_per_km), stats.costPerKm?.fmt(2), "₺/km", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                KokpitCard(Modifier.weight(1f).fillMaxHeight()) { FuelForm(car, entries.maxOfOrNull { it.odometerKm }) }
                KokpitCard(Modifier.weight(1.2f).fillMaxHeight()) { FuelList(entries, car::removeFuel) }
            }
        }
    }
}

@Composable
internal fun Stat(label: String, value: String?, unit: String, modifier: Modifier) {
    KokpitCard(modifier) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value ?: "–", style = TextStyle(fontFamily = KokpitFonts.condensed, fontSize = 30.sp), color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
    }
}

@Composable
private fun FuelForm(car: CarRepository, lastOdometer: Int?) {
    val context = LocalContext.current
    var odometer by remember { mutableStateOf("") }
    var liters by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var full by remember { mutableStateOf(true) }
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            odometer, { odometer = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().testTag("fuel_odometer"),
            label = { Text(stringResource(R.string.fuel_odometer)) },
            placeholder = { lastOdometer?.let { Text(stringResource(R.string.fuel_odometer_last, it)) } },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(liters, { liters = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.fuel_liters)) }, singleLine = true, keyboardOptions = numbers)
            OutlinedTextField(price, { price = it }, Modifier.weight(1f), label = { Text(stringResource(R.string.fuel_price)) }, singleLine = true, keyboardOptions = numbers)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fuel_full), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Switch(full, { full = it })
        }
        Button(
            shape = KokpitButtonShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch),
            onClick = {
                val km = odometer.toIntOrNull()
                val l = parseDecimal(liters)
                val p = parseDecimal(price)
                when {
                    km == null || l == null || p == null -> Toast.makeText(context, R.string.fuel_invalid, Toast.LENGTH_SHORT).show()
                    lastOdometer != null && km <= lastOdometer -> Toast.makeText(context, R.string.fuel_odometer_low, Toast.LENGTH_SHORT).show()
                    else -> {
                        car.addFuel(FuelEntry(io.github.yorulmazsinan.kokpit.time.TrueTime.nowMs(), km, l, p, full))
                        odometer = ""; liters = ""; price = ""; full = true
                    }
                }
            },
        ) { Text(stringResource(R.string.save)) }
    }
}

@Composable
private fun FuelList(entries: List<FuelEntry>, onDelete: (FuelEntry) -> Unit) {
    if (entries.isEmpty()) {
        Text(stringResource(R.string.fuel_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val date = remember { DateTimeFormatter.ofPattern("d MMM yyyy", TR) }
    LazyColumn {
        items(entries, key = { it.timeMs }) { e ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${e.liters.fmt(2)} L · ${e.totalPrice.fmt(2)} ₺" + if (e.full) "" else " · " + stringResource(R.string.fuel_partial),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${Instant.ofEpochMilli(e.timeMs).atZone(ZoneId.systemDefault()).format(date)} · ${String.format(TR, "%,d", e.odometerKm)} km · ${(e.totalPrice / e.liters).fmt(2)} ₺/L",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onDelete(e) }) { Text(stringResource(R.string.intro_delete), color = MaterialTheme.colorScheme.error) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}

private val DMY = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)

/** `14.11.2026` → 2026-11-14; empty → ""; anything else → null. */
internal fun dmyToIso(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return ""
    return runCatching { java.time.LocalDate.parse(t, DMY).toString() }.getOrNull()
}

/** Maintenance dates (muayene, sigorta, kasko, oil) and what is due soon. */
@Composable
private fun ServiceContent(car: CarRepository, store: ReminderStore) {
    val context = LocalContext.current
    val saved by store.reminders.collectAsStateWithLifecycle()
    val entries by car.fuel.collectAsStateWithLifecycle()
    val odometer = entries.maxOfOrNull { it.odometerKm }
    val dates = remember(saved) {
        ReminderKind.entries.associateWith { k -> mutableStateOf(saved.date(k)?.format(DMY).orEmpty()) }
    }
    var oil by remember(saved) { mutableStateOf(saved.oilKm?.toString().orEmpty()) }
    var tires by remember(saved) { mutableStateOf(saved.tires) }
    val due = remember(saved, odometer) { dueReminders(saved, io.github.yorulmazsinan.kokpit.time.TrueTime.date(), odometer) }
    Row(Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        KokpitCard(Modifier.weight(1.3f).fillMaxHeight()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReminderKind.entries.forEach { kind ->
                    item {
                        var v by dates.getValue(kind)
                        OutlinedTextField(v, { v = it.take(10) }, label = { Text(kind.label) }, placeholder = { Text("GG.AA.YYYY") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    }
                }
                item {
                    OutlinedTextField(oil, { oil = it.filter(Char::isDigit).take(7) }, label = { Text(stringResource(R.string.reminders_oil_km)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reminders_tires), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        Switch(tires, { tires = it })
                    }
                }
                item {
                    Button(onClick = {
                        val iso = ReminderKind.entries.associate { it.key to dmyToIso(dates.getValue(it).value) }
                        if (iso.values.any { it == null }) {
                            Toast.makeText(context, R.string.reminders_invalid, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        store.set(Reminders(iso.filterValues { !it.isNullOrEmpty() }.mapValues { it.value!! }, oil.toIntOrNull()?.takeIf { it > 0 }, tires))
                        Toast.makeText(context, R.string.reminders_saved, Toast.LENGTH_SHORT).show()
                    }, shape = KokpitButtonShape, modifier = Modifier.heightIn(min = Dimens.MinTouch)) { Text(stringResource(R.string.reminders_save)) }
                }
            }
        }
        KokpitCard(Modifier.weight(1f).fillMaxHeight()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (due.isEmpty()) Text(stringResource(R.string.reminders_none), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                due.forEach { d ->
                    Text(d.text, style = MaterialTheme.typography.titleMedium, color = if (d.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
                Text(stringResource(R.string.reminders_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
