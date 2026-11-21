package app.pokeplan.plan

import kotlinx.serialization.Serializable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

@Serializable
sealed interface HabitTarget {
    @Serializable
    data object Daily : HabitTarget
    @Serializable
    data class Weekdays(val days: Set<DayOfWeek>) : HabitTarget
    @Serializable
    data class PerWeek(val times: Int) : HabitTarget
    @Serializable
    data class PerMonth(val times: Int) : HabitTarget
}

@Serializable
data class Habit(
    val id: String,
    val title: String,
    val rank: Rank = Rank.C,
    val target: HabitTarget,
    val createdOn: LocalDate,
    val completions: Set<LocalDate> = emptySet(),
    val paidOn: LocalDate? = null,
) {
    init {
        when (target) {
            is HabitTarget.Weekdays -> require(target.days.isNotEmpty()) { "choose at least one weekday" }
            is HabitTarget.PerWeek -> require(target.times in 1..7) { "1 to 7 times a week" }
            is HabitTarget.PerMonth -> require(target.times in 1..31) { "1 to 31 times a month" }
            HabitTarget.Daily -> {}
        }
    }

    fun paysOn(date: LocalDate): Boolean = date !in completions && date != paidOn

    fun complete(date: LocalDate): Habit = copy(completions = completions + date)

    fun paid(date: LocalDate): Habit = copy(paidOn = date)

    fun undo(date: LocalDate): Habit = copy(completions = completions - date)

    fun isScheduledOn(date: LocalDate): Boolean = when (val t = target) {
        HabitTarget.Daily, is HabitTarget.PerWeek, is HabitTarget.PerMonth -> true
        is HabitTarget.Weekdays -> date.dayOfWeek in t.days
    }

    fun streak(today: LocalDate, rest: Set<LocalDate> = emptySet(), extraFreeMisses: Int = 0, weekStart: DayOfWeek = DayOfWeek.MONDAY): Int =
        when (val t = target) {
            HabitTarget.Daily, is HabitTarget.Weekdays ->
                Streaks.walk({ it in completions }, { it in rest }, ::isScheduledOn, createdOn, today, Streaks.FREE_MISSES_PER_WEEK + extraFreeMisses, weekStart)
            is HabitTarget.PerWeek -> periods(today, t.times, weekStart, rest)
            is HabitTarget.PerMonth -> monthPeriods(today, t.times)
        }

    private fun periods(today: LocalDate, times: Int, weekStart: DayOfWeek, rest: Set<LocalDate>): Int {
        var week = Streaks.weekStartOf(today, weekStart)
        var streak = 0
        var first = true
        while (week.plusDays(6) >= createdOn) {
            val days = (0..6).map { week.plusDays(it) }
            val done = days.count { it in completions }
            val restDays = days.count { it in rest }
            val needed = (times - restDays.coerceAtMost(times)).coerceAtLeast(1)
            when {
                done >= needed -> streak++
                first -> {}
                done >= needed - 1 -> streak++
                else -> break
            }
            first = false
            week = week.plusDays(-7)
        }
        return streak
    }

    private fun monthPeriods(today: LocalDate, times: Int): Int {
        var year = today.year
        var month = today.monthNumber
        var streak = 0
        var first = true
        while (LocalDate(year, month, daysInMonth(year, month)) >= createdOn) {
            val done = completions.count { it.year == year && it.monthNumber == month }
            when {
                done >= times -> streak++
                first -> {}
                done >= times - 1 -> streak++
                else -> break
            }
            first = false
            if (month == 1) { month = 12; year-- } else month--
        }
        return streak
    }
}
