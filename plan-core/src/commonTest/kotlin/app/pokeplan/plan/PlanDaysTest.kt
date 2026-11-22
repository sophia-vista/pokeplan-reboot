package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanDaysTest {
    private fun day(d: Int) = LocalDate(2026, 11, d)

    @Test
    fun theWeekendAndNextWeekCountFromToday() {
        assertEquals(listOf("Today" to 0, "Tomorrow" to 1, "This weekend" to 5, "Next week" to 7), PlanDays.presets(day(16)))
        assertEquals(listOf("Today" to 0, "Tomorrow" to 1, "This weekend" to 2, "Next week" to 4), PlanDays.presets(day(19)))
    }

    @Test
    fun aKeyThatNamesTheSameDayAsAnEarlierOneIsLeftOut() {
        assertEquals(listOf("Today" to 0, "Tomorrow" to 1, "Next week" to 3), PlanDays.presets(day(20)))
        assertEquals(listOf("Today" to 0, "Tomorrow" to 1, "Next week" to 2), PlanDays.presets(day(21)))
        assertEquals(listOf("Today" to 0, "Tomorrow" to 1), PlanDays.presets(day(22)))
    }

    @Test
    fun theWeekRunsFromItsFirstDay() {
        assertEquals(DayOfWeek.SUNDAY, PlanDays.week(DayOfWeek.SUNDAY).first())
        assertEquals(DayOfWeek.SATURDAY, PlanDays.week(DayOfWeek.SUNDAY).last())
        assertEquals(DayOfWeek.SUNDAY, PlanDays.week(DayOfWeek.MONDAY).last())
    }

    @Test
    fun repeatsReadInWords() {
        val monday = day(16)
        assertEquals("daily", Repeat.Daily().words(monday))
        assertEquals("on weekdays", Repeat.Weekly(days = DayOfWeek.entries.take(5).toSet()).words(monday))
        assertEquals("weekly", Repeat.Weekly().words(monday))
        assertEquals("weekly on Mon, Thu", Repeat.Weekly(days = setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY)).words(monday))
        assertEquals("every 2 weeks on Mon", Repeat.Weekly(every = 2).words(monday))
        assertEquals("yearly", Repeat.Yearly().words(monday))
    }

    @Test
    fun aMovedOccurrenceIsKnownByTheDayInItsId() {
        val made = Task(id = RecurringTasks.instanceId("s1", day(17)), title = "Bins", seriesId = "s1", planned = day(17), createdAtMillis = 0)
        assertEquals(day(17), made.instanceDay())
        assertFalse(made.movedOccurrence)
        assertTrue(made.copy(planned = day(18)).movedOccurrence)
        assertNull(Task(id = "first", title = "Bins", seriesId = "s1", planned = day(16), createdAtMillis = 0).instanceDay(), "the series' own first task")
    }
}
