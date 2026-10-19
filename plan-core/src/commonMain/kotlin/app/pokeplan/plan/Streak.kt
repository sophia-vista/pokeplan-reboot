package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber

/**
 * Forgiving streaks. Everyone gets [FREE_MISSES_PER_WEEK] missed day per week that does not break a streak (the Streak keeper
 * perk adds more), rest days are neutral, and a day that is not over yet never breaks it.
 */
object Streaks {
    const val FREE_MISSES_PER_WEEK = 1

    /** Rest days are capped per month so they stay a pause, not a way to hold a streak forever. *(tune)* */
    const val MAX_REST_DAYS_PER_MONTH = 14

    /** Days away after which the welcome-back gift is offered. */
    const val WELCOME_BACK_DAYS = 7

    fun weekStartOf(d: LocalDate, weekStart: DayOfWeek): LocalDate =
        d.plusDays(-((d.dayOfWeek.isoDayNumber - weekStart.isoDayNumber + 7) % 7))

    data class Result(val current: Int, val longest: Int)

    /**
     * The streak of days with activity. [active] are the days with a paid task or a finished session; [rest] are rest days.
     * A missed day uses up one of the week's free misses; with none left the streak ends there.
     */
    fun daily(
        active: Set<LocalDate>,
        rest: Set<LocalDate>,
        today: LocalDate,
        extraFreeMisses: Int = 0,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): Result {
        val first = active.minOrNull() ?: return Result(0, 0)
        return Result(
            current = walk({ it in active }, { it in rest }, { true }, first, today, FREE_MISSES_PER_WEEK + extraFreeMisses, weekStart),
            longest = longest(active, rest, first, today, FREE_MISSES_PER_WEEK + extraFreeMisses, weekStart),
        )
    }

    /**
     * Walks back from [today] counting scheduled, active days. A scheduled day that was missed uses one of that week's free
     * misses or ends the walk. Today does not count against the streak until it is over.
     */
    internal fun walk(
        isActive: (LocalDate) -> Boolean,
        isRest: (LocalDate) -> Boolean,
        isScheduled: (LocalDate) -> Boolean,
        startDate: LocalDate,
        today: LocalDate,
        freeMisses: Int,
        weekStart: DayOfWeek,
    ): Int {
        var streak = 0
        val used = mutableMapOf<LocalDate, Int>()
        var d = if (isActive(today)) today else today.plusDays(-1)
        while (d >= startDate) {
            when {
                isRest(d) || !isScheduled(d) -> {}
                isActive(d) -> streak++
                else -> {
                    val week = weekStartOf(d, weekStart)
                    val misses = used[week] ?: 0
                    if (misses < freeMisses) used[week] = misses + 1 else break
                }
            }
            d = d.plusDays(-1)
        }
        return streak
    }

    private fun longest(active: Set<LocalDate>, rest: Set<LocalDate>, first: LocalDate, today: LocalDate, freeMisses: Int, weekStart: DayOfWeek): Int {
        var best = 0
        var d = first
        while (d <= today) {
            if (d in active) best = maxOf(best, walk({ it in active }, { it in rest }, { true }, first, d, freeMisses, weekStart))
            d = d.plusDays(1)
        }
        return best
    }

    /** Active days in the week containing [today], for the "active days this week" ring (out of 7). */
    fun activeDaysThisWeek(active: Set<LocalDate>, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Int {
        val start = weekStartOf(today, weekStart)
        return (0..6).count { start.plusDays(it) in active }
    }

    /** Whether adding [newDays] keeps rest days within the monthly cap. */
    fun canAddRestDays(existing: Set<LocalDate>, newDays: Set<LocalDate>): Boolean =
        (existing + newDays).groupBy { it.year * 100 + it.monthNumber }.values.all { it.size <= MAX_REST_DAYS_PER_MONTH }

    /** After a long absence the player gets a gift and a fresh start, never a scolding. */
    fun welcomeBack(lastActive: LocalDate?, today: LocalDate): Boolean =
        lastActive != null && lastActive.daysUntil(today) >= WELCOME_BACK_DAYS
}
