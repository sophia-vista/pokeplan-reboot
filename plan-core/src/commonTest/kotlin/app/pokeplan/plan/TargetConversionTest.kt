package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TargetConversionTest {
    private val start = LocalDate(2026, 11, 12)

    private fun repeat(t: HabitTarget) = TargetConversion.toRecurrence(t, start).repeat

    @Test fun `every day and chosen weekdays map exactly`() {
        assertEquals(Repeat.Daily(1), repeat(HabitTarget.Daily))
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)
        assertEquals(Repeat.Weekly(1, days), repeat(HabitTarget.Weekdays(days)))
    }

    @Test fun `n a week is a weekly rule on n days spread across the week`() {
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.MONDAY)), repeat(HabitTarget.PerWeek(1)))
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), repeat(HabitTarget.PerWeek(2)))
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), repeat(HabitTarget.PerWeek(3)))
        assertEquals(Repeat.Daily(1), repeat(HabitTarget.PerWeek(7)))
        assertEquals(5, TargetConversion.spreadDays(5).size, "every count from 1 to 7 keeps its number of days")
        (1..7).forEach { assertEquals(it, TargetConversion.spreadDays(it).size) }
    }

    @Test fun `once a month keeps the day it began on and more is about n over four a week`() {
        assertEquals(Repeat.Monthly(1, MonthlyRule.DayOfMonth(12)), repeat(HabitTarget.PerMonth(1)))
        assertEquals(Repeat.Monthly(1, MonthlyRule.DayOfMonth(28)), TargetConversion.toRecurrence(HabitTarget.PerMonth(1), LocalDate(2026, 1, 31)).repeat, "a day past the 28th stays in every month")
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.MONDAY)), repeat(HabitTarget.PerMonth(4)))
        assertEquals(Repeat.Weekly(1, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)), repeat(HabitTarget.PerMonth(8)))
    }

    @Test fun `the rule starts on the day the target began`() {
        assertEquals(start, TargetConversion.toRecurrence(HabitTarget.Daily, start).start)
    }
}
