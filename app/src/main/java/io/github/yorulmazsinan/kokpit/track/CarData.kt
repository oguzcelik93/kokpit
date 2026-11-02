package io.github.yorulmazsinan.kokpit.track

import io.github.yorulmazsinan.kokpit.headunit.HeadUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Where a value comes from: the unit's CAN decoder today, a Bluetooth OBD adapter later. */
enum class SignalSource { UNIT, OBD }

/**
 * Every car value Kokpit knows how to show. [UNIT] ones are what the head unit's vendor
 * services hand out (only when a CAN decoder is fitted and set up); [OBD] ones need the
 * adapter (stage 2) — the screens already have a place for them.
 */
enum class Signal(val label: String, val unit: String, val source: SignalSource, val decimals: Int = 0) {
    SPEED("Hız (araçtan)", "km/h", SignalSource.UNIT),
    OUTSIDE_TEMP("Dış sıcaklık", "°C", SignalSource.UNIT, 1),
    FUEL("Yakıt (araçtan)", "", SignalSource.UNIT),
    ODOMETER("Kilometre (araçtan)", "km", SignalSource.UNIT),
    BRAKE("Fren / el freni", "", SignalSource.UNIT),
    REVERSE("Geri vites", "", SignalSource.UNIT),
    LEFT_SIGNAL("Sol sinyal", "", SignalSource.UNIT),
    RIGHT_SIGNAL("Sağ sinyal", "", SignalSource.UNIT),
    HAZARD("Dörtlü", "", SignalSource.UNIT),
    HEADLIGHTS("Farlar / gece", "", SignalSource.UNIT),

    RPM("Devir", "d/dk", SignalSource.OBD),
    BOOST("Turbo basıncı", "bar", SignalSource.OBD, 2),
    OIL_TEMP("Yağ sıcaklığı", "°C", SignalSource.OBD),
    COOLANT_TEMP("Su sıcaklığı", "°C", SignalSource.OBD),
    INTAKE_TEMP("Emme havası", "°C", SignalSource.OBD),
    THROTTLE("Gaz pedalı", "%", SignalSource.OBD),
    BATTERY("Akü", "V", SignalSource.OBD, 1),
}

/** What we know of one signal: its latest value, and whether it ever answered or moved. */
data class SignalState(val value: Double? = null, val answered: Boolean = false, val changed: Boolean = false) {
    fun next(v: Double?): SignalState = SignalState(
        value = v,
        answered = answered || v != null,
        changed = changed || (v != null && value != null && v != value),
    )
}

/** "23.5°C", "23,5 C", "-4" → number; anything without a number → null. */
fun parseTemperature(text: String?): Double? =
    Regex("""-?\d+(?:[.,]\d+)?""").find(text ?: return null)?.value?.replace(',', '.')?.toDouble()

/** A value for the screen: "—" when there is none, on/off for switch-like signals. */
fun formatSignal(signal: Signal, value: Double?): String {
    if (value == null) return "—"
    if (signal.unit.isEmpty() && signal != Signal.FUEL) return if (value != 0.0) "Açık" else "Kapalı"
    return if (signal.decimals == 0) "%d".format(Math.round(value)) else "%.${signal.decimals}f".format(value).replace('.', ',')
}

/**
 * Reads the car values the unit already hands out, only while a screen asks for them
 * ([acquire]/[release]), a few times a second. Read-only: the same documented calls the
 * report's "[head unit]" section uses, nothing is ever sent to the car.
 */
class CarData(private val headUnit: HeadUnit, private val scope: CoroutineScope) {
    private val _states = MutableStateFlow(Signal.entries.associateWith { SignalState() })
    val states: StateFlow<Map<Signal, SignalState>> = _states.asStateFlow()

    private var users = 0
    private var job: Job? = null

    @Synchronized
    fun acquire() {
        if (users++ > 0) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val read = readUnit()
                _states.value = _states.value.mapValues { (s, st) -> if (s.source == SignalSource.UNIT) st.next(read[s]) else st }
                delay(POLL_MS)
            }
        }
    }

    @Synchronized
    fun release() {
        if (--users > 0) return
        users = 0
        job?.cancel()
        job = null
    }

    private fun readUnit(): Map<Signal, Double?> {
        val c = headUnit.common
        val k = headUnit.car
        fun Int?.d() = this?.toDouble()
        return mapOf(
            Signal.SPEED to (k?.speed().d() ?: c?.speed()?.toDouble()),
            Signal.OUTSIDE_TEMP to parseTemperature(c?.temperature()),
            Signal.FUEL to k?.fuelLeft().d(),
            Signal.ODOMETER to k?.totalKilometres().d(),
            Signal.BRAKE to c?.brakeState().d(),
            Signal.REVERSE to c?.reverseState().d(),
            Signal.LEFT_SIGNAL to k?.leftSignal().d(),
            Signal.RIGHT_SIGNAL to k?.rightSignal().d(),
            Signal.HAZARD to k?.hazard().d(),
            Signal.HEADLIGHTS to c?.nightMode()?.let { if (it) 1.0 else 0.0 },
        )
    }

    private companion object {
        const val POLL_MS = 300L
    }
}
