package app.pokeplan.plan

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** When a reminder fires relative to its task's time. */
sealed interface ReminderOffset {
    data object AtTime : ReminderOffset
    data class Before(val minutes: Int) : ReminderOffset
}

enum class Snooze { TEN_MINUTES, ONE_HOUR, TOMORROW }

/** Quiet hours may wrap past midnight (22:00 to 07:00). Reminders inside them wait for [end]. */
data class QuietHours(val start: LocalTime = LocalTime(22, 0), val end: LocalTime = LocalTime(7, 0)) {
    fun contains(t: LocalTime): Boolean = if (start <= end) t >= start && t < end else t >= start || t < end
}

object Reminders {
    const val MAX_PER_TASK = 3

    /** Tasks with a date but no time remind relative to this. *(tune)* */
    val DEFAULT_TIME = LocalTime(9, 0)
    val TOMORROW_TIME = LocalTime(9, 0)

    /** The moment reminders count from: the planned time, else the deadline, else the planned day at [DEFAULT_TIME]. */
    fun baseTime(task: Task): LocalDateTime? = when {
        task.planned != null && task.plannedTime != null -> LocalDateTime(task.planned, task.plannedTime)
        task.due != null -> task.due
        task.planned != null -> LocalDateTime(task.planned, DEFAULT_TIME)
        else -> null
    }

    private fun minus(t: LocalDateTime, minutes: Int, zone: TimeZone): LocalDateTime =
        (t.toEpochMillis(zone) - minutes * 60_000L).toLocalDateTime(zone)

    /** Moves a reminder out of quiet hours: to the end of the quiet window (the next morning if it fell late at night). */
    fun outOfQuietHours(t: LocalDateTime, quiet: QuietHours): LocalDateTime {
        if (!quiet.contains(t.time)) return t
        val day = if (t.time >= quiet.end && quiet.start > quiet.end) t.date.plusDays(1) else t.date
        return LocalDateTime(day, quiet.end)
    }

    /** At most [MAX_PER_TASK] reminders, as wall-clock times after quiet hours are applied, earliest first. */
    fun fireTimes(task: Task, reminders: List<ReminderOffset>, zone: TimeZone, quiet: QuietHours = QuietHours()): List<LocalDateTime> {
        val base = baseTime(task) ?: return emptyList()
        require(reminders.size <= MAX_PER_TASK) { "at most $MAX_PER_TASK reminders per task" }
        return reminders.map { offset ->
            val at = when (offset) {
                ReminderOffset.AtTime -> base
                is ReminderOffset.Before -> minus(base, offset.minutes, zone)
            }
            outOfQuietHours(at, quiet)
        }.sorted()
    }

    fun snoozedTo(now: LocalDateTime, snooze: Snooze, zone: TimeZone): LocalDateTime = when (snooze) {
        Snooze.TEN_MINUTES -> minus(now, -10, zone)
        Snooze.ONE_HOUR -> minus(now, -60, zone)
        Snooze.TOMORROW -> LocalDateTime(now.date.plusDays(1), TOMORROW_TIME)
    }

    /** Reminders that would fire during a focus session wait until it ends. */
    fun delayedByFocus(fireAt: LocalDateTime, focusStart: LocalDateTime, focusEnd: LocalDateTime): LocalDateTime =
        if (fireAt >= focusStart && fireAt < focusEnd) focusEnd else fireAt
}
