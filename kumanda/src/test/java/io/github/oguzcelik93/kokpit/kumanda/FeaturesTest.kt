package io.github.oguzcelik93.kokpit.kumanda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeaturesTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun amounts() {
        assertEquals(2160.36, parseAmount("2.160,36")!!, 1e-9)
        assertEquals(2160.36, parseAmount("*2160,36")!!, 1e-9)
        assertEquals(2160.36, parseAmount("2160.36")!!, 1e-9)
        assertEquals(2160.0, parseAmount("2.160")!!, 1e-9)
        assertEquals(45.3, parseAmount("45,300", threeDecimals = true)!!, 1e-9)
        assertEquals(45.3, parseAmount("45.300", threeDecimals = true)!!, 1e-9)
        assertNull(parseAmount("12a"))
    }

    @Test
    fun typicalPumpReceipt() {
        val text = """
            OPET PETROLCÜLÜK
            TARİH: 01/11/2026   SAAT: 14:32
            KURŞUNSUZ BENZİN 95
            45,30 LT X 47,69
                                *2.160,36
            TOPKDV              *360,06
            TOPLAM              *2.160,36
        """.trimIndent()
        assertEquals(ReceiptGuess("45,30", "2160,36", "2026-11-01"), parseReceipt(text, today))
    }

    @Test
    fun receiptWithMiktarAndTutar() {
        val text = "SHELL\n02.11.2026 09:10\nMİKTAR : 38.250\nBİRİM FİYAT 48.10\nTUTAR\n1.839,83 TL"
        assertEquals(ReceiptGuess("38,25", "1839,83", "2026-11-02"), parseReceipt(text, today))
    }

    @Test
    fun receiptTotalFromUnitPriceWhenNoTotalLine() {
        val g = parseReceipt("40,00 LT X 50,00", today)
        assertEquals("40,00", g.liters)
        assertEquals("2000,00", g.price)
        assertNull(g.date)
    }

    @Test
    fun absurdTotalIsDropped() {
        // 2 TL for 40 litres can only be a misread.
        assertNull(parseReceipt("40,00 LT\nTOPLAM 2,00\n15.03.2020", today).price)
        assertNull(parseReceipt("40,00 LT\nTOPLAM 2,00\n15.03.2020", today).date)
    }

    @Test
    fun mapsLinks() {
        assertEquals(41.0256 to 28.9742, coordinatesIn("https://www.google.com/maps/place/Galata+Kulesi/@41.0255,28.9741,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d41.0256!4d28.9742"))
        assertEquals(41.0255 to 28.9741, coordinatesIn("https://www.google.com/maps/@41.0255,28.9741,17z"))
        assertEquals(41.0 to 29.0, coordinatesIn("https://maps.google.com/?q=41.0,29.0"))
        assertEquals(41.0 to 29.0, coordinatesIn("geo:41.0,29.0?q=41.0,29.0(Ev)"))
        assertEquals(41.0 to 29.0, coordinatesIn("https://waze.com/ul?ll=41.0%2C29.0&navigate=yes"))
        assertEquals(41.0 to 29.0, coordinatesIn("https://yandex.com.tr/maps/?ll=29.0%2C41.0&z=12"))
        assertNull(coordinatesIn("https://maps.app.goo.gl/AbC123"))
        assertEquals("Galata Kulesi", nameIn("https://www.google.com/maps/place/Galata+Kulesi/@41,28"))
        assertTrue(isShortMapsLink("https://maps.app.goo.gl/AbC123"))
        assertFalse(isShortMapsLink("https://www.google.com/maps/@41,29"))
    }

    @Test
    fun sharedTextBecomesAPlace() {
        val text = "Galata Kulesi\nBereketzade, 34421 Beyoğlu/İstanbul\nhttps://maps.app.goo.gl/AbC123"
        assertEquals(SharedPlace(41.0256, 28.9742, "Galata Kulesi, Bereketzade, 34421 Beyoğlu/İstanbul"),
            sharedPlace(text, "https://www.google.com/maps/place/Galata+Kulesi/data=!3d41.0256!4d28.9742"))
        assertEquals(SharedPlace(null, null, "Galata Kulesi"), sharedPlace("https://maps.app.goo.gl/AbC123", "https://www.google.com/maps/place/Galata+Kulesi/"))
        assertEquals(SharedPlace(null, null, "Kadıköy iskele"), sharedPlace("Kadıköy iskele", null))
        assertNull(sharedPlace("https://maps.app.goo.gl/AbC123", null))
    }

    @Test
    fun deviceKeyTravelsOnlyBetweenPhoneAndUnit() {
        val key = "ab".repeat(32)
        val head = "GET /api/state?t=x HTTP/1.1\r\nHost: 127.0.0.1:5\r\nX-Kokpit-Device-Key: forged\r\n\r\n".toByteArray()
        val sent = String(withDeviceKey(head, key), Charsets.ISO_8859_1)
        assertEquals("GET /api/state?t=x HTTP/1.1\r\nHost: 127.0.0.1:5\r\nX-Kokpit-Device-Key: $key\r\n\r\n", sent)
        assertFalse(String(withDeviceKey(head, null), Charsets.ISO_8859_1).contains("Device-Key"))

        val (clean, got) = takeDeviceKey("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nx-kokpit-device-key: $key\r\nConnection: close\r\n\r\n")
        assertEquals(key, got)
        assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n", clean)
        assertNull(takeDeviceKey("HTTP/1.1 200 OK\r\nx-kokpit-device-key: short\r\n\r\n").second)
    }

    @Test
    fun parkOnlyAfterADrive() {
        assertTrue(wasDriving(1_000, 1_000 + MIN_DRIVE_MS))
        assertFalse(wasDriving(1_000, 1_000 + MIN_DRIVE_MS - 1))
        assertFalse(wasDriving(0, 10_000_000))
        assertEquals("https://maps.google.com/?q=41.000000,29.000000", parkLink(41.0, 29.0))
    }
}
