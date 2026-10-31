package io.github.yorulmazsinan.kokpit.media

data class SessionSnapshot(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val isPlaying: Boolean,
    val lastActiveMs: Long,
)

/** The session worth showing: the most recent playing one, else the most recent one with a title. */
fun selectSession(sessions: List<SessionSnapshot>): SessionSnapshot? {
    val titled = sessions.filter { !it.title.isNullOrBlank() }
    return titled.filter { it.isPlaying }.maxByOrNull { it.lastActiveMs }
        ?: titled.maxByOrNull { it.lastActiveMs }
}
