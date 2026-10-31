package io.github.yorulmazsinan.kokpit.intro

import io.github.yorulmazsinan.kokpit.system.BRIGHTNESS_MAX
import io.github.yorulmazsinan.kokpit.system.BRIGHTNESS_MIN
import io.github.yorulmazsinan.kokpit.system.brightnessPercent
import io.github.yorulmazsinan.kokpit.system.nextBrightness
import io.github.yorulmazsinan.kokpit.weather.Weather
import io.github.yorulmazsinan.kokpit.weather.WeatherState
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeasonalIntroTest {
    private val videos = listOf(
        IntroVideo(BUILTIN_SUMMER_ID, "Yaz", 0),
        IntroVideo(BUILTIN_WINTER_ID, "Kış", 0),
        IntroVideo("own", "Benim.mp4", 10),
    )

    @Test
    fun temperatureDecidesWhenClear() {
        assertEquals(Season.WINTER, seasonFor(LocalDate.of(2026, 7, 1), 4.0))
        assertEquals(Season.SUMMER, seasonFor(LocalDate.of(2026, 1, 15), 24.0))
    }

    @Test
    fun monthDecidesOtherwise() {
        assertEquals(Season.WINTER, seasonFor(LocalDate.of(2026, 11, 2), null))
        assertEquals(Season.WINTER, seasonFor(LocalDate.of(2026, 3, 31), 14.0))
        assertEquals(Season.SUMMER, seasonFor(LocalDate.of(2026, 4, 1), null))
        assertEquals(Season.SUMMER, seasonFor(LocalDate.of(2026, 10, 31), 14.0))
    }

    @Test
    fun seasonalModePicksTheMatchingBuiltin() {
        val s = IntroSettings(mode = IntroMode.SEASONAL)
        assertEquals(BUILTIN_WINTER_ID, pickIntroVideo(s, videos, null, Random(1), Season.WINTER)?.id)
        assertEquals(BUILTIN_SUMMER_ID, pickIntroVideo(s, videos, null, Random(1), Season.SUMMER)?.id)
    }

    @Test
    fun seasonalIsTheDefault() = assertEquals(IntroMode.SEASONAL, IntroSettings().mode)

    @Test
    fun staleWeatherIsIgnored() {
        val w = Weather(5.0, 0, true, 8.0, 1.0, LocalTime.NOON, LocalTime.NOON, fetchedAtEpochMs = 0)
        assertEquals(5.0, currentTemperature(WeatherState.Stale(w), nowMs = 3_600_000L)!!, 0.0)
        assertNull(currentTemperature(WeatherState.Stale(w), nowMs = 13 * 3_600_000L))
    }

    @Test
    fun brightnessSteps() {
        assertEquals(25, nextBrightness(10, up = true))
        assertEquals(140, nextBrightness(120, up = true))
        assertEquals(100, nextBrightness(120, up = false))
        assertEquals(BRIGHTNESS_MAX, nextBrightness(255, up = true))
        assertEquals(BRIGHTNESS_MIN, nextBrightness(10, up = false))
        assertEquals(0, brightnessPercent(BRIGHTNESS_MIN))
        assertEquals(100, brightnessPercent(BRIGHTNESS_MAX))
    }
}
