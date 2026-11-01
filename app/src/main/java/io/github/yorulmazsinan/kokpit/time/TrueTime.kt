package io.github.yorulmazsinan.kokpit.time

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

enum class TimeSource { SYSTEM, GPS, INTERNET }

/** Corrections smaller than this are noise; the system clock is then trusted as is. */
const val TIME_TOLERANCE_MS = 2_000L

/**
 * Parses an NMEA RMC sentence (`$GPRMC` / `$GNRMC` …) into UTC epoch millis, or null when
 * the receiver has no valid fix (status "V") or the sentence is malformed.
 * `$GNRMC,203512.00,A,4836.1,N,00841.2,E,0.0,,011126,,,A*7C` → 2026-11-01T20:35:12Z
 */
fun parseRmcUtc(sentence: String): Long? {
    val body = sentence.trim().substringBefore('*')
    val f = body.split(',')
    if (f.size < 10 || !f[0].endsWith("RMC") || f[2] != "A") return null
    val t = f[1]
    val d = f[9]
    if (t.length < 6 || d.length != 6) return null
    return runCatching {
        val hh = t.substring(0, 2).toInt()
        val mm = t.substring(2, 4).toInt()
        val ss = t.substring(4, 6).toInt()
        val ms = t.substringAfter('.', "").padEnd(3, '0').take(3).toIntOrNull() ?: 0
        val day = d.substring(0, 2).toInt()
        val month = d.substring(2, 4).toInt()
        val year = 2000 + d.substring(4, 6).toInt()
        LocalDateTime.of(year, month, day, hh, mm, ss, ms * 1_000_000).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
}

/** The offset to add to the system clock, or 0 when within tolerance. */
fun clockOffset(trueUtcMs: Long, systemMs: Long): Long = (trueUtcMs - systemMs).let { if (abs(it) < TIME_TOLERANCE_MS) 0L else it }

/**
 * The time Kokpit shows. Head units without a clock battery wake up on the firmware's
 * build date (e.g. 1 August 02:06) and only fix it with internet. Apps may not set the
 * system clock, so Kokpit keeps an offset, learnt from GPS satellites or an internet
 * server, and adds it everywhere it shows or records time.
 */
object TrueTime {
    @Volatile var offsetMs: Long = 0L
        private set
    @Volatile var source: TimeSource = TimeSource.SYSTEM
        private set
    @Volatile var syncedAtMs: Long = 0L
        private set

    private const val PREFS = "true_time"

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        offsetMs = p.getLong("offset", 0L)
        source = runCatching { TimeSource.valueOf(p.getString("source", null) ?: "SYSTEM") }.getOrDefault(TimeSource.SYSTEM)
        syncedAtMs = p.getLong("synced_at", 0L)
    }

    fun update(context: Context, trueUtcMs: Long, from: TimeSource) {
        offsetMs = clockOffset(trueUtcMs, System.currentTimeMillis())
        source = from
        syncedAtMs = trueUtcMs
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("offset", offsetMs).putString("source", from.name).putLong("synced_at", trueUtcMs).apply()
    }

    fun nowMs(): Long = System.currentTimeMillis() + offsetMs
    fun instant(): Instant = Instant.ofEpochMilli(nowMs())
    fun dateTime(): LocalDateTime = LocalDateTime.ofInstant(instant(), ZoneId.systemDefault())
    fun time(): LocalTime = dateTime().toLocalTime()
    fun date(): LocalDate = dateTime().toLocalDate()
}
