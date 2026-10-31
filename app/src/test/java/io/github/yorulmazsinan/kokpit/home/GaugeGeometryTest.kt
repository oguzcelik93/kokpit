package io.github.yorulmazsinan.kokpit.home

import io.github.yorulmazsinan.kokpit.home.cards.gaugeGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GaugeGeometryTest {
    @Test
    fun visibleArcIsVerticallyCentred() {
        val g = gaugeGeometry(width = 400f, height = 180f, stroke = 12f)
        val top = g.centerY - g.radius - 6f
        val bottom = 180f - (g.centerY + g.radius * 0.5f + 6f)
        assertEquals(top, bottom, 0.5f)
        assertEquals(200f, g.centerX, 0.01f)
    }

    @Test
    fun arcFitsInsideTheBox() {
        val g = gaugeGeometry(width = 150f, height = 400f, stroke = 10f)
        assertTrue(g.centerX - g.radius - 5f >= -0.01f)
        assertTrue(g.centerX + g.radius + 5f <= 150.01f)
    }
}
