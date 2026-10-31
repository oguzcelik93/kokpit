package io.github.yorulmazsinan.kokpit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {
    @Test
    fun gaugeGreenMatchesTheDashboardClock() {
        assertEquals(Color(0xFFA6E05A), ThemeAccent.GAUGE_GREEN.palette().accent)
    }

    @Test
    fun everyPaletteKeepsTextReadableOnItsCards() {
        ThemeAccent.entries.forEach { accent ->
            val p = accent.palette()
            assertTrue("$accent text vs card", contrast(p.textPrimary, p.card) >= 7.0)
            assertTrue("$accent accent vs card", contrast(p.accent, p.card) >= 4.5)
            assertTrue("$accent CarPlay label", contrast(p.onAccentContainer, p.accentContainer) >= 4.5)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
