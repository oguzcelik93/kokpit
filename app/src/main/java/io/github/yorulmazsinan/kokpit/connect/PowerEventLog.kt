package io.github.yorulmazsinan.kokpit.connect

import android.content.Context

/** Ignition events kept across process restarts (QuickBoot may kill Kokpit at ignition off). */
object PowerEventLog {
    private const val PREFS = "power_events"
    private const val KEY_LINES = "lines"
    private const val KEY_SLEEP_AT = "sleep_at"
    private const val KEY_BEAT = "heartbeat"
    private const val MAX_LINES = 30

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(context: Context, line: String) {
        val p = prefs(context)
        val lines = (p.getString(KEY_LINES, "").orEmpty().lines().filter { it.isNotBlank() } + line).takeLast(MAX_LINES)
        p.edit().putString(KEY_LINES, lines.joinToString("\n")).apply()
    }

    fun lines(context: Context): List<String> = prefs(context).getString(KEY_LINES, "").orEmpty().lines().filter { it.isNotBlank() }

    fun storeSleepAt(context: Context, elapsedMs: Long) = prefs(context).edit().putLong(KEY_SLEEP_AT, elapsedMs).commit()

    /** Returns and clears the stored sleep time, if any. */
    fun takeSleepAt(context: Context): Long? {
        val p = prefs(context)
        if (!p.contains(KEY_SLEEP_AT)) return null
        val value = p.getLong(KEY_SLEEP_AT, 0L)
        p.edit().remove(KEY_SLEEP_AT).commit()
        return value
    }

    /** Stores this beat and returns the previous one. */
    fun beat(context: Context, elapsedMs: Long): Long? {
        val p = prefs(context)
        val last = if (p.contains(KEY_BEAT)) p.getLong(KEY_BEAT, 0L) else null
        p.edit().putLong(KEY_BEAT, elapsedMs).apply()
        return last
    }
}
