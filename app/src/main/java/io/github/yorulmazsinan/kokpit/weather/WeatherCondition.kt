package io.github.yorulmazsinan.kokpit.weather

import androidx.annotation.StringRes
import io.github.yorulmazsinan.kokpit.R

enum class WeatherCondition(@StringRes val label: Int) {
    CLEAR(R.string.weather_clear),
    PARTLY_CLOUDY(R.string.weather_partly_cloudy),
    CLOUDY(R.string.weather_cloudy),
    FOG(R.string.weather_fog),
    DRIZZLE(R.string.weather_drizzle),
    RAIN(R.string.weather_rain),
    SNOW(R.string.weather_snow),
    SHOWERS(R.string.weather_showers),
    THUNDER(R.string.weather_thunder);

    companion object {
        /** WMO weather interpretation codes as used by Open-Meteo. */
        fun fromWmo(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 80..82 -> SHOWERS
            in 95..99 -> THUNDER
            else -> CLOUDY
        }
    }
}
