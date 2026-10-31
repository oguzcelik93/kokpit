package io.github.yorulmazsinan.kokpit.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

/** Android's built-in condensed faces: an automotive look without bundling font files. */
object KokpitFonts {
    val condensed: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    val condensedLight: FontFamily = FontFamily(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL))
}
