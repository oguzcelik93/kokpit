package io.github.yorulmazsinan.kokpit.drive

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Feeds GPS fixes into [TripTracker] and publishes a snapshot every second. Main thread only. */
class DriveRepository(
    private val scope: CoroutineScope,
    private val locationSource: LocationSource,
    private val tracker: TripTracker,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _state = MutableStateFlow(TripSnapshot(null, null, null, hasFix = false))
    val state: StateFlow<TripSnapshot> = _state.asStateFlow()

    private val _permissionMissing = MutableStateFlow(!locationSource.hasPermission())
    val permissionMissing: StateFlow<Boolean> = _permissionMissing.asStateFlow()

    private var fixJob: Job? = null

    fun start() {
        tracker.onWake(clock())
        refreshPermission()
        scope.launch {
            while (isActive) {
                _state.value = tracker.snapshot(clock())
                delay(1000)
            }
        }
    }

    /** Call after the location permission may have changed. */
    fun refreshPermission() {
        _permissionMissing.value = !locationSource.hasPermission()
        fixJob?.cancel()
        fixJob = scope.launch { locationSource.fixes().collect(tracker::onFix) }
    }

    fun lastPosition(): Pair<Double, Double>? = tracker.lastPosition()

    /** Metres driven in the current trip (resets after a long sleep). */
    fun distanceMeters(): Double = tracker.distanceMeters

    fun onWake(elapsedMs: Long) = tracker.onWake(elapsedMs)

    fun onSleep(elapsedMs: Long) = tracker.onSleep(elapsedMs)
}
