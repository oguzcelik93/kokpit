package io.github.yorulmazsinan.kokpit.panel

import io.github.yorulmazsinan.kokpit.car.tokenMatches
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId

class PanelFeaturesTest {
    @Test
    fun closedPanelTokenMatchesNothing() {
        assertFalse(tokenMatches("", ""))
        assertFalse(tokenMatches("", null))
        assertTrue(tokenMatches("abc", "abc"))
    }

    @Test
    fun sharedRouteNeedsAPointOrAName() {
        assertEquals(SharedRoute(41.0256, 28.9741, "Galata Kulesi"), parseSharedRoute("""{"lat":"41.0256","lon":"28.9741","label":"Galata Kulesi"}"""))
        assertEquals(SharedRoute(null, null, "Bağdat Cd. 10"), parseSharedRoute("""{"label":" Bağdat Cd. 10 "}"""))
        assertEquals("41.00000, 29.00000", parseSharedRoute("""{"lat":41,"lon":29}""")!!.label)
        assertNull(parseSharedRoute("""{"label":""}"""))
        assertNull(parseSharedRoute("""{"lat":"95","lon":"10","label":"x"}"""))
        assertNull(parseSharedRoute("""{"lat":"41","label":"x"}"""))
        assertNull(parseSharedRoute("""{"lat":"0","lon":"0"}"""))
        assertNull(parseSharedRoute("""{"label":"a\u0000b"}"""))
        assertNull(parseSharedRoute("""{"label":"${"x".repeat(201)}"}"""))
        assertNull(parseSharedRoute("not json"))
    }

    @Test
    fun remoteTakesOnlyItsButtons() {
        assertEquals("next", parseMediaAction("""{"action":"next"}"""))
        assertEquals("vol_up", parseMediaAction("""{"action":"vol_up"}"""))
        assertNull(parseMediaAction("""{"action":"shell"}"""))
        assertNull(parseMediaAction("{}"))
    }

    @Test
    fun fuelDateFromReceiptWithinTwoMonths() {
        val zone = ZoneId.of("Europe/Istanbul")
        val now = LocalDate.of(2026, 11, 2).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val back = parseFuel("""{"km":"1000","liters":"40","price":"2000","date":"2026-10-30"}""", now, null, zone)!!
        assertEquals(LocalDate.of(2026, 10, 30), java.time.Instant.ofEpochMilli(back.timeMs).atZone(zone).toLocalDate())
        assertEquals(now, parseFuel("""{"km":"1000","liters":"40","price":"2000","date":""}""", now, null, zone)!!.timeMs)
        assertEquals(now, parseFuel("""{"km":"1000","liters":"40","price":"2000","date":"2026-11-02"}""", now, null, zone)!!.timeMs)
        assertNull(parseFuel("""{"km":"1000","liters":"40","price":"2000","date":"2026-11-03"}""", now, null, zone))
        assertNull(parseFuel("""{"km":"1000","liters":"40","price":"2000","date":"2026-08-01"}""", now, null, zone))
        assertNull(parseFuel("""{"km":"1000","liters":"40","price":"2000","date":"dün"}""", now, null, zone))
    }

    @Test
    fun deviceKeyIsSixtyFourHex() {
        assertTrue(isDeviceKey("a".repeat(64)))
        assertFalse(isDeviceKey("A".repeat(64)))
        assertFalse(isDeviceKey("a".repeat(63)))
        assertFalse(isDeviceKey(null))
    }

    @Test
    fun replyCarriesExtraHeaderBeforeTheBody() {
        val out = ByteArrayOutputStream()
        writePanelReply(out, 200, "text/plain", "s".toByteArray(), mapOf(DEVICE_KEY_HEADER to "k"))
        val text = out.toString("UTF-8")
        assertTrue(text.contains("\r\nx-kokpit-device-key: k\r\n"))
        assertTrue(text.endsWith("\r\n\r\ns"))
    }

    @Test
    fun driverProfilesSettingIsOnTheList() {
        val s = io.github.yorulmazsinan.kokpit.settings.KokpitSettings()
        assertEquals(false, applyPanelSettings(s, """{"driverProfiles":false}""")!!.driverProfiles)
        assertTrue(panelSettingsJson(s).contains("\"driverProfiles\":true"))
    }

    @Test
    fun pageHasTheNewTabs() {
        val page = panelPage("0123456789abcdef0123456789abcdef")
        assertNotNull(Regex("data-t=\"kumanda\"").find(page))
        assertTrue(page.contains("kumanda://fis"))
        assertTrue(page.contains("/api/pin/device"))
    }
}
