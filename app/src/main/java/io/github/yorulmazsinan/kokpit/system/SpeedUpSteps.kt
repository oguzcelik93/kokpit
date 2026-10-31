package io.github.yorulmazsinan.kokpit.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** The no-computer speed-up, as a checklist the driver works through once. */
enum class SpeedUpStep { MEMORY, BLOAT, ANIMATIONS, BATTERY }

data class SpeedUpStatus(val done: Set<SpeedUpStep>, val bloatLeft: Int, val animationScale: Float) {
    val remaining: Int get() = SpeedUpStep.entries.size - done.size
}

/** Animations count as done at 0.5× or faster; memory once freed this session. */
fun speedUpStatus(memoryFreed: Boolean, bloatLeft: Int, animationScale: Float, batteryExempt: Boolean): SpeedUpStatus {
    val done = buildSet {
        if (memoryFreed) add(SpeedUpStep.MEMORY)
        if (bloatLeft == 0) add(SpeedUpStep.BLOAT)
        if (animationScale <= 0.5f) add(SpeedUpStep.ANIMATIONS)
        if (batteryExempt) add(SpeedUpStep.BATTERY)
    }
    return SpeedUpStatus(done, bloatLeft, animationScale)
}

object SpeedUp {
    private const val PREFS = "speedup"
    private const val KEY_SEEN = "wizard_seen"

    fun readStatus(context: Context, memoryFreed: Boolean): SpeedUpStatus = speedUpStatus(
        memoryFreed = memoryFreed,
        bloatLeft = Performance.knownBloat(context.packageManager).count { it.enabled },
        animationScale = Performance.currentAnimationScale(context),
        batteryExempt = isBatteryExempt(context),
    )

    fun isBatteryExempt(context: Context): Boolean =
        runCatching { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(true)

    /** Asks Android to stop putting Kokpit to sleep (keeps the service and resume-playback alive). */
    @SuppressLint("BatteryLife")
    fun batteryExemptIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Developer options hold the three animation scales; hidden until "build number" is tapped 7×. */
    fun developerOptionsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    fun aboutPhoneIntent(): Intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)

    fun wizardSeen(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

    fun markWizardSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply()
    }
}
