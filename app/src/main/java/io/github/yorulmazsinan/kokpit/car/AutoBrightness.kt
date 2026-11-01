package io.github.yorulmazsinan.kokpit.car

import android.content.Context
import io.github.yorulmazsinan.kokpit.connect.WakeEvent
import io.github.yorulmazsinan.kokpit.connect.WakeEvents
import io.github.yorulmazsinan.kokpit.settings.KokpitSettings
import io.github.yorulmazsinan.kokpit.system.Brightness
import io.github.yorulmazsinan.kokpit.weather.WeatherState
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Day/night brightness by sunrise and sunset. It only acts when the phase changes (or the
 * unit wakes up), so a manual change with the brightness panel holds until the next sunrise
 * or sunset.
 */
class AutoBrightness(
    private val context: Context,
    private val settings: StateFlow<KokpitSettings>,
    private val weather: StateFlow<WeatherState>,
    private val nightPanel: MutableStateFlow<Boolean>,
) {
    private var applied: DayPhase? = null
    private var panelOpenedByUs = false

    fun start(scope: CoroutineScope) {
        scope.launch {
            WakeEvents.flow.collect { if (it is WakeEvent.Wake) applied = null }
        }
        scope.launch {
            while (isActive) {
                tick()
                delay(30_000)
            }
        }
    }

    /** Forget the last phase so the next tick applies again (e.g. after a settings change). */
    fun reapply() {
        applied = null
        tick()
    }

    private fun tick() {
        val s = settings.value
        val w = when (val state = weather.value) {
            is WeatherState.Fresh -> state.weather
            is WeatherState.Stale -> state.weather
            is WeatherState.NoData -> null
        }
        val phase = dayPhase(io.github.yorulmazsinan.kokpit.time.TrueTime.time(), w?.sunriseLocal, w?.sunsetLocal)
        if (phase == applied) return
        applied = phase
        if (s.autoBrightness && Brightness.canWrite(context)) {
            Brightness.set(context, if (phase == DayPhase.DAY) s.dayBrightness else s.nightBrightness)
        }
        if (s.autoNightPanel) {
            if (phase == DayPhase.NIGHT && !nightPanel.value) {
                nightPanel.value = true
                panelOpenedByUs = true
            } else if (phase == DayPhase.DAY && panelOpenedByUs) {
                nightPanel.value = false
                panelOpenedByUs = false
            }
        }
    }
}
