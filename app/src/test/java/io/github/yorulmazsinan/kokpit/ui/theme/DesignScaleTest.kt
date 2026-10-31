package io.github.yorulmazsinan.kokpit.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class DesignScaleTest {
    @Test
    fun shrinksToFitTheSmallerSide() {
        assertEquals(0.6f, designScale(widthDp = 683f, heightDp = 360f), 0.001f)
    }

    @Test
    fun neverEnlarges() {
        assertEquals(1f, designScale(widthDp = 1280f, heightDp = 720f), 0.001f)
    }

    @Test
    fun designSizeIsUnchanged() {
        assertEquals(1f, designScale(widthDp = 1024f, heightDp = 600f), 0.001f)
    }
}
