package io.github.yorulmazsinan.kokpit.weather

import java.io.IOException
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenMeteoClient(
    http: OkHttpClient,
    private val baseUrl: String = "https://api.open-meteo.com",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val http = http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** @throws IOException on network failure or a non-2xx response. */
    suspend fun fetch(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "%s/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto&forecast_days=1",
            baseUrl, lat, lon,
        )
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Open-Meteo HTTP ${response.code}")
            parse(response.body.string(), clock())
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(body: String, fetchedAtEpochMs: Long): Weather {
            val dto = json.decodeFromString(ForecastDto.serializer(), body)
            return Weather(
                temperatureC = dto.current.temperature,
                code = dto.current.weatherCode,
                isDay = dto.current.isDay == 1,
                maxC = dto.daily.max.first(),
                minC = dto.daily.min.first(),
                sunriseLocal = LocalDateTime.parse(dto.daily.sunrise.first()).toLocalTime(),
                sunsetLocal = LocalDateTime.parse(dto.daily.sunset.first()).toLocalTime(),
                fetchedAtEpochMs = fetchedAtEpochMs,
            )
        }
    }
}
