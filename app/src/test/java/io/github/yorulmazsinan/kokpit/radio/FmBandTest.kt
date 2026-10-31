package io.github.yorulmazsinan.kokpit.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class FmBandTest {
    @Test fun stepsByOneTenth() = assertEquals(985, FmBand.step(984, +1))
    @Test fun wrapsAtTheTop() = assertEquals(875, FmBand.step(1080, +1))
    @Test fun wrapsAtTheBottom() = assertEquals(1080, FmBand.step(875, -1))
    @Test fun formatsMegahertz() = assertEquals("98.4", FmBand.format(984))
    @Test fun positionAlongTheScale() = assertEquals(0.5f, FmBand.fraction(977), 0.01f)
}
