package app.pokeplan.plan

import kotlinx.serialization.Serializable
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

internal fun LocalDate.plusDays(n: Int): LocalDate = app.pokeplan.kit.Calendars.plusDays(this, n)

internal fun daysInMonth(year: Int, month: Int): Int = app.pokeplan.kit.Calendars.daysInMonth(year, month)

@Serializable
sealed interface Repeat {
    @Serializable
    data class Daily(val every: Int = 1) : Repeat
    @Serializable
    data class Weekly(val every: Int = 1, val days: Set<DayOfWeek> = emptySet()) : Repeat
    @Serializable
    data class Monthly(val every: Int = 1, val rule: MonthlyRule) : Repeat
    @Serializable
    data class Yearly(val every: Int = 1) : Repeat
}

@Serializable
sealed interface MonthlyRule {
    @Serializable
    data class DayOfMonth(val day: Int) : MonthlyRule
    @Serializable
    data class NthWeekday(val n: Int, val weekday: DayOfWeek) : MonthlyRule
    @Serializable
    data object LastDay : MonthlyRule
    @Serializable
    data class LastWeekday(val weekday: DayOfWeek) : MonthlyRule
}

enum class RepeatMode {
    FIXED,

    AFTER_COMPLETION,
}

@Serializable
sealed interface RepeatEnd {
    @Serializable
    data object Never : RepeatEnd
    @Serializable
    data class On(val date: LocalDate) : RepeatEnd
    @Serializable
    data class After(val count: Int) : RepeatEnd
}

@Serializable
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


    private fun monthsBetween(a: LocalDate, b: LocalDate) = app.pokeplan.kit.Calendars.monthsBetween(a, b)

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

    private fun scheduled(d: LocalDate): Boolean {
        if (d < start) return false
        return when (val r = repeat) {
            is Repeat.Daily -> start.daysUntil(d) % r.every == 0
            is Repeat.Weekly -> {
                val days = r.days.ifEmpty { setOf(start.dayOfWeek) }
                Streaks.weekStartOf(start, DayOfWeek.MONDAY).daysUntil(Streaks.weekStartOf(d, DayOfWeek.MONDAY)) / 7 % r.every == 0 && d.dayOfWeek in days
            }
            is Repeat.Monthly -> monthsBetween(start, d) % r.every == 0 && d == monthlyDate(r.rule, d.year, d.monthNumber)
            is Repeat.Yearly -> {
                val sameDay = minOf(start.day, daysInMonth(d.year, start.monthNumber))
                (d.year - start.year) % r.every == 0 && d.monthNumber == start.monthNumber && d.day == sameDay
            }
        }
    }

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

    fun nextAfterCompletion(completedOn: LocalDate): LocalDate? {
        val next = when (val r = repeat) {
            is Repeat.Daily -> completedOn.plusDays(r.every)
            is Repeat.Weekly -> completedOn.plusDays(7 * r.every)
            is Repeat.Monthly -> {
                val m = completedOn.plus(DatePeriod(months = r.every))
                LocalDate(m.year, m.monthNumber, minOf(completedOn.day, daysInMonth(m.year, m.monthNumber)))
            }
            is Repeat.Yearly -> {
                val y = completedOn.year + r.every
                LocalDate(y, completedOn.monthNumber, minOf(completedOn.day, daysInMonth(y, completedOn.monthNumber)))
            }
        }
        val e = end
        return if (e is RepeatEnd.On && next > e.date) null else next
    }
}

object RecurringTasks {
    const val HORIZON_DAYS = 14

    fun instanceId(seriesId: String, date: LocalDate) = "$seriesId-$date"

    fun instancesToCreate(template: Task, recurrence: Recurrence, existing: List<Task>, today: LocalDate, createdAtMillis: Long): List<Task> {
        val seriesId = requireNotNull(template.seriesId) { "a recurring template needs a series id" }
        val ours = existing.filter { it.seriesId == seriesId }
        val have = ours.mapNotNull { it.planned }.toSet()
        val ids = ours.mapTo(HashSet()) { it.id }
        return recurrence.occurrences(today, today.plusDays(HORIZON_DAYS)).filter { it !in have && instanceId(seriesId, it) !in ids }.map { date ->
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

    fun momentOf(date: LocalDate, time: LocalTime, zone: TimeZone): Long = kotlinx.datetime.LocalDateTime(date, time).toEpochMillis(zone)
}
