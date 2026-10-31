package io.github.yorulmazsinan.kokpit.media

/** A short in-memory log of media session changes and Kokpit's own commands, for Diagnostics. */
class MediaEventLog(private val capacity: Int = 60) {
    private val entries = ArrayDeque<String>()

    @Synchronized
    fun add(elapsedMs: Long, event: String) {
        val s = elapsedMs / 1000
        entries.addLast("%02d:%02d:%02d %s".format(s / 3600, s / 60 % 60, s % 60, event))
        while (entries.size > capacity) entries.removeFirst()
    }

    @Synchronized
    fun lines(): List<String> = entries.toList()

    companion object {
        val shared = MediaEventLog()
    }
}
