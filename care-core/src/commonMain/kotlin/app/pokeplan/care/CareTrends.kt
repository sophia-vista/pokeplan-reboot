package app.pokeplan.care

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Rolling trends and personal bests, never streaks: a quiet week costs nothing and says nothing. */
object CareTrends {
    data class MoodWeek(val weekStart: LocalDate, val average: Double?, val checkIns: Int)

    /** The average mood of each of the last [weeks] weeks, oldest first; a week with no check-ins has no average. */
    fun moodWeeks(moods: List<MoodCheckIn>, today: LocalDate, weeks: Int, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<MoodWeek> {
        val current = CareRules.weekStartOf(today, weekStart)
        return (weeks - 1 downTo 0).map { back ->
            val start = current.plus(DatePeriod(days = -7 * back))
            val end = start.plus(DatePeriod(days = 7))
            val inWeek = moods.filter { it.day >= start && it.day < end }
            MoodWeek(start, if (inWeek.isEmpty()) null else inWeek.map { it.mood.score }.average(), inWeek.size)
        }
    }

    /** Routines done in the 7 days up to and including [today]. */
    fun routinesLast7Days(routines: List<Routine>, today: LocalDate): Int {
        val from = today.plus(DatePeriod(days = -6))
        return routines.sumOf { r -> r.completions.count { it >= from && it <= today } }
    }

    /** The most routines done in any calendar week so far. */
    fun bestWeek(routines: List<Routine>, weekStart: DayOfWeek = DayOfWeek.MONDAY): Int =
        routines.flatMap { it.completions }.groupingBy { CareRules.weekStartOf(it, weekStart) }.eachCount().values.maxOrNull() ?: 0

    /** Minutes of calm breaks in the 7 days up to and including [today]. */
    fun calmMinutesLast7Days(breaks: List<CalmBreak>, today: LocalDate): Int {
        val from = today.plus(DatePeriod(days = -6))
        return breaks.filter { it.day >= from && it.day <= today }.sumOf { it.seconds } / 60
    }
}
