package io.github.yorulmazsinan.kokpit.calendar

data class EventInstance(val title: String, val beginMs: Long, val endMs: Long, val allDay: Boolean)

data class UpcomingEvent(val title: String, val beginMs: Long)

/** Today's next timed event: an ongoing one first, otherwise the earliest that hasn't started. */
fun pickNextEvent(instances: List<EventInstance>, nowMs: Long, endOfDayMs: Long): UpcomingEvent? =
    instances
        .filter { !it.allDay && it.endMs > nowMs && it.beginMs < endOfDayMs }
        .minByOrNull { it.beginMs }
        ?.let { UpcomingEvent(it.title, it.beginMs) }
