package io.github.yorulmazsinan.kokpit.track

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yorulmazsinan.kokpit.AppContainer
import io.github.yorulmazsinan.kokpit.home.cards.KokpitCard
import io.github.yorulmazsinan.kokpit.ui.components.KokpitButtonShape
import io.github.yorulmazsinan.kokpit.ui.components.ScreenScaffold
import io.github.yorulmazsinan.kokpit.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/** Values shown on the track screen's tiles, in this order (OBD ones wait for the adapter). */
private val TRACK_TILES = listOf(Signal.OIL_TEMP, Signal.BOOST, Signal.RPM, Signal.COOLANT_TEMP, Signal.OUTSIDE_TEMP, Signal.INTAKE_TEMP)

/** GPS fixes older than this mean the receiver has lost the sky. */
private const val FIX_STALE_MS = 3_000L

/** The start/finish line is kept, so the same track works next time. */
private fun savedLine(context: Context): StartLine? {
    val p = context.getSharedPreferences("track", Context.MODE_PRIVATE)
    val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return null
    val lon = p.getString("lon", null)?.toDoubleOrNull() ?: return null
    return StartLine(lat, lon)
}

private fun saveLine(context: Context, line: StartLine) {
    context.getSharedPreferences("track", Context.MODE_PRIVATE).edit()
        .putString("lat", line.lat.toString()).putString("lon", line.lon.toString()).apply()
}

/**
 * Pist modu: speed and maximum, lap times through a start/finish line set with one tap,
 * 0–100, a G meter when the unit has an accelerometer, and the car's values (the unit's
 * now; oil temperature, boost and the rest once a Bluetooth OBD adapter is added).
 * The screen stays on while it is open.
 */
@Composable
fun TrackScreen(container: AppContainer, onBack: () -> Unit, onCarData: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val session = remember { TrackSession(TrackState(line = savedLine(context))) }
    var state by remember { mutableStateOf(session.state) }
    var lastFix by remember { mutableStateOf<TrackFix?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val signals by container.carData.states.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        container.carData.acquire()
        onDispose {
            view.keepScreenOn = false
            container.carData.release()
        }
    }
    LaunchedEffect(Unit) {
        var prev: io.github.yorulmazsinan.kokpit.drive.Fix? = null
        container.locationSource.fixes(intervalMs = 0L).collect { f ->
            // Without the receiver's own speed, work it out from the last fix.
            val kmh = f.speedMps?.let { it * 3.6 } ?: prev?.let { p ->
                val dt = (f.elapsedMs - p.elapsedMs) / 1000.0
                val (x, y) = localMeters(StartLine(p.lat, p.lon), f.lat, f.lon)
                if (dt > 0) sqrt(x * x + y * y) / dt * 3.6 else null
            } ?: 0.0
            prev = f
            val fix = TrackFix(f.elapsedMs, f.lat, f.lon, kmh)
            session.onFix(fix)
            lastFix = fix
            state = session.state
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(50)
        }
    }
    val g = rememberGForce()
    val gpsOk = lastFix?.let { now - it.timeMs < FIX_STALE_MS } == true
    val carSpeed = signals[Signal.SPEED]?.takeIf { it.changed }?.value

    ScreenScaffold("Pist modu", onBack, actions = {
        Text(
            if (gpsOk) "GPS ✓" else "GPS bekleniyor",
            color = if (gpsOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 16.dp),
        )
    }) {
        Row(Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
            // Speed, maximum, 0–100.
            KokpitCard(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                    val speed = if (gpsOk) lastFix?.speedKmh else carSpeed
                    Text(speed?.let { "%d".format(Math.round(it)) } ?: "—", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("km/h", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Big("Maks", if (state.maxKmh > 0) "%d km/h".format(Math.round(state.maxKmh)) else "—")
                    Big("0–100", formatLap(state.zeroToHundredMs) + (state.bestZeroToHundredMs?.let { "  (en iyi ${formatLap(it)})" } ?: ""))
                    if (!gpsOk) Text("Pist modu GPS ile ölçer. Ünite GPS almıyorsa anteni kontrol edin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Laps.
            KokpitCard(Modifier.weight(1.3f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val running = state.lapStartMs?.let { now - it }
                    Text(if (state.line == null) "Başlangıç çizgisi yok" else "TUR ${state.laps.size + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatLap(running), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    val best = state.bestLap
                    val last = state.lastLap
                    val deltaColor = if (last != null && best != null && last <= best) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Big("Son", formatLap(last))
                        Big("En iyi", formatLap(best))
                    }
                    if (last != null && best != null && state.laps.size > 1) {
                        val previousBest = state.laps.dropLast(1).minOrNull()
                        Text(formatDelta(previousBest?.let { last - it }), style = MaterialTheme.typography.titleLarge, color = deltaColor)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { lastFix?.let { session.setLine(it); saveLine(context, session.state.line!!); state = session.state } },
                            enabled = gpsOk, shape = KokpitButtonShape, modifier = Modifier.heightIn(min = Dimens.MinTouch),
                        ) { Text("Çizgi burası") }
                        OutlinedButton(onClick = { session.reset(SystemClock.elapsedRealtime()); state = session.state }, shape = KokpitButtonShape, modifier = Modifier.heightIn(min = Dimens.MinTouch)) { Text("Sıfırla") }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        state.laps.withIndex().reversed().forEach { (i, lap) ->
                            item {
                                Text("Tur ${i + 1}   ${formatLap(lap)}" + if (lap == best) "  ★" else "",
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            // G meter and car values.
            KokpitCard(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (g != null) GMeter(g.first, g.second, Modifier.fillMaxWidth(0.62f).aspectRatio(1f).align(Alignment.CenterHorizontally))
                    TRACK_TILES.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { s -> Tile(s, signals[s], Modifier.weight(1f)) }
                        }
                    }
                    OutlinedButton(onClick = onCarData, shape = KokpitButtonShape, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch)) { Text("Tüm araç verileri") }
                }
            }
        }
    }
}

@Composable
private fun Big(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Tile(signal: Signal, state: SignalState?, modifier: Modifier) {
    val waiting = signal.source == SignalSource.OBD && state?.answered != true
    Column(modifier.padding(4.dp)) {
        Text(signal.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            if (waiting) "OBD" else formatSignal(signal, state?.value) + if (state?.value != null && signal.unit.isNotEmpty()) " ${signal.unit}" else "",
            style = MaterialTheme.typography.titleMedium,
            color = if (waiting) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Dot in a circle: 1 G at the rim; the peak stays as a faint ring. */
@Composable
private fun GMeter(x: Float, y: Float, modifier: Modifier) {
    var peak by remember { mutableFloatStateOf(0f) }
    val total = sqrt(x * x + y * y)
    if (total > peak) peak = total
    val rim = MaterialTheme.colorScheme.outline
    val dot = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(rim, r, style = Stroke(2f))
            drawCircle(rim, r / 2, style = Stroke(1f))
            drawCircle(dot.copy(alpha = 0.25f), (peak.coerceAtMost(1.2f) / 1.2f) * r, style = Stroke(2f))
            drawCircle(dot, 10f, center + Offset((x / 1.2f).coerceIn(-1f, 1f) * r, (-y / 1.2f).coerceIn(-1f, 1f) * r))
        }
        Text("%.2f G · maks %.2f".format(total, peak).replace('.', ','), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Sideways and fore-aft G from the unit's accelerometer, null when it has none. Gravity is
 * taken off with a slow average, so the unit's mounting angle doesn't matter.
 */
@Composable
private fun rememberGForce(): Pair<Float, Float>? {
    val context = LocalContext.current
    val sensors = remember { context.getSystemService(SensorManager::class.java) }
    val accel = remember { sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } ?: return null
    var g by remember { mutableStateOf(0f to 0f) }
    DisposableEffect(accel) {
        val gravity = FloatArray(3)
        var first = true
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (first) { e.values.copyInto(gravity); first = false }
                for (i in 0..2) gravity[i] = gravity[i] * 0.98f + e.values[i] * 0.02f
                g = (e.values[0] - gravity[0]) / SensorManager.GRAVITY_EARTH to (e.values[1] - gravity[1]) / SensorManager.GRAVITY_EARTH
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sensors.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors.unregisterListener(listener) }
    }
    return g
}

/**
 * Araç verileri: what the unit hands out, live, and what will come with OBD. The answer to
 * "which values can Kokpit show?" on this very car.
 */
@Composable
fun CarDataScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val signals by container.carData.states.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        container.carData.acquire()
        onDispose { container.carData.release() }
    }
    val hasAccel = remember { context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null }
    val trip by container.drive.state.collectAsStateWithLifecycle()
    val connected = container.headUnit.car != null || container.headUnit.common != null
    ScreenScaffold("Araç verileri", onBack) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Dimens.ScreenPadding)) {
            item { Header("Ünitenin verdikleri") }
            item {
                Note(
                    if (connected) "Ünitenin araç servisine bağlı. \"Canlı\" yazanlar araçtan gerçekten geliyor; aracı kullanırken (sinyal, fren, geri vites) değişenler canlıya döner."
                    else "Ünitenin araç servisi bulunamadı: bu ünitede araç verileri Kokpit'e açık değil. Bluetooth OBD ile gelecek.",
                )
            }
            Signal.entries.filter { it.source == SignalSource.UNIT }.forEach { s -> item { DataRow(s, signals[s]) } }
            item { Header("Bluetooth OBD ile gelecek") }
            item { Note("Yağ sıcaklığı, turbo basıncı ve devir ünitenin CAN kutusundan gelmiyor; aracın OBD soketine takılan adaptörle okunacak.") }
            Signal.entries.filter { it.source == SignalSource.OBD }.forEach { s -> item { DataRow(s, signals[s]) } }
            item { Header("Ünitenin kendi sensörleri") }
            item { PlainRow("GPS", if (trip.hasFix) "Konum alıyor" else "Konum yok") }
            item { PlainRow("İvme sensörü (G ölçer)", if (hasAccel) "Var" else "Yok") }
        }
    }
}

@Composable
private fun Header(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))

@Composable
private fun Note(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))

@Composable
private fun DataRow(signal: Signal, state: SignalState?) {
    val status = when {
        signal.source == SignalSource.OBD && state?.answered != true -> "adaptör bekleniyor"
        state?.changed == true -> "canlı"
        state?.answered == true -> "yanıt veriyor, henüz değişmedi"
        else -> "yanıt yok"
    }
    val value = formatSignal(signal, state?.value) + if (state?.value != null && signal.unit.isNotEmpty()) " ${signal.unit}" else ""
    PlainRow(signal.label, "$value   ·   $status")
}

@Composable
private fun PlainRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
