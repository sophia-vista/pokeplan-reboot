package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

/**
 * An event the player puts on PokéPlan's own calendar: an appointment or a block of time, on [day] from [start] for
 * [minutes]. Unlike a task it is never ticked off and never pays. It is saved and synced with the rest of the save, sits
 * on the timeline beside the phone's events, can be dragged and resized like a task, and the time it takes counts as
 * busy in the day load. It never runs past midnight.
 *
 * An [allDay] event has no hours: it covers [day] through [lastDay] (the same day when null), like "Home" for a weekend
 * or "WFH" for a day, sits in the all-day band and the month as a bar across its days, and is never busy time. [color]
 * is one of [TaskList.COLORS], or null for the own-events teal.
 */
@Serializable
data class PlanEvent(
    val id: String,
    val title: String,
    val day: LocalDate,
    val start: LocalTime,
    val minutes: Int = DEFAULT_MINUTES,
    val allDay: Boolean = false,
    val lastDay: LocalDate? = null,
    val color: Long? = null,
) {
    init {
        require(title.isNotBlank()) { "an event needs a title" }
        require(minutes > 0) { "an event lasts at least a minute" }
        require(lastDay == null || lastDay >= day) { "an event ends on or after the day it starts" }
    }

    /** The last day an all-day event covers (its [day] for a timed one). */
    val endDay: LocalDate get() = if (allDay) lastDay ?: day else day

    /** Whether the event is on [date] at all. */
    fun isOn(date: LocalDate): Boolean = date >= day && date <= endDay

    /** The minute after midnight it starts. */
    val startMinute: Int get() = start.hour * 60 + start.minute

    /** As an event on the timeline, beside the phone's events. */
    fun toCalendarEvent(): CalendarEvent {
        if (allDay) {
            return CalendarEvent(id, OWN_CALENDAR, title, LocalDateTime(day, LocalTime(0, 0)), LocalDateTime(endDay.plus(DatePeriod(days = 1)), LocalTime(0, 0)), allDay = true, color = color)
        }
        val end = (startMinute + minutes).coerceAtMost(CalendarEvent.DAY_MINUTES)
        val endTime = if (end >= CalendarEvent.DAY_MINUTES) LocalDateTime(day.plus(DatePeriod(days = 1)), LocalTime(0, 0))
        else LocalDateTime(day, LocalTime(end / 60, end % 60))
        return CalendarEvent(id, OWN_CALENDAR, title, LocalDateTime(day, start), endTime, color = color)
    }

    companion object {
        const val DEFAULT_MINUTES = 60

        /** The calendar id PokéPlan's own events carry on the timeline (device calendar ids are never negative). */
        const val OWN_CALENDAR = -1L

        /** Keeps [minutes] inside the day for an event starting at [startMinute]: at least 15, never past midnight. */
        fun fit(startMinute: Int, minutes: Int): Int =
            minutes.coerceIn(DayTimeline.SNAP_MINUTES, (CalendarEvent.DAY_MINUTES - startMinute).coerceAtLeast(DayTimeline.SNAP_MINUTES))
    }
}

/** Whether this is one of PokéPlan's own events rather than one read from the phone. */
val CalendarEvent.isOwn: Boolean get() = calendarId == PlanEvent.OWN_CALENDAR
