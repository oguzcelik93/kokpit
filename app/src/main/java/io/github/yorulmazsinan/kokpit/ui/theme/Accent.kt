package io.github.yorulmazsinan.kokpit.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.yorulmazsinan.kokpit.R

/**
 * Dark-theme colour sets. SAAB_93 is the default: the Saab 9-3's green instrument
 * lighting with orange needles. GAUGE_GREEN matches the Captiva's dashboard clock.
 */
enum class ThemeAccent(@StringRes val label: Int) {
    SAAB_93(R.string.accent_saab_93),
    GAUGE_GREEN(R.string.accent_gauge_green),
    CHROME(R.string.accent_chrome),
    NIGHT_BLUE(R.string.accent_night_blue),
    TEAL(R.string.accent_teal),
}

data class AccentPalette(
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Big numbers: clock and speed. */
    val highlight: Color,
    val weatherIcon: Color,
)

fun ThemeAccent.palette(): AccentPalette = when (this) {
    ThemeAccent.SAAB_93 -> AccentPalette(
        background = SaabColors.Black, card = SaabColors.Panel, cardBorder = SaabColors.Bezel,
        accent = SaabColors.Green, accentContainer = SaabColors.GreenDeep, onAccentContainer = SaabColors.GreenPale,
        textPrimary = SaabColors.GreenPale, textSecondary = SaabColors.GreenDim,
        highlight = SaabColors.GreenBright, weatherIcon = SaabColors.Needle,
    )
    ThemeAccent.GAUGE_GREEN -> AccentPalette(
        background = Color(0xFF0F110D), card = Color(0xFF1A1F15), cardBorder = Color(0xFF2C3A1E),
        accent = Color(0xFFA6E05A), accentContainer = Color(0xFF3E5A1E), onAccentContainer = Color(0xFFEAF7D6),
        textPrimary = Color(0xFFEEF3E6), textSecondary = Color(0xFFAEB8A1),
        highlight = Color(0xFFA6E05A), weatherIcon = Color(0xFFA6E05A),
    )
    ThemeAccent.CHROME -> AccentPalette(
        background = Color(0xFF101011), card = Color(0xFF1D1D1F), cardBorder = Color(0xFF3A3A3D),
        accent = Color(0xFFE8E8EC), accentContainer = Color(0xFFEDEDF0), onAccentContainer = Color(0xFF1D1D1F),
        textPrimary = Color(0xFFF4F4F2), textSecondary = Color(0xFFB8B8BC),
        highlight = Color(0xFFF4F4F2), weatherIcon = Color(0xFFD9D9DE),
    )
    ThemeAccent.NIGHT_BLUE -> AccentPalette(
        background = Color(0xFF0D1015), card = Color(0xFF161C25), cardBorder = Color(0xFF233246),
        accent = Color(0xFF6CB4F5), accentContainer = Color(0xFF14406B), onAccentContainer = Color(0xFFDCEBFB),
        textPrimary = Color(0xFFECF1F8), textSecondary = Color(0xFFA7B2C2),
        highlight = Color(0xFFECF1F8), weatherIcon = Color(0xFF6CB4F5),
    )
    ThemeAccent.TEAL -> AccentPalette(
        background = KokpitColors.Background, card = KokpitColors.Card, cardBorder = KokpitColors.Card,
        accent = KokpitColors.Accent, accentContainer = KokpitColors.AccentContainer, onAccentContainer = KokpitColors.OnAccentContainer,
        textPrimary = KokpitColors.TextPrimary, textSecondary = KokpitColors.TextSecondary,
        highlight = KokpitColors.TextPrimary, weatherIcon = KokpitColors.Warm,
    )
}
