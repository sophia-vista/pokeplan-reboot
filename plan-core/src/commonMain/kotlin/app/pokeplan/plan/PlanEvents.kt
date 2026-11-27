package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable

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
    val repeat: EventRepeat? = null,
    val until: LocalDate? = null,
    val skipped: Set<LocalDate> = emptySet(),
    val companionId: String? = null,
    val companionSince: Long = 0L,
    val paid: Set<LocalDate> = emptySet(),
    /** Where and what, in the player's words; both sync to the calendar with the event (PLAN 8.2). */
    val description: String = "",
    val location: String = "",
    /** Minutes before the start to remind, or null for no reminder. PLAN's own: it is never written to a phone calendar. */
    val reminderMinutes: Int? = null,
) {
    init {
        require(title.isNotBlank()) { "an event needs a title" }
        require(reminderMinutes == null || reminderMinutes in 0..MAX_REMINDER_MINUTES) { "a reminder is up to a week ahead" }
        require(minutes > 0) { "an event lasts at least a minute" }
        require(lastDay == null || lastDay >= day) { "an event ends on or after the day it starts" }
    }

    val endDay: LocalDate get() = if (allDay) lastDay ?: day else day

    val spanDays: Int get() = day.daysUntil(endDay)

    fun isOn(date: LocalDate): Boolean = occurrences(date, date).isNotEmpty()

    val recurrence: Recurrence? get() = repeat?.let {
        Recurrence(it.rule(day), day, end = until?.let { u -> RepeatEnd.On(u) } ?: RepeatEnd.Never, skipped = skipped)
    }

    fun startDates(from: LocalDate, to: LocalDate): List<LocalDate> {
        val r = recurrence ?: return if (day <= to && endDay >= from) listOf(day) else emptyList()
        return r.occurrences(from.plus(DatePeriod(days = -spanDays)), to)
    }

    fun occurrences(from: LocalDate, to: LocalDate): List<PlanEvent> {
        if (repeat == null) return startDates(from, to).map { this }
        return startDates(from, to).map { d ->
            copy(id = occurrenceId(id, d), day = d, lastDay = lastDay?.let { d.plus(DatePeriod(days = spanDays)) }, skipped = emptySet())
        }
    }

    val startMinute: Int get() = start.hour * 60 + start.minute

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
        const val MAX_REMINDER_MINUTES = 7 * 24 * 60
        const val MAX_TEXT = 4_000

        const val OWN_CALENDAR = -1L

        private const val SEPARATOR = '@'

        fun occurrenceId(seriesId: String, date: LocalDate): String = "$seriesId$SEPARATOR$date"

        fun seriesOf(id: String): String = id.substringBefore(SEPARATOR)

        fun occurrenceDate(id: String): LocalDate? =
            id.substringAfter(SEPARATOR, "").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        fun fit(startMinute: Int, minutes: Int): Int =
            minutes.coerceIn(DayTimeline.SNAP_MINUTES, (CalendarEvent.DAY_MINUTES - startMinute).coerceAtLeast(DayTimeline.SNAP_MINUTES))
    }
}

@Serializable
enum class EventRepeat(val label: String) {
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Weekly"),
    FORTNIGHTLY("Every 2 weeks"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    fun rule(first: LocalDate): Repeat = when (this) {
        DAILY -> Repeat.Daily()
        WEEKDAYS -> Repeat.Weekly(days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
        WEEKLY -> Repeat.Weekly()
        FORTNIGHTLY -> Repeat.Weekly(every = 2)
        MONTHLY -> Repeat.Monthly(rule = MonthlyRule.DayOfMonth(first.day))
        YEARLY -> Repeat.Yearly()
    }
}

val CalendarEvent.isOwn: Boolean get() = calendarId == PlanEvent.OWN_CALENDAR

/** An entry an integration adds (its calendar id is below PLAN's own): read-only here, and the integration decides what a tap opens. */
val CalendarEvent.isOverlay: Boolean get() = calendarId < PlanEvent.OWN_CALENDAR

/** Whether a tap on this entry does anything: PLAN's own opens the editor, an integration's opens what it points at, a phone calendar's opens its details. */
val CalendarEvent.isOpenable: Boolean get() = isOwn || isOverlay || isDevice

/** An event read from a phone calendar: its details open, with the player's own additions, and it is editable only in the synced calendar (PLAN 8.1B). */
val CalendarEvent.isDevice: Boolean get() = calendarId >= 0
