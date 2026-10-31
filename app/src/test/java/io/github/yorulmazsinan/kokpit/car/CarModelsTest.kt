package io.github.yorulmazsinan.kokpit.car

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarModelsTest {
    private fun at(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun consumptionBetweenFullTanks() {
        val log = listOf(
            FuelEntry(at(2026, 9, 1), 10_000, 50.0, 2_000.0),
            FuelEntry(at(2026, 9, 15), 10_600, 42.0, 1_700.0),
            FuelEntry(at(2026, 10, 2), 11_000, 20.0, 820.0, full = false),
            FuelEntry(at(2026, 10, 20), 11_500, 33.0, 1_350.0),
        )
        val s = fuelStats(log, YearMonth.of(2026, 10), ZoneOffset.UTC)
        assertEquals((20.0 + 33) / 900 * 100, s.lastL100!!, 0.001) // since the previous full tank at 10 600 km
        assertEquals((42.0 + 20 + 33) / 1_500 * 100, s.averageL100!!, 0.001)
        assertEquals(820.0 + 1_350.0, s.monthCost, 0.001)
        assertEquals(1_500, s.totalKm)
        assertEquals((1_700.0 + 820 + 1_350) / 1_500, s.costPerKm!!, 0.0001)
    }

    @Test
    fun oneEntryHasNoConsumption() {
        val s = fuelStats(listOf(FuelEntry(at(2026, 10, 1), 1_000, 40.0, 1_600.0)), YearMonth.of(2026, 10), ZoneOffset.UTC)
        assertNull(s.averageL100)
        assertNull(s.costPerKm)
        assertEquals(40.0, s.pricePerLiter!!, 0.001)
    }

    @Test
    fun decimalsWithComma() {
        assertEquals(45.6, parseDecimal("45,6")!!, 0.0)
        assertEquals(45.6, parseDecimal(" 45.6 ")!!, 0.0)
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal("0"))
    }

    @Test
    fun navigationPerApp() {
        val point = Place(41.0, 29.0)
        assertEquals(NavSpec("google.navigation:q=41.0,29.0", "com.google.android.apps.maps"), navigationSpec(point, "com.google.android.apps.maps", "Ev"))
        assertEquals("https://waze.com/ul?ll=41.0,29.0&navigate=yes", navigationSpec(point, "com.waze", "Ev")?.uri)
        assertEquals("yandexnavi://build_route_on_map?lat_to=41.0&lon_to=29.0", navigationSpec(point, "ru.yandex.yandexnavi", "Ev")?.uri)
        assertEquals("geo:0,0?q=Ba%C4%9Fdat%20Cad.%201", navigationSpec(Place(address = "Bağdat Cad. 1"), null, "Ev")?.uri)
        assertNull(navigationSpec(Place(), null, "Ev"))
    }

    @Test
    fun dayAndNight() {
        assertEquals(DayPhase.DAY, dayPhase(LocalTime.of(12, 0), LocalTime.of(7, 30), LocalTime.of(17, 45)))
        assertEquals(DayPhase.NIGHT, dayPhase(LocalTime.of(17, 45), LocalTime.of(7, 30), LocalTime.of(17, 45)))
        assertEquals(DayPhase.NIGHT, dayPhase(LocalTime.of(6, 0), null, null))
        assertEquals(DayPhase.DAY, dayPhase(LocalTime.of(7, 0), null, null))
    }

    @Test
    fun qrLinkUsesDots() = assertEquals("https://maps.google.com/?q=41.012345,29.000001", mapsLink(41.012345, 29.000001))
}
