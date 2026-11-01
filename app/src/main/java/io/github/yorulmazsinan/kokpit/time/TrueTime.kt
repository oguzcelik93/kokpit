package io.github.yorulmazsinan.kokpit.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

enum class TimeSource { SYSTEM, GPS, INTERNET, ESTIMATE }

/** A system clock earlier than this cannot be right (it is the firmware's build date after a reset). */
val EARLIEST_PLAUSIBLE_MS: Long = LocalDateTime.of(2026, 1, 1, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

/** Slack when comparing the system clock with the last time Kokpit knew for sure. */
const val PLAUSIBLE_SLACK_MS = 5 * 60_000L

/**
 * Parses an NMEA RMC sentence (`$GPRMC` / `$GNRMC` …) into UTC epoch millis, or null when
 * the receiver has no valid fix (status "V") or the sentence is malformed.
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

/** Is the system clock believable, given the last time Kokpit knew for sure? */
fun systemClockPlausible(systemMs: Long, lastKnownTrueMs: Long): Boolean =
    systemMs >= EARLIEST_PLAUSIBLE_MS && systemMs >= lastKnownTrueMs - PLAUSIBLE_SLACK_MS

/**
 * True time from an anchor taken on the monotonic clock: once learnt, it stays right however
 * often the system clock is reset or changed, for as long as the unit doesn't really reboot.
 */
fun anchoredNow(anchorTrueMs: Long, anchorElapsedMs: Long, elapsedNowMs: Long): Long = anchorTrueMs + (elapsedNowMs - anchorElapsedMs)

/**
 * The time Kokpit shows. This unit has no clock battery: every start it wakes up on the
 * firmware date (1 August 02:00). Apps may not set the system clock, so Kokpit keeps its own:
 * - learnt from GPS, an internet server, or the system clock once someone (CarPlay, NTP)
 *   sets it right;
 * - kept on `elapsedRealtime`, which system-clock resets don't touch;
 * - after a real reboot with no source yet, an *estimate* (last known time + time since
 *   boot), flagged as such so the home screen can say so.
 */
object TrueTime {
    @Volatile var source: TimeSource = TimeSource.SYSTEM
        private set
    @Volatile var syncedAtMs: Long = 0L
        private set
    /** False while showing an estimate. */
    @Volatile var sure: Boolean = true
        private set

    @Volatile private var anchorTrue: Long? = null
    @Volatile private var anchorElapsed: Long = 0L
    @Volatile private var lastKnownTrue: Long = 0L
    private var appContext: Context? = null

    private const val PREFS = "true_time_v2"

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lastKnownTrue = p.getLong("last_known", 0L)
        syncedAtMs = p.getLong("synced_at", 0L)
        val savedBoot = p.getInt("boot", -1)
        val savedElapsed = p.getLong("anchor_elapsed", -1L)
        val now = SystemClock.elapsedRealtime()
        // Same boot (Kokpit restarted, or the unit only slept): the anchor still holds.
        if (savedBoot == bootCount(ctx) && savedElapsed in 0..now && p.contains("anchor_true")) {
            anchorTrue = p.getLong("anchor_true", 0L)
            anchorElapsed = savedElapsed
            source = runCatching { TimeSource.valueOf(p.getString("source", null) ?: "SYSTEM") }.getOrDefault(TimeSource.SYSTEM)
            sure = true
            return
        }
        anchorTrue = null
        if (systemClockPlausible(System.currentTimeMillis(), lastKnownTrue)) {
            source = TimeSource.SYSTEM
            sure = true
        } else {
            source = TimeSource.ESTIMATE
            sure = false
        }
    }

    /** A trusted time arrived (GPS, internet). */
    fun update(context: Context, trueUtcMs: Long, from: TimeSource) {
        anchor(context, trueUtcMs, from)
    }

    /** Someone set the system clock (CarPlay, NTP, the driver): adopt it if it is believable. */
    fun onSystemClockSet(context: Context) {
        val system = System.currentTimeMillis()
        if (!systemClockPlausible(system, lastKnownTrue)) return
        // GPS / internet anchors stay; they are at least as good as whoever set the clock.
        if (anchorTrue != null && (source == TimeSource.GPS || source == TimeSource.INTERNET)) return
        anchor(context, system, TimeSource.SYSTEM)
    }

    /** Remember the time regularly, so the next cold start can at least estimate. */
    fun remember(context: Context) {
        if (!sure) return
        lastKnownTrue = nowMs()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("last_known", lastKnownTrue).apply()
    }

    fun nowMs(): Long {
        val elapsed = SystemClock.elapsedRealtime()
        anchorTrue?.let { return anchoredNow(it, anchorElapsed, elapsed) }
        val system = System.currentTimeMillis()
        if (source != TimeSource.ESTIMATE) return system
        // Last known time plus time since this boot: the car's off-time is unknown, so this
        // is a lower bound, shown as an estimate.
        return if (lastKnownTrue > 0) lastKnownTrue + elapsed else system
    }

    fun instant(): Instant = Instant.ofEpochMilli(nowMs())
    fun dateTime(): LocalDateTime = LocalDateTime.ofInstant(instant(), ZoneId.systemDefault())
    fun time(): LocalTime = dateTime().toLocalTime()
    fun date(): LocalDate = dateTime().toLocalDate()

    private fun anchor(context: Context, trueMs: Long, from: TimeSource) {
        anchorTrue = trueMs
        anchorElapsed = SystemClock.elapsedRealtime()
        source = from
        sure = true
        syncedAtMs = trueMs
        lastKnownTrue = trueMs
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("anchor_true", trueMs).putLong("anchor_elapsed", anchorElapsed).putInt("boot", bootCount(context))
            .putString("source", from.name).putLong("synced_at", trueMs).putLong("last_known", trueMs).apply()
    }

    private fun bootCount(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-2)
}
