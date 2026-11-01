package io.github.yorulmazsinan.kokpit.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoReceiverTest {
    @Test
    fun requestLines() {
        assertEquals(Triple("POST", "/upload", "abc"), parseRequestLine("POST /upload?t=abc HTTP/1.1"))
        assertEquals(Triple("GET", "/", "abc"), parseRequestLine("GET /?x=1&t=abc HTTP/1.1"))
        assertEquals(Triple("GET", "/favicon.ico", null), parseRequestLine("GET /favicon.ico HTTP/1.1"))
        assertNull(parseRequestLine("garbage"))
    }

    @Test
    fun tokenMustMatchExactly() {
        val t = "0123456789abcdef0123456789abcdef"
        assertTrue(tokenMatches(t, t))
        assertFalse(tokenMatches(t, null))
        assertFalse(tokenMatches(t, ""))
        assertFalse(tokenMatches(t, t.dropLast(1)))
        assertFalse(tokenMatches(t, t.dropLast(1) + "0"))
    }

    @Test
    fun tokensAreLongAndRandom() {
        val a = PhotoReceiver { true }.token
        val b = PhotoReceiver { true }.token
        assertEquals(32, a.length)
        assertTrue(a != b)
    }

    @Test
    fun phonePhotosAreShrunk() {
        assertEquals(2, sampleSizeFor(4032, 3024, 1280, 720)) // 12 MP phone photo
        assertEquals(1, sampleSizeFor(1280, 720, 1280, 720))
        assertEquals(4, sampleSizeFor(8000, 6000, 1280, 720))
    }
}
