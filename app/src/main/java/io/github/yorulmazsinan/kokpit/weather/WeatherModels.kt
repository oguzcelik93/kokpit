package io.github.yorulmazsinan.kokpit.weather

import java.time.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Weather(
    val temperatureC: Double,
    val code: Int,
    val isDay: Boolean,
    val maxC: Double,
    val minC: Double,
    val sunriseLocal: LocalTime,
    val sunsetLocal: LocalTime,
    val fetchedAtEpochMs: Long,
)

enum class WeatherProblem { NO_LOCATION, NO_NETWORK }

sealed interface WeatherState {
    data class NoData(val problem: WeatherProblem) : WeatherState
    data class Fresh(val weather: Weather) : WeatherState
    data class Stale(val weather: Weather) : WeatherState
}

@Serializable
internal data class ForecastDto(val current: CurrentDto, val daily: DailyDto)

@Serializable
internal data class CurrentDto(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int,
)

@Serializable
internal data class DailyDto(
    @SerialName("temperature_2m_max") val max: List<Double>,
    @SerialName("temperature_2m_min") val min: List<Double>,
    val sunrise: List<String>,
    val sunset: List<String>,
)
