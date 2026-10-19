package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

internal fun LocalDate.plusDays(n: Int): LocalDate = plus(DatePeriod(days = n))

internal fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/** Never more often than daily. */
sealed interface Repeat {
    data class Daily(val every: Int = 1) : Repeat
    /** Chosen weekdays every [every] weeks; empty [days] means the weekday of the start date. */
    data class Weekly(val every: Int = 1, val days: Set<DayOfWeek> = emptySet()) : Repeat
    data class Monthly(val every: Int = 1, val rule: MonthlyRule) : Repeat
    data class Yearly(val every: Int = 1) : Repeat
}

sealed interface MonthlyRule {
    /** A day of the month; in shorter months it falls on the last day. */
    data class DayOfMonth(val day: Int) : MonthlyRule
    /** The [n]th (1 to 4) [weekday] of the month. */
    data class NthWeekday(val n: Int, val weekday: DayOfWeek) : MonthlyRule
    data object LastDay : MonthlyRule
    data class LastWeekday(val weekday: DayOfWeek) : MonthlyRule
}

enum class RepeatMode {
    /** On the schedule, whether or not the last one was done. */
    FIXED,

    /** The next one comes N days (weeks, months) after you finish the last one. */
    AFTER_COMPLETION,
}

sealed interface RepeatEnd {
    data object Never : RepeatEnd
    data class On(val date: LocalDate) : RepeatEnd
    data class After(val count: Int) : RepeatEnd
}

/**
 * A repeating schedule. Dates are wall-clock, so the time of day stays the same across daylight saving.
 * Skipped dates are not generated but still count towards [RepeatEnd.After].
 */
data class Recurrence(
    val repeat: Repeat,
    val start: LocalDate,
    val mode: RepeatMode = RepeatMode.FIXED,
    val end: RepeatEnd = RepeatEnd.Never,
    val skipped: Set<LocalDate> = emptySet(),
) {
    init {
        val every = when (repeat) {
            is Repeat.Daily -> repeat.every
            is Repeat.Weekly -> repeat.every
            is Repeat.Monthly -> repeat.every
            is Repeat.Yearly -> repeat.every
        }
        require(every >= 1) { "repeat interval must be at least 1" }
        if (end is RepeatEnd.After) require(end.count >= 1) { "an end count must be at least 1" }
    }

    private fun mondayOf(d: LocalDate) = d.plusDays(-(d.dayOfWeek.isoDayNumber - 1))

    private fun monthsBetween(a: LocalDate, b: LocalDate) = (b.year - a.year) * 12 + (b.monthNumber - a.monthNumber)

    private fun monthlyDate(rule: MonthlyRule, year: Int, month: Int): LocalDate {
        val length = daysInMonth(year, month)
        return when (rule) {
            is MonthlyRule.DayOfMonth -> LocalDate(year, month, minOf(rule.day, length))
            MonthlyRule.LastDay -> LocalDate(year, month, length)
            is MonthlyRule.NthWeekday -> {
                val first = LocalDate(year, month, 1)
                val offset = (rule.weekday.isoDayNumber - first.dayOfWeek.isoDayNumber + 7) % 7
                first.plusDays(offset + 7 * (rule.n - 1))
            }
            is MonthlyRule.LastWeekday -> {
                val last = LocalDate(year, month, length)
                last.plusDays(-((last.dayOfWeek.isoDayNumber - rule.weekday.isoDayNumber + 7) % 7))
            }
        }
    }

    /** True if the schedule itself (ignoring skips and the end) lands on [d]. Fixed mode. */
    private fun scheduled(d: LocalDate): Boolean {
        if (d < start) return false
        return when (val r = repeat) {
            is Repeat.Daily -> start.daysUntil(d) % r.every == 0
            is Repeat.Weekly -> {
                val days = r.days.ifEmpty { setOf(start.dayOfWeek) }
                mondayOf(start).daysUntil(mondayOf(d)) / 7 % r.every == 0 && d.dayOfWeek in days
            }
            is Repeat.Monthly -> monthsBetween(start, d) % r.every == 0 && d == monthlyDate(r.rule, d.year, d.monthNumber)
            is Repeat.Yearly -> {
                val sameDay = minOf(start.dayOfMonth, daysInMonth(d.year, start.monthNumber))
                (d.year - start.year) % r.every == 0 && d.monthNumber == start.monthNumber && d.dayOfMonth == sameDay
            }
        }
    }

    /** The last date allowed by [end], or null if it never ends. */
    private fun lastDate(): LocalDate? = when (val e = end) {
        RepeatEnd.Never -> null
        is RepeatEnd.On -> e.date
        is RepeatEnd.After -> {
            var count = 0
            var d = start
            var guard = 0
            while (guard++ < 40_000) {
                if (scheduled(d)) { count++; if (count == e.count) break }
                d = d.plusDays(1)
            }
            d
        }
    }

    /**
     * Dates in [from]..[to] (inclusive) on which the task is due, in order. Fixed schedules only: for
     * [RepeatMode.AFTER_COMPLETION] the next date depends on when the last one was finished, see [nextAfterCompletion].
     */
    fun occurrences(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (mode == RepeatMode.AFTER_COMPLETION) return if (from <= start && start <= to && start !in skipped) listOf(start) else emptyList()
        val limit = lastDate()?.let { minOf(it, to) } ?: to
        val result = mutableListOf<LocalDate>()
        var d = maxOf(from, start)
        while (d <= limit) {
            if (scheduled(d) && d !in skipped) result += d
            d = d.plusDays(1)
        }
        return result
    }

    /** For [RepeatMode.AFTER_COMPLETION]: the next date, one interval after [completedOn], or null once the end is reached. */
    fun nextAfterCompletion(completedOn: LocalDate): LocalDate? {
        val next = when (val r = repeat) {
            is Repeat.Daily -> completedOn.plusDays(r.every)
            is Repeat.Weekly -> completedOn.plusDays(7 * r.every)
            is Repeat.Monthly -> {
                val m = completedOn.plus(DatePeriod(months = r.every))
                // keep the day of month, clamped (31 Jan + 1 month = 28/29 Feb)
                LocalDate(m.year, m.monthNumber, minOf(completedOn.dayOfMonth, daysInMonth(m.year, m.monthNumber)))
            }
            is Repeat.Yearly -> {
                val y = completedOn.year + r.every
                LocalDate(y, completedOn.monthNumber, minOf(completedOn.dayOfMonth, daysInMonth(y, completedOn.monthNumber)))
            }
        }
        val e = end
        return if (e is RepeatEnd.On && next > e.date) null else next
    }
}

/** Creates the concrete tasks of a recurring series. */
object RecurringTasks {
    /** Instances are generated this many days ahead. */
    const val HORIZON_DAYS = 14

    fun instanceId(seriesId: String, date: LocalDate) = "$seriesId-$date"

    /**
     * Instances of [template] still to create for today through [HORIZON_DAYS] days ahead: one per due date that does not
     * already exist in [existing]. Each is planned for its date at the template's time, with the series id so it pays straight away.
     */
    fun instancesToCreate(template: Task, recurrence: Recurrence, existing: List<Task>, today: LocalDate, createdAtMillis: Long): List<Task> {
        val seriesId = requireNotNull(template.seriesId) { "a recurring template needs a series id" }
        val have = existing.filter { it.seriesId == seriesId }.mapNotNull { it.planned }.toSet()
        return recurrence.occurrences(today, today.plusDays(HORIZON_DAYS)).filter { it !in have }.map { date ->
            template.copy(
                id = instanceId(seriesId, date),
                planned = date,
                due = template.due?.let { kotlinx.datetime.LocalDateTime(date, it.time) },
                createdAtMillis = createdAtMillis,
                state = TaskState.OPEN,
                completedAtMillis = null,
                paid = false,
                focusedMinutes = 0,
            )
        }
    }

    /** The real moment of a wall-clock [time] on [date]. In a daylight-saving gap the clock jumps forward past it. */
    fun momentOf(date: LocalDate, time: LocalTime, zone: TimeZone): Long = kotlinx.datetime.LocalDateTime(date, time).toEpochMillis(zone)
}
