package io.github.yorulmazsinan.kokpit.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelRulesTest {
    @Test
    fun requestsParse() {
        val r = parsePanelRequest("POST /api/photo/123/rotate?t=abc HTTP/1.1\r\nContent-Length: 10\r\nX-Kokpit-Token: zz\r\n\r\n")!!
        assertEquals("POST", r.method)
        assertEquals("/api/photo/123/rotate", r.path)
        assertEquals("abc", r.query["t"])
        assertEquals("10", r.headers["content-length"])
        assertNull(parsePanelRequest("garbage"))
    }

    @Test
    fun photoIdsCannotCarryPaths() {
        assertTrue(isPhotoId("1793040000000"))
        assertFalse(isPhotoId("../../shared_prefs/settings"))
        assertFalse(isPhotoId("123.jpg"))
        assertFalse(isPhotoId(""))
        assertEquals("123" to "select", photoAction("/api/photo/123/select"))
        assertNull(photoAction("/api/photo/../x/select"))
        assertNull(photoAction("/api/photo/123/format"))
        assertNull(photoAction("/api/photo/123/select/extra"))
    }

    @Test
    fun fuelIsChecked() {
        val ok = parseFuel("""{"km":"85420","liters":"45,3","price":"2160","full":true}""", 1L, 84790)
        assertNotNull(ok)
        assertEquals(45.3, ok!!.liters, 0.0)
        assertNull(parseFuel("""{"km":"84000","liters":"45","price":"2000"}""", 1L, 84790)) // below last odometer
        assertNull(parseFuel("""{"km":"abc","liters":"45","price":"2000"}""", 1L, null))
        assertNull(parseFuel("""{"km":"85000","liters":"4500","price":"2000"}""", 1L, null)) // not a car tank
        assertNull(parseFuel("not json", 1L, null))
    }

    @Test
    fun placesAreChecked() {
        assertEquals("home" to "Bağdat Cad. 1, İstanbul", parsePlace("""{"kind":"home","address":"  Bağdat Cad. 1, İstanbul "}"""))
        assertEquals("work" to null, parsePlace("""{"kind":"work","address":""}""")) // clears
        assertNull(parsePlace("""{"kind":"admin","address":"x y"}"""))
        assertNull(parsePlace("""{"kind":"home","address":"a\u0000b"}"""))
        assertNull(parsePlace("""{"kind":"home","address":"${"x".repeat(300)}"}"""))
    }

    @Test
    fun pageOnlyAcceptsHexTokens() {
        assertTrue(panelPage("0123456789abcdef").contains("var T='0123456789abcdef'"))
        try {
            panelPage("x';alert(1);//")
            throw AssertionError("should refuse")
        } catch (_: IllegalArgumentException) {
        }
    }
}
