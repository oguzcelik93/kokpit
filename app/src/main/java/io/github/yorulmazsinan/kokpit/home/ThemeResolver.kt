package io.github.yorulmazsinan.kokpit.home

import java.time.LocalTime

/** AUTO is light from sunrise until sunset, dark otherwise (and dark when the times are unknown). */
fun resolveDark(mode: ThemeMode, now: LocalTime, sunrise: LocalTime?, sunset: LocalTime?): Boolean = when (mode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.AUTO -> sunrise == null || sunset == null || now < sunrise || now >= sunset
}
