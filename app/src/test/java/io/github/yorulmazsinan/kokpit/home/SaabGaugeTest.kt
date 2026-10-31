package io.github.yorulmazsinan.kokpit.home

import io.github.yorulmazsinan.kokpit.home.cards.SAAB_MAX_KMH
import io.github.yorulmazsinan.kokpit.home.cards.saabNeedleAngle
import io.github.yorulmazsinan.kokpit.ui.theme.ThemeAccent
import io.github.yorulmazsinan.kokpit.ui.theme.cockpitStyleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaabGaugeTest {
    @Test
    fun needleSweepsFromBottomLeftOverTheTop() {
        assertEquals(150f, saabNeedleAngle(0f), 0.01f)
        assertEquals(270f, saabNeedleAngle(SAAB_MAX_KMH / 2), 0.01f) // 120 km/h straight up
        assertEquals(390f, saabNeedleAngle(SAAB_MAX_KMH), 0.01f)
        assertEquals(390f, saabNeedleAngle(400f), 0.01f)
        assertEquals(150f, saabNeedleAngle(-5f), 0.01f)
    }

    @Test
    fun saabDialsOnlyInDarkSaabTheme() {
        assertTrue(cockpitStyleFor(dark = true, accent = ThemeAccent.SAAB_93).saab)
        assertFalse(cockpitStyleFor(dark = false, accent = ThemeAccent.SAAB_93).saab)
        assertFalse(cockpitStyleFor(dark = true, accent = ThemeAccent.GAUGE_GREEN).saab)
    }
}
