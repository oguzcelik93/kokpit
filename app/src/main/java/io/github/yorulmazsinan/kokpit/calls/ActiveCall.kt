package io.github.yorulmazsinan.kokpit.calls

enum class CallPhase { INCOMING, DIALING, ACTIVE, HELD }

/** The call in progress on the paired phone; [connectedAt] is `elapsedRealtime` when it was answered. */
data class ActiveCall(val phase: CallPhase, val number: String?, val name: String?, val connectedAt: Long?) {
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: number?.takeIf { it.isNotBlank() }.orEmpty()
}

/** `BluetoothHeadsetClientCall` states (0 active … 7 terminated); null when no call is going on. */
fun callPhaseFor(state: Int?): CallPhase? = when (state) {
    0 -> CallPhase.ACTIVE
    1, 6 -> CallPhase.HELD
    2, 3 -> CallPhase.DIALING
    4, 5 -> CallPhase.INCOMING
    else -> null
}

/** Digits only (leading + kept), so "0555 111 22 33" and "+90 555 111 22 33" can be compared. */
fun sameNumber(a: String?, b: String?): Boolean {
    fun tail(n: String?) = n?.filter(Char::isDigit)?.takeLast(10)
    val x = tail(a)
    return !x.isNullOrEmpty() && x.length >= 7 && x == tail(b)
}
