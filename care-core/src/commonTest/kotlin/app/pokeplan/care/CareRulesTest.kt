package app.pokeplan.care

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareRulesTest {
    private val monday = LocalDate(2026, 10, 19)
    private fun day(n: Int) = monday.plus(DatePeriod(days = n))
    private fun routine(days: Set<DayOfWeek> = emptySet(), done: Set<LocalDate> = emptySet()) =
        Routine("r", "Water", days = days, createdOn = monday, completions = done)

    @Test
    fun aRoutinePaysOnceADayEvenAfterAnUndo() {
        val r = routine()
        assertTrue(r.paysOn(monday))
        val paid = r.complete(monday).paid(monday)
        assertFalse(paid.paysOn(monday))
        assertFalse(paid.undo(monday).paysOn(monday), "undo and tick again never pays twice")
        assertTrue(paid.paysOn(day(1)))
    }

    @Test
    fun aRoutineIsDueOnItsDaysAndNotBeforeItWasMade() {
        val weekdays = routine(days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertTrue(weekdays.isDueOn(monday))
        assertFalse(weekdays.isDueOn(day(1)))
        assertTrue(weekdays.isDueOn(day(2)))
        assertFalse(routine().isDueOn(day(-1)))
        assertFalse(routine().copy(archived = true).isDueOn(monday))
    }

    @Test
    fun aRhythmNeedsFourDaysOfCareInTheWeekAndPaysOnce() {
        val three = CareData(routines = listOf(routine(done = setOf(day(0), day(1)))), breaks = listOf(CalmBreak(0, day(3), 90)))
        assertFalse(CareRules.rhythmDue(three, monday))
        val four = three.copy(breaks = three.breaks + CalmBreak(0, day(6), 90))
        assertTrue(CareRules.rhythmDue(four, monday))
        assertFalse(CareRules.rhythmDue(four.copy(rhythmWeeks = setOf(monday)), monday))
        val nextWeek = three.copy(breaks = three.breaks + CalmBreak(0, day(7), 90))
        assertFalse(CareRules.rhythmDue(nextWeek, monday), "a day in the next week counts for that week")
    }

    @Test
    fun weeksCanStartOnSunday() {
        assertEquals(day(-1), CareRules.weekStartOf(day(3), DayOfWeek.SUNDAY))
        assertEquals(monday, CareRules.weekStartOf(day(6), DayOfWeek.MONDAY))
    }

    @Test
    fun promptsTurnWithTheDayAndRunOutWhenAllAreSkipped() {
        val first = Prompts.forDay(monday)!!
        assertTrue(first != Prompts.forDay(day(1)))
        assertTrue(first != Prompts.forDay(monday, skipped = 1))
        assertNull(Prompts.forDay(monday, skipped = Prompts.all.size))
        assertEquals(Prompts.all.size, Prompts.all.map { it.id }.toSet().size)
    }

    @Test
    fun trendsAreRollingCountsAndBests() {
        val r = routine(done = setOf(day(0), day(1), day(2), day(8)))
        assertEquals(1, CareTrends.routinesLast7Days(listOf(r), day(14)))
        assertEquals(2, CareTrends.routinesLast7Days(listOf(r), day(8)))
        assertEquals(3, CareTrends.bestWeek(listOf(r)))
        val moods = listOf(
            MoodCheckIn("a", 0, day(0), Mood.GOOD), MoodCheckIn("b", 0, day(1), Mood.OKAY), MoodCheckIn("c", 0, day(9), Mood.GREAT),
        )
        val weeks = CareTrends.moodWeeks(moods, day(9), 3)
        assertEquals(listOf(null, 3.5, 5.0), weeks.map { it.average })
        assertEquals(day(-7), weeks.first().weekStart)
        assertEquals(1, CareTrends.calmMinutesLast7Days(listOf(CalmBreak(0, day(0), 90), CalmBreak(0, day(-10), 120)), day(2)))
    }
}
