package io.github.yorulmazsinan.kokpit.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Android's screen brightness range as Kokpit uses it (never fully black). */
const val BRIGHTNESS_MIN = 10
const val BRIGHTNESS_MAX = 255

/** Steps for the − / + buttons: big jumps at the bright end, fine ones at night. */
val BRIGHTNESS_STEPS = listOf(10, 25, 45, 70, 100, 140, 190, 255)

fun nextBrightness(current: Int, up: Boolean): Int =
    if (up) BRIGHTNESS_STEPS.firstOrNull { it > current } ?: BRIGHTNESS_MAX
    else BRIGHTNESS_STEPS.lastOrNull { it < current } ?: BRIGHTNESS_MIN

fun brightnessPercent(value: Int): Int =
    ((value - BRIGHTNESS_MIN) * 100f / (BRIGHTNESS_MAX - BRIGHTNESS_MIN)).toInt().coerceIn(0, 100)

/** System-wide screen brightness (WRITE_SETTINGS, which the user allows once). */
object Brightness {
    fun canWrite(context: Context): Boolean = runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    fun get(context: Context): Int? =
        runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()

    /** Sets brightness; automatic mode is switched off, or it would undo the change. */
    fun set(context: Context, value: Int): Boolean = runCatching {
        val resolver = context.contentResolver
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX))
    }.getOrDefault(false)

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
}
