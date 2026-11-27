package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** One reminder for one occurrence of a PLAN event. [eventId] is the series id; [day] is the occurrence's start day. */
data class DueEventReminder(val eventId: String, val title: String, val atMillis: Long, val day: LocalDate)

/**
 * Reminders for PLAN events (PLAN 8.2): an event may ask to be remembered some minutes before it starts, and every occurrence of
 * a repeating event does. Quiet hours move a reminder to when they end, and one that would then fall after the start is dropped
 * (a reminder for something already begun helps nobody). An all-day event counts from 09:00.
 */
object EventReminders {
    const val HORIZON_DAYS = 14
    val ALL_DAY_TIME = LocalTime(9, 0)

    val PRESETS: List<Int> = listOf(0, 10, 30, 60, 24 * 60)

    fun label(minutes: Int): String = when {
        minutes == 0 -> "At the start"
        minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} day${if (minutes == 24 * 60) "" else "s"} before"
        minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes == 60) "" else "s"} before"
        else -> "$minutes min before"
    }

    fun startOf(e: PlanEvent, day: LocalDate): LocalDateTime = LocalDateTime(day, if (e.allDay) ALL_DAY_TIME else e.start)

    fun upcoming(
        events: List<PlanEvent>, nowMillis: Long, zone: TimeZone, quiet: QuietHours = QuietHours(), restDays: Set<LocalDate> = emptySet(),
    ): List<DueEventReminder> {
        val today = nowMillis.toLocalDateTime(zone).date
        return events.asSequence().filter { it.reminderMinutes != null }.flatMap { series ->
            series.startDates(today.plus(DatePeriod(days = -1)), today.plus(DatePeriod(days = HORIZON_DAYS))).asSequence().mapNotNull { day ->
                val start = startOf(series, day)
                val ahead = start.toEpochMillis(zone) - series.reminderMinutes!! * 60_000L
                val at = Reminders.outOfQuietHours(ahead.toLocalDateTime(zone), quiet)
                if (at > start || at.date in restDays) null
                else DueEventReminder(series.id, series.title, at.toEpochMillis(zone), day).takeIf { it.atMillis > nowMillis }
            }
        }.sortedWith(compareBy({ it.atMillis }, { it.eventId })).toList()
    }

    /** Whether the reminder for [day] of [eventId] still stands: the event exists, asks for a reminder and still happens that day. */
    fun stillWanted(events: List<PlanEvent>, eventId: String, day: LocalDate): Boolean {
        val e = events.firstOrNull { it.id == eventId } ?: return false
        return e.reminderMinutes != null && day in e.startDates(day, day)
    }
}
