package io.github.yorulmazsinan.kokpit.weather

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

fun sampleJson(): String = OpenMeteoParseTest::class.java.getResource("/open_meteo_sample.json")!!.readText()

class OpenMeteoParseTest {
    @Test
    fun parsesSample() {
        val w = OpenMeteoClient.parse(sampleJson(), fetchedAtEpochMs = 42L)
        assertEquals(18.4, w.temperatureC, 0.0)
        assertEquals(2, w.code)
        assertTrue(w.isDay)
        assertEquals(21.0, w.maxC, 0.0)
        assertEquals(12.1, w.minC, 0.0)
        assertEquals(LocalTime.of(6, 58), w.sunriseLocal)
        assertEquals(LocalTime.of(17, 21), w.sunsetLocal)
        assertEquals(42L, w.fetchedAtEpochMs)
    }

    @Test
    fun wmoMapping() {
        assertEquals(WeatherCondition.CLEAR, WeatherCondition.fromWmo(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.fromWmo(2))
        assertEquals(WeatherCondition.CLOUDY, WeatherCondition.fromWmo(3))
        assertEquals(WeatherCondition.FOG, WeatherCondition.fromWmo(45))
        assertEquals(WeatherCondition.DRIZZLE, WeatherCondition.fromWmo(53))
        assertEquals(WeatherCondition.RAIN, WeatherCondition.fromWmo(63))
        assertEquals(WeatherCondition.SNOW, WeatherCondition.fromWmo(86))
        assertEquals(WeatherCondition.SHOWERS, WeatherCondition.fromWmo(81))
        assertEquals(WeatherCondition.THUNDER, WeatherCondition.fromWmo(96))
        assertEquals(WeatherCondition.CLOUDY, WeatherCondition.fromWmo(1234))
    }
}
