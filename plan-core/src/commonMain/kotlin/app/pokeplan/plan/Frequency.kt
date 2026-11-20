package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * A frequency target is the second way a recurring task repeats (docs/pokelife `MODULES/PLAN.md` section 5): not on calendar dates but
 * "so many times in a period" (every day, on chosen weekdays, N a week, N a month). It is the same shape the Habit record used, so
 * stored targets keep reading; there is no separate Habit record type any more: a Habit of an older save becomes a recurring task with
 * this target ([Habit.toSeriesParts], applied once when the save loads), with its id, its completion days and its payment day.
 */
typealias FrequencyTarget = HabitTarget

/** How far a frequency target is in the current period: [done] of [target], in [unit] ("today", "this week", "this month"). */
data class FrequencyProgress(val done: Int, val target: Int, val unit: String) {
    val reached: Boolean get() = done >= target
    fun label(): String = "$done / $target $unit"
}

object Frequency {
    /**
     * The target's progress on [today] from the days it was done. Only valid completion days count; nothing is invented for days not done.
     * A week starts on [weekStart] and a month on its first day, in the player's local calendar.
     */
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

    /**
     * Whether the target shows on Today (PLAN.md section 3.1): not done today, today one of its days (for chosen weekdays), and its
     * period not met yet. A target met for the week asks for nothing more that week.
     */
    fun dueToday(target: FrequencyTarget, completions: Set<LocalDate>, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Boolean {
        if (today in completions) return false
        if (target is HabitTarget.Weekdays && today.dayOfWeek !in target.days) return false
        return !progress(target, completions, today, weekStart).reached
    }
}

/**
 * The frequency targets were scrapped on 20 November 2026 (owner; PLAN.md section 5): a saved target becomes an ordinary recurring task, and
 * this maps its "how often" onto the nearest calendar rule. Every day and chosen weekdays map exactly; "N a week" and "N a month" have no exact
 * calendar form, so they become a weekly rule on N days spread across the week (a month's N becomes about N / 4 days a week, and once a month
 * becomes the day of the month it began on). The completion history is kept by the caller.
 */
object TargetConversion {
    /** [times] days of the week spread as evenly as they go, from Monday: 1 is Monday, 2 Monday and Thursday, 3 Monday, Wednesday and Friday. */
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
