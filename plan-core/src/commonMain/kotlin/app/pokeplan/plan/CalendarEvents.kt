package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/** One of the phone's calendars (Google Calendar's among them, once its account syncs on the phone). */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Long? = null)

/**
 * A read-only event from a device calendar, one occurrence of it (recurring events come expanded). Events are read from
 * the phone each time they are shown: they are never saved, synced or paid. [start] and [end] are local wall-clock times;
 * an [allDay] event covers the days from [start]'s date up to (not including) [end]'s date.
 */
data class CalendarEvent(
    val id: String,
    val calendarId: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    /** Marked private in its calendar: widgets show it as "Busy". */
    val private: Boolean = false,
    val color: Long? = null,
) {
    /** Whether the event falls on [day] at all. */
    fun isOn(day: LocalDate): Boolean =
        if (allDay) day >= start.date && (day < end.date || (day == start.date && end.date <= start.date))
        else span(day) != null

    /** The minutes after midnight this timed event covers on [day], or null if none. All-day events have no span. */
    fun span(day: LocalDate): IntRange? {
        if (allDay) return null
        val from = if (start.date < day) 0 else if (start.date == day) start.minuteOfDay() else return null
        val to = if (end.date > day) DAY_MINUTES else if (end.date == day) end.minuteOfDay() else return null
        return if (to > from) from until to else if (to == from && start == end && start.date == day) from until from + 1 else null
    }

    /** The last day the event is on: an all-day event ends the morning after its last day; a timed one counts its first day. */
    val lastDay: LocalDate get() = if (allDay && end.date > start.date) end.date.minus(DatePeriod(days = 1)) else start.date

    /** How the event reads where privacy matters (widgets). */
    val shownTitle: String get() = if (private) "Busy" else title

    private fun LocalDateTime.minuteOfDay() = hour * 60 + minute

    companion object {
        const val DAY_MINUTES = 24 * 60
    }
}
