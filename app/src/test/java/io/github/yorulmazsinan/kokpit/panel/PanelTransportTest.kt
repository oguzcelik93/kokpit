package io.github.yorulmazsinan.kokpit.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PanelTransportTest {
    private val token = "0123456789abcdef0123456789abcdef"

    @Test
    fun qrCarriesTokenAndDoors() {
        assertEquals("http://192.168.43.5:4321/?t=$token&bt=Tiguan+%C3%9Cnite", panelQr("192.168.43.5", 4321, token, "Tiguan Ünite", true))
        assertEquals("http://192.168.43.5:4321/?t=$token", panelQr("192.168.43.5", 4321, token, "Tiguan", false))
        assertEquals("kokpit://panel?t=$token&bt=", panelQr(null, null, token, null, true))
        assertNull(panelQr(null, null, token, null, false))
    }

    private fun serve(raw: ByteArray, tokenOk: Boolean = true): Triple<List<Pair<PanelRequest, ByteArray>>, String, Boolean> {
        val seen = mutableListOf<Pair<PanelRequest, ByteArray>>()
        val input = ByteArrayInputStream(raw)
        val out = ByteArrayOutputStream()
        var keep = true
        while (keep && input.available() > 0) {
            keep = serveOnePanelRequest(input, out, { tokenOk }) { r, b, o -> seen += r to b; writePanelReply(o, 200, "text/plain", "ok".toByteArray()) }
        }
        return Triple(seen, out.toString(Charsets.UTF_8), keep)
    }

    @Test
    fun oneLinkCarriesRequestsInTurn() {
        val raw = ("GET /api/state?t=x HTTP/1.1\r\n\r\n" +
            "POST /api/fuel?t=x HTTP/1.1\r\nContent-Length: 5\r\n\r\nhello" +
            "GET /api/trips?t=x HTTP/1.1\r\n\r\n").toByteArray()
        val (seen, out, _) = serve(raw)
        assertEquals(listOf("/api/state", "/api/fuel", "/api/trips"), seen.map { it.first.path })
        assertEquals("hello", seen[1].second.decodeToString())
        assertEquals(3, Regex("HTTP/1.1 200").findAll(out).count())
        assertTrue(out.contains("Content-Length: 2"))
    }

    @Test
    fun bodiesNeedTheToken() {
        val (seen, out, keep) = serve("POST /api/photo HTTP/1.1\r\nContent-Length: 4\r\n\r\nabcd".toByteArray(), tokenOk = false)
        assertTrue(seen.isEmpty())
        assertTrue(out.startsWith("HTTP/1.1 403"))
        assertFalse(keep)
    }

    @Test
    fun oversizedOrUnframedBodiesAreRefused() {
        val big = serve("POST /api/photo?t=x HTTP/1.1\r\nContent-Length: ${PANEL_MAX_BODY + 1}\r\n\r\n".toByteArray())
        assertTrue(big.first.isEmpty()); assertTrue(big.second.startsWith("HTTP/1.1 413")); assertFalse(big.third)
        val chunked = serve("POST /api/photo?t=x HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nabcd\r\n0\r\n\r\n".toByteArray())
        assertTrue(chunked.first.isEmpty()); assertTrue(chunked.second.startsWith("HTTP/1.1 411"))
        val bad = serve("POST /api/photo?t=x HTTP/1.1\r\nContent-Length: -3\r\n\r\n".toByteArray())
        assertTrue(bad.first.isEmpty()); assertFalse(bad.third)
    }

    @Test
    fun cutOffBodyClosesTheLink() {
        val (seen, _, keep) = serve("POST /api/fuel?t=x HTTP/1.1\r\nContent-Length: 50\r\n\r\nshort".toByteArray())
        assertTrue(seen.isEmpty())
        assertFalse(keep)
    }

    @Test
    fun headIsBounded() {
        assertNull(readPanelHead(ByteArrayInputStream(("GET / HTTP/1.1\r\nX: " + "a".repeat(PANEL_MAX_HEAD)).toByteArray())))
    }
}
