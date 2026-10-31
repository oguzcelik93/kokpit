package io.github.yorulmazsinan.kokpit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun darkScheme(p: AccentPalette) = darkColorScheme(
    primary = p.accent,
    onPrimary = p.background,
    primaryContainer = p.accentContainer,
    onPrimaryContainer = p.onAccentContainer,
    secondaryContainer = p.accentContainer,
    onSecondaryContainer = p.onAccentContainer,
    secondary = p.highlight,
    tertiary = p.weatherIcon,
    background = p.background,
    onBackground = p.textPrimary,
    surface = p.card,
    onSurface = p.textPrimary,
    surfaceVariant = p.card,
    onSurfaceVariant = p.textSecondary,
    outline = KokpitColors.CardBorder,
    outlineVariant = p.cardBorder,
    // Hairlines and the speedometer's empty track.
    surfaceContainerHighest = p.cardBorder,
)

private val LightScheme = lightColorScheme(
    primary = KokpitColors.LightAccent,
    onPrimary = KokpitColors.LightCard,
    primaryContainer = KokpitColors.LightAccentContainer,
    onPrimaryContainer = KokpitColors.LightOnAccentContainer,
    secondaryContainer = KokpitColors.LightAccentContainer,
    onSecondaryContainer = KokpitColors.LightOnAccentContainer,
    background = KokpitColors.LightBackground,
    onBackground = KokpitColors.LightTextPrimary,
    surface = KokpitColors.LightCard,
    onSurface = KokpitColors.LightTextPrimary,
    surfaceVariant = KokpitColors.LightCard,
    onSurfaceVariant = KokpitColors.LightTextSecondary,
    secondary = KokpitColors.LightTextPrimary,
    tertiary = KokpitColors.LightAccent,
    outlineVariant = KokpitColors.LightCard,
    surfaceContainerHighest = KokpitColors.LightHairline,
)

@Composable
fun KokpitTheme(dark: Boolean = true, accent: ThemeAccent = ThemeAccent.SAAB_93, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCockpitStyle provides cockpitStyleFor(dark, accent)) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(accent.palette()) else LightScheme,
            typography = KokpitTypography,
            content = content,
        )
    }
}
