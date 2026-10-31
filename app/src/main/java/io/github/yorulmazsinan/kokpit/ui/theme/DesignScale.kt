package io.github.yorulmazsinan.kokpit.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private const val DESIGN_WIDTH_DP = 1024f
private const val DESIGN_HEIGHT_DP = 600f

/** Text reads a touch larger than the layout grid at arm's length in a car. */
const val TEXT_BOOST = 1.06f

/** How much to shrink so the 1024×600 dp design fits; never enlarges. */
fun designScale(widthDp: Float, heightDp: Float): Float =
    minOf(widthDp / DESIGN_WIDTH_DP, heightDp / DESIGN_HEIGHT_DP, 1f)

/**
 * Head units often report a high density for a 1024×600 px panel (e.g. 240 dpi), leaving
 * only ~680×360 dp. Scale density so every screen keeps the proportions it was designed with.
 */
@Composable
fun DesignScale(textBoost: Float = TEXT_BOOST, content: @Composable () -> Unit) {
    BoxWithConstraints {
        val base = LocalDensity.current
        val scale = designScale(maxWidth.value, maxHeight.value)
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale * textBoost), content = content)
    }
}
