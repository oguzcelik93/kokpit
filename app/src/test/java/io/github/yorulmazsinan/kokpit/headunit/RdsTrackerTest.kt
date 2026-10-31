package io.github.yorulmazsinan.kokpit.headunit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RdsTrackerTest {
    @Test
    fun keepsTheNameThroughABriefRdsDropout() {
        val rds = RdsTracker()
        assertEquals("KRAL FM", rds.update(984, "KRAL FM"))
        assertEquals("KRAL FM", rds.update(984, null))
    }

    @Test
    fun ignoresThePreviousStationsNameAfterRetuning() {
        val rds = RdsTracker()
        rds.update(984, "KRAL FM")
        assertNull(rds.update(914, "KRAL FM"))
        assertNull(rds.update(915, "KRAL FM"))
        assertEquals("TRT FM", rds.update(914, "TRT FM"))
    }

    @Test
    fun acceptsANameAlreadySeenOnThatFrequency() {
        val rds = RdsTracker()
        rds.update(984, "KRAL FM")
        rds.update(914, "KRAL FM")
        assertEquals("KRAL FM", rds.update(984, "KRAL FM"))
    }

    @Test
    fun keepsScrollingNamesChanging() {
        val rds = RdsTracker()
        rds.update(953, "POWER")
        assertEquals("FM", rds.update(953, "FM"))
    }
}
