package io.github.yorulmazsinan.kokpit.intro

import android.content.Context
import android.net.Uri
import io.github.yorulmazsinan.kokpit.R
import io.github.yorulmazsinan.kokpit.weather.WeatherState
import java.time.LocalDate
import java.time.Month

enum class Season { SUMMER, WINTER }

/** Welcome clips shipped inside the app: a sunny summer road and a snowy winter one. */
data class BuiltinIntro(val video: IntroVideo, val season: Season, val rawRes: Int)

const val BUILTIN_PREFIX = "builtin:"
const val BUILTIN_SUMMER_ID = "${BUILTIN_PREFIX}yaz"
const val BUILTIN_WINTER_ID = "${BUILTIN_PREFIX}kis"

val BUILTIN_INTROS: List<BuiltinIntro> = listOf(
    BuiltinIntro(IntroVideo(BUILTIN_SUMMER_ID, "Yaz karşılama", 0), Season.SUMMER, R.raw.intro_yaz),
    BuiltinIntro(IntroVideo(BUILTIN_WINTER_ID, "Kış karşılama", 0), Season.WINTER, R.raw.intro_kis),
)

fun isBuiltin(id: String): Boolean = id.startsWith(BUILTIN_PREFIX)

/** Built-in clips first, then the user's own from USB. */
fun allIntroVideos(library: IntroVideoLibrary): List<IntroVideo> = BUILTIN_INTROS.map { it.video } + library.list()

fun builtinUri(context: Context, id: String): Uri? =
    BUILTIN_INTROS.firstOrNull { it.video.id == id }?.let { Uri.parse("android.resource://${context.packageName}/${it.rawRes}") }

/**
 * Summer or winter for the welcome clip. The outside temperature decides when it is clear
 * (≤ 10 °C winter, ≥ 18 °C summer); in between, or without weather, the month does:
 * November–March is winter.
 */
fun seasonFor(date: LocalDate, temperatureC: Double?): Season {
    if (temperatureC != null) {
        if (temperatureC <= 10.0) return Season.WINTER
        if (temperatureC >= 18.0) return Season.SUMMER
    }
    return when (date.month) {
        Month.NOVEMBER, Month.DECEMBER, Month.JANUARY, Month.FEBRUARY, Month.MARCH -> Season.WINTER
        else -> Season.SUMMER
    }
}

/** Outside temperature if the weather is recent enough (12 h) to say anything about today. */
fun currentTemperature(state: WeatherState, nowMs: Long = System.currentTimeMillis()): Double? {
    val weather = when (state) {
        is WeatherState.Fresh -> state.weather
        is WeatherState.Stale -> state.weather
        is WeatherState.NoData -> null
    } ?: return null
    return weather.temperatureC.takeIf { nowMs - weather.fetchedAtEpochMs < 12 * 3_600_000L }
}
