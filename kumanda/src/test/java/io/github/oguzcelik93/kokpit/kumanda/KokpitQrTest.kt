package io.github.oguzcelik93.kokpit.kumanda

import org.junit.Assert.assertEquals
import org.junit.Test

class KokpitQrTest {
    private val t = "0123456789abcdef0123456789abcdef"

    @Test
    fun readsBothQrForms() {
        assertEquals(QrResult.Panel(t, "Tiguan Ünite"), parsePanelQr("http://192.168.43.5:4321/?t=$t&bt=Tiguan+%C3%9Cnite"))
        assertEquals(QrResult.Panel(t, "XY"), parsePanelQr("kokpit://panel?t=$t&bt=XY"))
        assertEquals(QrResult.Panel(t, null), parsePanelQr("kokpit://panel?t=$t&bt="))
    }

    @Test
    fun wifiOnlyQrSaysSo() {
        assertEquals(QrResult.NoBluetooth, parsePanelQr("http://192.168.43.5:4321/?t=$t"))
    }

    @Test
    fun othersAreRefused() {
        assertEquals(QrResult.NotKokpit, parsePanelQr("https://example.com/?t=$t&bt=x"))
        assertEquals(QrResult.NotKokpit, parsePanelQr("http://192.168.43.5:4321/photo/1.jpg?t=$t&bt=x"))
        assertEquals(QrResult.NotKokpit, parsePanelQr("kokpit://panel?t=XYZ&bt=x"))
        assertEquals(QrResult.NotKokpit, parsePanelQr("WIFI:T:WPA;S:Kokpit;P:secret;;"))
        assertEquals(QrResult.NotKokpit, parsePanelQr(""))
    }

    @Test
    fun unitIsTriedByNameThenLastThenAll() {
        val buds = Paired("00:00:00:00:00:01", "Kulaklık")
        val unit = Paired("00:00:00:00:00:02", "Tiguan")
        val watch = Paired("00:00:00:00:00:03", "Saat")
        val paired = listOf(buds, unit, watch)
        assertEquals(listOf(unit), tryOrder(paired, "Tiguan", null))
        assertEquals(listOf(unit, watch), tryOrder(paired, "Tiguan", watch.address))
        assertEquals(listOf(watch, buds, unit), tryOrder(paired, "Yeni ad", watch.address))
        assertEquals(paired, tryOrder(paired, null, null))
    }
}
