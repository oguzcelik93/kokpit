package io.github.yorulmazsinan.kokpit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object KokpitType {
    val minBody = 14.sp
    val clock = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Normal, lineHeight = 64.sp, letterSpacing = (-1).sp)
}

private fun style(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.3f).sp)

val KokpitTypography = Typography(
    displayLarge = style(64),
    displayMedium = style(48),
    displaySmall = style(36),
    headlineLarge = style(30),
    headlineMedium = style(26),
    headlineSmall = style(22),
    titleLarge = style(22, FontWeight.Medium),
    titleMedium = style(18, FontWeight.Medium),
    titleSmall = style(16, FontWeight.Medium),
    bodyLarge = style(18),
    bodyMedium = style(16),
    bodySmall = style(14),
    labelLarge = style(16, FontWeight.Medium),
    labelMedium = style(14, FontWeight.Medium),
    labelSmall = style(14),
)
