package io.github.yorulmazsinan.kokpit.calls

enum class CallType { INCOMING, OUTGOING, MISSED }

data class CallEntry(val name: String?, val number: String, val type: CallType, val timeMs: Long) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: number
}

/** The latest calls, one per number (spaces and punctuation ignored), newest first. */
fun recentCalls(entries: List<CallEntry>, limit: Int = 3): List<CallEntry> =
    entries.sortedByDescending { it.timeMs }
        .distinctBy { call -> call.number.filter { it.isDigit() || it == '+' }.ifEmpty { call.number } }
        .take(limit)
