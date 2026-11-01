package io.github.oguzcelik93.kokpit.kumanda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class HttpTest {
    @Test
    fun headsAndLengths() {
        val input = ByteArrayInputStream("POST /api/fuel HTTP/1.1\r\nHost: 127.0.0.1:5555\r\ncontent-length: 5\r\n\r\nhello".toByteArray())
        val head = readHttpHead(input)!!
        assertEquals("127.0.0.1:5555", httpHeader(head, "Host"))
        assertEquals(5L, contentLength(head))
        val out = ByteArrayOutputStream()
        assertTrue(copyExactly(input, out, 5))
        assertEquals("hello", out.toString())
        assertEquals(0L, contentLength("GET / HTTP/1.1\r\n\r\n"))
        assertNull(contentLength("POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n"))
        assertNull(readHttpHead(ByteArrayInputStream(("GET / HTTP/1.1\r\nX: " + "a".repeat(MAX_HEAD)).toByteArray())))
        assertFalse(copyExactly(ByteArrayInputStream(ByteArray(3)), ByteArrayOutputStream(), 4))
    }

    @Test
    fun goneClientDoesNotStopTheRead() {
        val broken = object : OutputStream() { override fun write(b: Int) = throw IOException("gone") }
        val sink = ForgivingOutput(broken)
        assertTrue(copyExactly(ByteArrayInputStream(ByteArray(40_000)), sink, 40_000))
        assertTrue(sink.failed)
    }
}
