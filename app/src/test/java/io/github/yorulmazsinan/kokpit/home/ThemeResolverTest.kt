package io.github.yorulmazsinan.kokpit.home

import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolverTest {
    private val sunrise = LocalTime.of(7, 0)
    private val sunset = LocalTime.of(17, 30)

    @Test
    fun autoIsLightBetweenSunriseAndSunset() {
        assertFalse(resolveDark(ThemeMode.AUTO, LocalTime.NOON, sunrise, sunset))
        assertTrue(resolveDark(ThemeMode.AUTO, LocalTime.of(6, 59), sunrise, sunset))
        assertTrue(resolveDark(ThemeMode.AUTO, LocalTime.of(17, 30), sunrise, sunset))
    }

    @Test
    fun autoIsDarkWithoutSunTimes() {
        assertTrue(resolveDark(ThemeMode.AUTO, LocalTime.NOON, null, null))
    }

    @Test
    fun fixedModesIgnoreTime() {
        assertTrue(resolveDark(ThemeMode.DARK, LocalTime.NOON, sunrise, sunset))
        assertFalse(resolveDark(ThemeMode.LIGHT, LocalTime.MIDNIGHT, sunrise, sunset))
    }
}
