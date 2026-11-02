package io.github.yorulmazsinan.kokpit.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingTest {
    @Test
    fun defaultFillsAndCentres() {
        // Portrait 720×1280 photo in a 300×470 card: width-limited, centred vertically.
        val r = drawRect(300f, 470f, 720f, 1280f, Framing())
        assertEquals(300f, r.width, 0.01f)
        assertEquals(0f, r.left, 0.01f)
        assertTrue(r.height >= 470f)
        assertEquals((470f - r.height) / 2, r.top, 0.01f)
    }

    @Test
    fun edgesShowTheEdges() {
        val top = drawRect(300f, 470f, 720f, 1280f, Framing(fy = -1f))
        assertEquals(0f, top.top, 0.01f)
        val bottom = drawRect(300f, 470f, 720f, 1280f, Framing(fy = 1f))
        assertEquals(470f, bottom.top + bottom.height, 0.01f)
    }

    @Test
    fun gestureNeverUncoversTheCard() {
        var f = Framing()
        f = f.gesture(10f, 0f, 0f, 300f, 470f, 720f, 1280f)
        assertEquals(FRAMING_MAX_SCALE, f.scale, 0f)
        f = f.gesture(1f, 100_000f, -100_000f, 300f, 470f, 720f, 1280f)
        assertEquals(-1f, f.fx, 0f)
        assertEquals(1f, f.fy, 0f)
        val r = drawRect(300f, 470f, 720f, 1280f, f)
        assertTrue(r.left <= 0f && r.top <= 0f && r.left + r.width >= 300f && r.top + r.height >= 470f)
        assertEquals(1f, f.gesture(0.01f, 0f, 0f, 300f, 470f, 720f, 1280f).scale, 0f)
    }

    @Test
    fun storedForm() {
        assertEquals(Framing(1.5f, -0.25f, 0.5f), Framing.decode(Framing(1.5f, -0.25f, 0.5f).encode()))
        assertEquals(Framing(FRAMING_MAX_SCALE, 1f, -1f), Framing.decode("9,5,-5"))
        assertNull(Framing.decode("x"))
        assertNull(Framing.decode(null))
    }
}
