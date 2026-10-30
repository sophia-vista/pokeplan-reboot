package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuickAddTest {
    private val monday = LocalDate(2026, 10, 19)

    private fun parse(text: String) = QuickAdd.parse(text, monday)

    @Test
    fun theExampleFromTheDesign() {
        val r = parse("Call mum tomorrow 5pm #Social !B")
        assertEquals("Call mum", r.title)
        assertEquals(LocalDate(2026, 10, 20), r.planned)
        assertEquals(LocalTime(17, 0), r.plannedTime)
        assertEquals(Category.SOCIAL, r.category)
        assertEquals(Rank.B, r.rank)
        assertNull(r.due)
    }

    @Test
    fun plainTextIsJustATitle() {
        val r = parse("buy milk")
        assertEquals(QuickAdd.Result("buy milk"), r)
    }

    @Test
    fun deadlinesDurationsAndRanks() {
        val r = parse("Write report by friday 3pm for 2h !a #work")
        assertEquals("Write report", r.title)
        assertEquals(LocalDateTime(2026, 10, 23, 15, 0), r.due)
        assertNull(r.planned)
        assertEquals(120, r.estimateMinutes)
        assertEquals(Rank.A, r.rank)
        assertEquals(Category.WORK, r.category)
    }

    @Test
    fun aDeadlineWithoutATimeIsEndOfDay() {
        assertEquals(LocalDateTime(2026, 10, 21, 23, 59), parse("Pay rent due wednesday").due)
    }

    @Test
    fun plannedAndDueTogether() {
        val r = parse("Review notes tomorrow 9:30 by friday")
        assertEquals(LocalDate(2026, 10, 20), r.planned)
        assertEquals(LocalTime(9, 30), r.plannedTime)
        assertEquals(LocalDateTime(2026, 10, 23, 23, 59), r.due)
        assertEquals("Review notes", r.title)
    }

    @Test
    fun weekdaysMeanTheNextOneAfterToday() {
        assertEquals(LocalDate(2026, 10, 26), parse("gym monday").planned, "today is Monday, so a week away")
        assertEquals(LocalDate(2026, 10, 26), parse("gym next mon").planned)
        assertEquals(LocalDate(2026, 10, 25), parse("gym sun").planned)
    }

    @Test
    fun relativeAndAbsoluteDates() {
        assertEquals(LocalDate(2026, 10, 22), parse("x in 3 days").planned)
        assertEquals(LocalDate(2026, 11, 2), parse("x in 2 weeks").planned)
        assertEquals(LocalDate(2026, 11, 1), parse("Pay rent 2026-11-01").planned)
        assertEquals(LocalDate(2026, 10, 25), parse("Dentist oct 25 9:30am").planned)
        assertEquals(LocalTime(9, 30), parse("Dentist oct 25 9:30am").plannedTime)
        assertEquals(LocalDate(2026, 12, 3), parse("Party 3 dec").planned)
        assertEquals(LocalDate(2027, 1, 5), parse("Dentist jan 5").planned, "a date already past rolls to next year")
        assertEquals("Dentist", parse("Dentist jan 5").title)
    }

    @Test
    fun tonightIsAnEveningTask() {
        val r = parse("Laundry tonight")
        assertEquals(monday, r.planned)
        assertEquals(LocalTime(20, 0), r.plannedTime)
        assertEquals(true, r.evening)
    }

    @Test
    fun timesInDifferentForms() {
        assertEquals(LocalTime(17, 30), parse("x 17:30").plannedTime)
        assertEquals(LocalTime(0, 15), parse("x 12:15am").plannedTime)
        assertEquals(LocalTime(12, 0), parse("x at 12pm").plannedTime)
        assertEquals(monday, parse("x 5pm").planned, "a time alone means today")
        assertEquals("x", parse("x at 5pm").title)
    }

    @Test
    fun durationForms() {
        assertEquals(30, parse("x 30m").estimateMinutes)
        assertEquals(45, parse("x 45 min").estimateMinutes)
        assertEquals(90, parse("x 1h30").estimateMinutes)
        assertEquals(60, parse("x for 1 hour").estimateMinutes)
        assertNull(parse("x 0m").estimateMinutes, "no estimate is better than a task that cannot exist")
        assertNull(parse("x 99999999999m").estimateMinutes)
        assertNull(parse("x 0 min").estimateMinutes)
        assertEquals("x 0m", parse("x 0m").title)
        assertEquals("x", parse("x for 90 minutes").title)
    }

    @Test
    fun unknownHashtagsBecomeLists() {
        val r = parse("Eggs #Groceries")
        assertEquals("Groceries", r.listName)
        assertNull(r.category)
        assertEquals("Eggs", r.title)
    }

    @Test
    fun wordsThatLookLikeCommandsStayInTheTitle() {
        assertEquals("Written by John", parse("Written by John").title)
        assertEquals("Meeting at the office", parse("Meeting at the office").title)
        assertEquals("Pick up 3 apples", parse("Pick up 3 apples").title)
    }
}
