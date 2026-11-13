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
