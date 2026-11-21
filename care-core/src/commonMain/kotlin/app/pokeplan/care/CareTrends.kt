package app.pokeplan.care

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

object CareTrends {
    data class FeelingWeek(val weekStart: LocalDate, val averages: Map<Emotion, Double>, val cards: Int)

    fun feelingWeeks(cards: List<DiaryCard>, today: LocalDate, weeks: Int, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<FeelingWeek> {
        val current = CareRules.weekStartOf(today, weekStart)
        return (weeks - 1 downTo 0).map { back ->
            val start = current.plus(DatePeriod(days = -7 * back))
            val end = start.plus(DatePeriod(days = 7))
            val inWeek = cards.filter { it.day >= start && it.day < end }
            val averages = Emotion.entries.mapNotNull { e ->
                val rated = inWeek.mapNotNull { it.feelings[e] }
                if (rated.isEmpty()) null else e to rated.average()
            }.toMap()
            FeelingWeek(start, averages, inWeek.size)
        }
    }

    fun skillsLast7Days(data: CareData, today: LocalDate): Int {
        val from = today.plus(DatePeriod(days = -6))
        fun inRange(d: LocalDate) = d >= from && d <= today
        val practised = data.practices.filter { inRange(it.day) }.map { it.day to it.skillId }
        val ticked = data.diary.filter { inRange(it.day) }.flatMap { c -> c.skills.map { c.day to it } }
        return (practised + ticked).toSet().size
    }

    fun routinesLast7Days(routines: List<Routine>, today: LocalDate): Int {
        val from = today.plus(DatePeriod(days = -6))
        return routines.sumOf { r -> r.completions.count { it >= from && it <= today } }
    }

    fun bestWeek(routines: List<Routine>, weekStart: DayOfWeek = DayOfWeek.MONDAY): Int =
        routines.flatMap { it.completions }.groupingBy { CareRules.weekStartOf(it, weekStart) }.eachCount().values.maxOrNull() ?: 0

    fun calmMinutesLast7Days(breaks: List<CalmBreak>, today: LocalDate): Int {
        val from = today.plus(DatePeriod(days = -6))
        return breaks.filter { it.day >= from && it.day <= today }.sumOf { it.seconds } / 60
    }
}
