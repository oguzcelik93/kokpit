package io.github.yorulmazsinan.kokpit.connect

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface WakeEvent {
    /** [sleptMs] is null on a cold boot. */
    data class Wake(val elapsedMs: Long, val sleptMs: Long?) : WakeEvent
    data class Sleep(val elapsedMs: Long) : WakeEvent
}

object WakeEvents {
    private val _flow = MutableSharedFlow<WakeEvent>(extraBufferCapacity = 8)
    val flow: SharedFlow<WakeEvent> = _flow.asSharedFlow()

    fun emit(event: WakeEvent) {
        _flow.tryEmit(event)
    }
}
