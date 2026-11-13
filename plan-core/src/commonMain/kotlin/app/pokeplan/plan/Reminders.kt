package app.pokeplan.plan

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/** When a reminder fires relative to its task's time. */
@Serializable
sealed interface ReminderOffset {
    @Serializable
    data object AtTime : ReminderOffset
    @Serializable
    data class Before(val minutes: Int) : ReminderOffset

    val label: String get() = when (this) {
        AtTime -> "At the time"
        is Before -> when {
            minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} day${if (minutes == 24 * 60) "" else "s"} before"
            minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes == 60) "" else "s"} before"
            else -> "$minutes min before"
        }
    }

    companion object {
        /** The choices the task card offers as keys; any other length before is typed ([MAX_BEFORE_MINUTES] at most). */
        val PRESETS: List<ReminderOffset> = listOf(AtTime, Before(10), Before(30), Before(60), Before(2 * 60), Before(24 * 60))

        /** The longest a typed reminder may come before: a week. */
        const val MAX_BEFORE_MINUTES = 7 * 24 * 60
    }
}

/** One reminder to show: [task] at [atMillis]. */
data class DueReminder(val taskId: String, val title: String, val atMillis: Long)

enum class Snooze { TEN_MINUTES, ONE_HOUR, TOMORROW }

/** Quiet hours may wrap past midnight (22:00 to 07:00). Reminders inside them wait for [end]. */
@Serializable
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

    /**
     * Every reminder of the open [tasks] still to come after [nowMillis], earliest first: quiet hours applied, nothing on
     * a [restDays] day, and anything due while a focus session runs (until [focusEndMillis]) moved to its end.
     */
    fun upcoming(
        tasks: List<Task>,
        nowMillis: Long,
        zone: TimeZone,
        quiet: QuietHours = QuietHours(),
        restDays: Set<LocalDate> = emptySet(),
        focusEndMillis: Long? = null,
    ): List<DueReminder> = tasks.asSequence()
        .filter { it.isOpen && it.reminders.isNotEmpty() }
        .flatMap { task ->
            fireTimes(task, task.reminders, zone, quiet).asSequence()
                .filter { it.date !in restDays }
                .map { at ->
                    val millis = at.toEpochMillis(zone)
                    DueReminder(task.id, task.title, if (focusEndMillis != null && millis in nowMillis until focusEndMillis) focusEndMillis else millis)
                }
                .distinctBy { it.atMillis }
        }
        .filter { it.atMillis > nowMillis }
        .sortedWith(compareBy({ it.atMillis }, { it.taskId }))
        .toList()

    /** Reminders that would fire during a focus session wait until it ends. */
    fun delayedByFocus(fireAt: LocalDateTime, focusStart: LocalDateTime, focusEnd: LocalDateTime): LocalDateTime =
        if (fireAt >= focusStart && fireAt < focusEnd) focusEnd else fireAt
}
