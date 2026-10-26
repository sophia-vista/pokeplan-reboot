package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
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
 *
 * An event can [repeat] ("Gym" every Monday, "Home" every weekend): its first one is on [day], the last on or before
 * [until] (never ending when null), and the dates in [skipped] are left out (deleted one by one). Each one shown is an
 * occurrence (see [occurrences]) with an id of its own, [occurrenceId], so it can be told apart on the calendar.
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
    val repeat: EventRepeat? = null,
    val until: LocalDate? = null,
    val skipped: Set<LocalDate> = emptySet(),
) {
    init {
        require(title.isNotBlank()) { "an event needs a title" }
        require(minutes > 0) { "an event lasts at least a minute" }
        require(lastDay == null || lastDay >= day) { "an event ends on or after the day it starts" }
    }

    /** The last day an all-day event covers (its [day] for a timed one). */
    val endDay: LocalDate get() = if (allDay) lastDay ?: day else day

    /** How many days after its first day an all-day event ends (0 for one day or a timed event). */
    val spanDays: Int get() = day.daysUntil(endDay)

    /** Whether the event (any of its occurrences, when it repeats) is on [date] at all. */
    fun isOn(date: LocalDate): Boolean = occurrences(date, date).isNotEmpty()

    /** The schedule a repeating event follows, from its first [day]. */
    val recurrence: Recurrence? get() = repeat?.let {
        Recurrence(it.rule(day), day, end = until?.let { u -> RepeatEnd.On(u) } ?: RepeatEnd.Never, skipped = skipped)
    }

    /** The dates its occurrences start on, from the first one that is still on [from] through [to]. */
    fun startDates(from: LocalDate, to: LocalDate): List<LocalDate> {
        val r = recurrence ?: return if (day <= to && endDay >= from) listOf(day) else emptyList()
        return r.occurrences(from.plus(DatePeriod(days = -spanDays)), to)
    }

    /**
     * The event as it is on the days [from] through [to]: itself when it does not repeat, else one copy per occurrence on
     * those days, each on its own day (an all-day one keeping its length) with an [occurrenceId]. A copy keeps the
     * series' [repeat] and [until] so it can say how it repeats; edit the series, never a copy.
     */
    fun occurrences(from: LocalDate, to: LocalDate): List<PlanEvent> {
        if (repeat == null) return startDates(from, to).map { this }
        return startDates(from, to).map { d ->
            copy(id = occurrenceId(id, d), day = d, lastDay = lastDay?.let { d.plus(DatePeriod(days = spanDays)) }, skipped = emptySet())
        }
    }

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

        private const val SEPARATOR = '@'

        /** The id of the occurrence of repeating event [seriesId] that starts on [date]. */
        fun occurrenceId(seriesId: String, date: LocalDate): String = "$seriesId$SEPARATOR$date"

        /** The event an id belongs to: the series of an occurrence, or the id itself. */
        fun seriesOf(id: String): String = id.substringBefore(SEPARATOR)

        /** The day an occurrence starts on, or null for an id that is not one. */
        fun occurrenceDate(id: String): LocalDate? =
            id.substringAfter(SEPARATOR, "").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        /** Keeps [minutes] inside the day for an event starting at [startMinute]: at least 15, never past midnight. */
        fun fit(startMinute: Int, minutes: Int): Int =
            minutes.coerceIn(DayTimeline.SNAP_MINUTES, (CalendarEvent.DAY_MINUTES - startMinute).coerceAtLeast(DayTimeline.SNAP_MINUTES))
    }
}

/**
 * How an own event repeats. Each rule follows the event's first day: Weekly on its weekday, Monthly on its date (the last
 * day in shorter months), Yearly on its day and month. Never more often than daily.
 */
@Serializable
enum class EventRepeat(val label: String) {
    DAILY("Daily"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Weekly"),
    FORTNIGHTLY("Every 2 weeks"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    /** The schedule for an event first on [first]. */
    fun rule(first: LocalDate): Repeat = when (this) {
        DAILY -> Repeat.Daily()
        WEEKDAYS -> Repeat.Weekly(days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
        WEEKLY -> Repeat.Weekly()
        FORTNIGHTLY -> Repeat.Weekly(every = 2)
        MONTHLY -> Repeat.Monthly(rule = MonthlyRule.DayOfMonth(first.dayOfMonth))
        YEARLY -> Repeat.Yearly()
    }
}

/** Whether this is one of PokéPlan's own events rather than one read from the phone. */
val CalendarEvent.isOwn: Boolean get() = calendarId == PlanEvent.OWN_CALENDAR
