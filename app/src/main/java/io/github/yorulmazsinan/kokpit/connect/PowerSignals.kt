package io.github.yorulmazsinan.kokpit.connect

/** Ignition, as reported by head-unit firmware (screen on/off is not reliable on these units). */
enum class PowerSignal { ON, OFF }

/** Broadcasts that mean ignition on/off. Autochips QuickBoot units send QB_POWERON/QB_POWEROFF. */
val POWER_ACTIONS: Map<String, PowerSignal> = mapOf(
    "autochips.intent.action.QB_POWERON" to PowerSignal.ON,
    "autochips.intent.action.QB_POWEROFF" to PowerSignal.OFF,
    "comm.power.POWER_ON" to PowerSignal.ON,
    "comm.power.POWER_OFF" to PowerSignal.OFF,
    "com.cusc.action.ACC_OFF" to PowerSignal.OFF,
    // MediaTek "IPO" fast boot, used by many MTK-based units instead of a real reboot.
    "android.intent.action.ACTION_BOOT_IPO" to PowerSignal.ON,
    "android.intent.action.ACTION_SHUTDOWN_IPO" to PowerSignal.OFF,
    "android.intent.action.ACTION_SHUTDOWN" to PowerSignal.OFF,
)

/**
 * Units with no ignition broadcast (XY Auto) still switch the screen off with the ignition.
 * A screen that was off at least this long and comes back on counts as ignition on.
 */
const val SCREEN_WAKE_MS = 20_000L

/** Off long enough to be an ignition cycle rather than a glance at the screen-off button. */
fun screenOffMeansIgnition(offAt: Long?, nowElapsed: Long, threshold: Long = SCREEN_WAKE_MS): Long? =
    offAt?.takeIf { it <= nowElapsed }?.let { nowElapsed - it }?.takeIf { it >= threshold }

fun powerSignalFor(action: String?): PowerSignal? = action?.let(POWER_ACTIONS::get)

/**
 * How long the unit slept, from a sleep time stored before the process was killed.
 * QuickBoot keeps `elapsedRealtime` running; a value lower than the stored one means a real reboot.
 */
fun sleptFor(storedSleepAt: Long?, nowElapsed: Long): Long? =
    storedSleepAt?.takeIf { it <= nowElapsed }?.let { nowElapsed - it }

/** Heartbeat period; a gap well beyond it means the CPU was asleep (ignition off). */
const val HEARTBEAT_MS = 15_000L
const val WAKE_GAP_MS = 90_000L

/**
 * Some units send no ignition broadcast at all. `elapsedRealtime` keeps counting while the
 * unit sleeps but Kokpit's heartbeat does not, so a long gap between beats is a wake-up.
 * Returns the gap, or null (no previous beat, a reboot reset the clock, or a normal beat).
 */
fun heartbeatGap(lastBeat: Long?, nowElapsed: Long, threshold: Long = WAKE_GAP_MS): Long? =
    lastBeat?.takeIf { it <= nowElapsed }?.let { nowElapsed - it }?.takeIf { it >= threshold }

/** One ignition is often reported several ways (broadcast, heartbeat gap, restart); act once. */
const val WAKE_DEBOUNCE_MS = 120_000L

fun isRepeatWake(lastWake: Long?, nowElapsed: Long): Boolean =
    lastWake != null && lastWake <= nowElapsed && nowElapsed - lastWake < WAKE_DEBOUNCE_MS

/** No intro once the car is already moving (a screen turned off while driving can look like sleep). */
const val INTRO_MAX_SPEED_KMH = 10

fun introAllowedAt(speedKmh: Int?): Boolean = speedKmh == null || speedKmh < INTRO_MAX_SPEED_KMH
