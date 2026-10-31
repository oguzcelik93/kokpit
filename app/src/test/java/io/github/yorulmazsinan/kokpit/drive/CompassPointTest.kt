package io.github.yorulmazsinan.kokpit.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassPointTest {
    @Test
    fun mapsBearingsToEightPoints() {
        mapOf(
            0f to CompassPoint.N, 44f to CompassPoint.NE, 46f to CompassPoint.NE, 90f to CompassPoint.E,
            337.6f to CompassPoint.N, 359.9f to CompassPoint.N, 202f to CompassPoint.S, 247.5f to CompassPoint.W,
            -10f to CompassPoint.N, 720f to CompassPoint.N,
        ).forEach { (deg, expected) -> assertEquals("bearing $deg", expected, CompassPoint.fromBearing(deg)) }
    }
}
