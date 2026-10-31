package io.github.yorulmazsinan.kokpit.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaEventLogTest {
    @Test
    fun keepsOnlyTheNewestEntries() {
        val log = MediaEventLog(capacity = 3)
        (1..5).forEach { log.add(it * 1000L, "event $it") }
        assertEquals(listOf("event 3", "event 4", "event 5"), log.lines().map { it.substringAfter(" ") })
    }

    @Test
    fun linesCarryATimestamp() {
        val log = MediaEventLog()
        log.add(3_723_000L, "com.ts.MainUI PLAYING")
        assertTrue(log.lines().single().startsWith("01:02:03"))
    }
}
