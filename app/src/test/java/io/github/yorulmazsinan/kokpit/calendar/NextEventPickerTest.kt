package io.github.yorulmazsinan.kokpit.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val H = 3_600_000L

class NextEventPickerTest {
    private val now = 15 * H
    private val endOfDay = 24 * H

    @Test
    fun skipsAllDayAndEnded() {
        val events = listOf(
            EventInstance("Holiday", 0, 24 * H, allDay = true),
            EventInstance("Lunch", 12 * H, 13 * H, allDay = false),
            EventInstance("Meeting", 16 * H, 17 * H, allDay = false),
        )
        assertEquals(UpcomingEvent("Meeting", 16 * H), pickNextEvent(events, now, endOfDay))
    }

    @Test
    fun prefersOngoingThenEarliest() {
        val events = listOf(
            EventInstance("Later", 18 * H, 19 * H, allDay = false),
            EventInstance("Ongoing", 14 * H, 16 * H, allDay = false),
            EventInstance("Soon", 16 * H, 17 * H, allDay = false),
        )
        assertEquals(UpcomingEvent("Ongoing", 14 * H), pickNextEvent(events, now, endOfDay))
        assertEquals(UpcomingEvent("Soon", 16 * H), pickNextEvent(events.filter { it.title != "Ongoing" }, now, endOfDay))
    }

    @Test
    fun nullWhenNothingLeftToday() {
        val events = listOf(
            EventInstance("Done", 9 * H, 10 * H, allDay = false),
            EventInstance("Tomorrow", 25 * H, 26 * H, allDay = false),
        )
        assertNull(pickNextEvent(events, now, endOfDay))
    }
}
