package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

typealias FrequencyTarget = HabitTarget

data class FrequencyProgress(val done: Int, val target: Int, val unit: String) {
    val reached: Boolean get() = done >= target
    fun label(): String = "$done / $target $unit"
}

object Frequency {
    fun progress(target: FrequencyTarget, completions: Set<LocalDate>, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): FrequencyProgress = when (target) {
        HabitTarget.Daily -> FrequencyProgress(if (today in completions) 1 else 0, 1, "today")
        is HabitTarget.Weekdays -> {
            val first = Streaks.weekStartOf(today, weekStart)
            val week = (0..6).map { first.plusDays(it) }
            FrequencyProgress(week.count { it in completions && it.dayOfWeek in target.days }, target.days.size, "this week")
        }
        is HabitTarget.PerWeek -> {
            val first = Streaks.weekStartOf(today, weekStart)
            FrequencyProgress((0..6).count { first.plusDays(it) in completions }, target.times, "this week")
        }
        is HabitTarget.PerMonth -> FrequencyProgress(completions.count { it.year == today.year && it.monthNumber == today.monthNumber }, target.times, "this month")
    }

    fun dueToday(target: FrequencyTarget, completions: Set<LocalDate>, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Boolean {
        if (today in completions) return false
        if (target is HabitTarget.Weekdays && today.dayOfWeek !in target.days) return false
        return !progress(target, completions, today, weekStart).reached
    }
}

object TargetConversion {
    fun spreadDays(times: Int): Set<DayOfWeek> {
        val k = times.coerceIn(1, 7)
        return (0 until k).map { DayOfWeek.entries[it * 7 / k] }.toSet()
    }

    fun toRecurrence(target: FrequencyTarget, start: LocalDate): Recurrence = Recurrence(
        repeat = when (target) {
            HabitTarget.Daily -> Repeat.Daily(1)
            is HabitTarget.Weekdays -> Repeat.Weekly(1, target.days)
            is HabitTarget.PerWeek -> if (target.times >= 7) Repeat.Daily(1) else Repeat.Weekly(1, spreadDays(target.times))
            is HabitTarget.PerMonth ->
                if (target.times <= 1) Repeat.Monthly(1, MonthlyRule.DayOfMonth(start.dayOfMonth.coerceAtMost(28)))
                else Repeat.Weekly(1, spreadDays(((target.times * 7 + 15) / 30).coerceAtLeast(1)))
        },
        start = start,
    )
}
