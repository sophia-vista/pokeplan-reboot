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
    fun aPractisedSkillCountsTowardsTheRhythmAndPaysOnceADay() {
        val data = CareData(practices = listOf(SkillPractice(0, day(0), "stop", paid = true)))
        assertFalse(CareRules.skillPaysOn(data, "stop", day(0)))
        assertTrue(CareRules.skillPaysOn(data, "stop", day(1)))
        assertEquals(setOf(day(0)), CareRules.careDays(data, monday))
    }

    @Test
    fun everySkillIsInTheLibraryOnceAndEveryGroupHasSome() {
        assertEquals(Skills.all.size, Skills.all.map { it.id }.toSet().size)
        SkillGroup.entries.forEach { assertTrue(Skills.inGroup(it).isNotEmpty(), "$it") }
        assertEquals(1, Skills.all.count { it.calmBreak })
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
        val cards = listOf(
            DiaryCard("a", day(0), 0, mapOf(Emotion.JOY to 4, Emotion.FEAR to 1)), DiaryCard("b", day(1), 0, mapOf(Emotion.JOY to 3)),
            DiaryCard("c", day(9), 0, mapOf(Emotion.CALM to 5), skills = setOf("stop")),
        )
        val weeks = CareTrends.feelingWeeks(cards, day(9), 3)
        assertEquals(listOf(null, 3.5, null), weeks.map { it.averages[Emotion.JOY] })
        assertEquals(listOf(0, 2, 1), weeks.map { it.cards })
        assertEquals(day(-7), weeks.first().weekStart)
        val practised = CareData(diary = cards, practices = listOf(SkillPractice(0, day(9), "stop"), SkillPractice(0, day(8), "tipp")))
        assertEquals(2, CareTrends.skillsLast7Days(practised, day(9)), "a skill ticked and practised on the same day counts once")
        assertEquals(1, CareTrends.calmMinutesLast7Days(listOf(CalmBreak(0, day(0), 90), CalmBreak(0, day(-10), 120)), day(2)))
    }
}
