package io.github.yorulmazsinan.kokpit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Saab 9-3 instrument panel: black faces, green back-lighting, orange-red needles and the
 * green SID (Saab Information Display). Colours picked to stay readable at night without
 * glare; the bright green is used only for numbers and needles' counterweights.
 */
object SaabColors {
    val Black = Color(0xFF030403)
    val Panel = Color(0xFF080A07)
    val Bezel = Color(0xFF1C2716)
    val Green = Color(0xFF8DD443)
    val GreenBright = Color(0xFFA9EC62)
    val GreenDim = Color(0xFF6E9152)
    val GreenDeep = Color(0xFF203512)
    val GreenPale = Color(0xFFD8EEC4)
    val Needle = Color(0xFFFF6A13)
    val NeedleGlow = Color(0x55FF6A13)
}

/** Style hints beyond Material colours: card shape and whether to draw analogue Saab dials. */
@Immutable
data class CockpitStyle(
    val saab: Boolean,
    val cardRadius: Dp,
    val needle: Color,
)

val DefaultCockpitStyle = CockpitStyle(saab = false, cardRadius = Dimens.CardRadius, needle = SaabColors.Needle)
val SaabCockpitStyle = CockpitStyle(saab = true, cardRadius = 4.dp, needle = SaabColors.Needle)

val LocalCockpitStyle = staticCompositionLocalOf { DefaultCockpitStyle }

fun cockpitStyleFor(dark: Boolean, accent: ThemeAccent): CockpitStyle =
    if (dark && accent == ThemeAccent.SAAB_93) SaabCockpitStyle else DefaultCockpitStyle
