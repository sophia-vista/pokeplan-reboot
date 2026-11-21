package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber

object Streaks {
    const val FREE_MISSES_PER_WEEK = 1

    const val MAX_REST_DAYS_PER_MONTH = 14

    const val ROLLOVER_HOUR = 4

    fun streakDay(at: kotlinx.datetime.LocalDateTime): LocalDate = if (at.hour < ROLLOVER_HOUR) at.date.plusDays(-1) else at.date

    const val WELCOME_BACK_DAYS = 7

    fun weekStartOf(d: LocalDate, weekStart: DayOfWeek): LocalDate =
        d.plusDays(-((d.dayOfWeek.isoDayNumber - weekStart.isoDayNumber + 7) % 7))

    data class Result(val current: Int, val longest: Int)

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

    fun activeDaysThisWeek(active: Set<LocalDate>, today: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): Int {
        val start = weekStartOf(today, weekStart)
        return (0..6).count { start.plusDays(it) in active }
    }

    fun canAddRestDays(existing: Set<LocalDate>, newDays: Set<LocalDate>): Boolean =
        (existing + newDays).groupBy { it.year * 100 + it.monthNumber }.values.all { it.size <= MAX_REST_DAYS_PER_MONTH }

    fun welcomeBack(lastActive: LocalDate?, today: LocalDate): Boolean =
        lastActive != null && lastActive.daysUntil(today) >= WELCOME_BACK_DAYS
}
