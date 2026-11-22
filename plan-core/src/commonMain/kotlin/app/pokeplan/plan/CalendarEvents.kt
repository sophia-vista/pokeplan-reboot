package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Long? = null)

data class CalendarEvent(
    val id: String,
    val calendarId: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean = false,
    val private: Boolean = false,
    val color: Long? = null,
) {
    fun isOn(day: LocalDate): Boolean =
        if (allDay) day >= start.date && (day < end.date || (day == start.date && end.date <= start.date))
        else span(day) != null

    fun span(day: LocalDate): IntRange? {
        if (allDay) return null
        val from = if (start.date < day) 0 else if (start.date == day) start.minuteOfDay() else return null
        val to = if (end.date > day) DAY_MINUTES else if (end.date == day) end.minuteOfDay() else return null
        return if (to > from) from until to else if (to == from && start == end && start.date == day) from until from + 1 else null
    }

    val lastDay: LocalDate get() = if (allDay && end.date > start.date) end.date.minus(DatePeriod(days = 1)) else start.date

    val shownTitle: String get() = if (private) "Busy" else title

    private fun LocalDateTime.minuteOfDay() = hour * 60 + minute

    companion object {
        const val DAY_MINUTES = 24 * 60
    }
}
