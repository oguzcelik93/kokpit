package io.github.yorulmazsinan.kokpit.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensTest {
    @Test
    fun minTouchTargetIsAtLeast64dp() {
        assertTrue(Dimens.MinTouch >= 64.dp)
    }

    @Test
    fun noTextStyleIsSmallerThan14sp() {
        val t = KokpitTypography
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        )
        styles.forEach { assertTrue("${it.fontSize} < 14sp", it.fontSize.value >= KokpitType.minBody.value) }
        assertTrue(KokpitType.minBody >= 14.sp)
    }
}
