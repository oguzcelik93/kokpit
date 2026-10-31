package io.github.yorulmazsinan.kokpit.system

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedUpTest {
    @Test
    fun nothingDone() {
        val s = speedUpStatus(memoryFreed = false, bloatLeft = 5, animationScale = 1f, batteryExempt = false)
        assertEquals(4, s.remaining)
    }

    @Test
    fun allDone() {
        val s = speedUpStatus(memoryFreed = true, bloatLeft = 0, animationScale = 0.5f, batteryExempt = true)
        assertEquals(0, s.remaining)
        assertEquals(SpeedUpStep.entries.toSet(), s.done)
    }

    @Test
    fun animationsOffCountsAsDone() =
        assertEquals(setOf(SpeedUpStep.ANIMATIONS), speedUpStatus(false, 1, 0f, false).done)
}
