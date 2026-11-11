package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private fun dates(r: Recurrence, from: LocalDate, to: LocalDate) = r.occurrences(from, to)

    @Test
    fun everyOtherDay() {
        val r = Recurrence(Repeat.Daily(2), d(2026, 10, 1))
        assertEquals(listOf(d(2026, 10, 1), d(2026, 10, 3), d(2026, 10, 5)), dates(r, d(2026, 10, 1), d(2026, 10, 6)))
        assertEquals(listOf(d(2026, 10, 5)), dates(r, d(2026, 10, 4), d(2026, 10, 6)))
    }

    @Test
    fun weeklyOnChosenDays() {
        val r = Recurrence(Repeat.Weekly(1, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), d(2026, 10, 19))
        assertEquals(
            listOf(d(2026, 10, 19), d(2026, 10, 21), d(2026, 10, 23), d(2026, 10, 26)),
            dates(r, d(2026, 10, 19), d(2026, 10, 26)),
        )
        val biweekly = Recurrence(Repeat.Weekly(2, setOf(DayOfWeek.TUESDAY)), d(2026, 10, 20))
        assertEquals(listOf(d(2026, 10, 20), d(2026, 11, 3)), dates(biweekly, d(2026, 10, 20), d(2026, 11, 10)))
        val noDays = Recurrence(Repeat.Weekly(), d(2026, 10, 22)) // Thursday
        assertEquals(listOf(d(2026, 10, 22), d(2026, 10, 29)), dates(noDays, d(2026, 10, 22), d(2026, 10, 31)))
    }

    @Test
    fun monthlyByDateClampsToShortMonths() {
        val r = Recurrence(Repeat.Monthly(1, MonthlyRule.DayOfMonth(31)), d(2026, 1, 31))
        assertEquals(listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30)), dates(r, d(2026, 1, 1), d(2026, 4, 30)))
        val everyThree = Recurrence(Repeat.Monthly(3, MonthlyRule.DayOfMonth(15)), d(2026, 1, 15))
        assertEquals(listOf(d(2026, 1, 15), d(2026, 4, 15), d(2026, 7, 15)), dates(everyThree, d(2026, 1, 1), d(2026, 8, 1)))
    }

    @Test
    fun monthlyByWeekday() {
        val second = Recurrence(Repeat.Monthly(1, MonthlyRule.NthWeekday(2, DayOfWeek.TUESDAY)), d(2026, 10, 1))
        assertEquals(listOf(d(2026, 10, 13), d(2026, 11, 10)), dates(second, d(2026, 10, 1), d(2026, 11, 30)))
        val lastFriday = Recurrence(Repeat.Monthly(1, MonthlyRule.LastWeekday(DayOfWeek.FRIDAY)), d(2026, 10, 1))
        assertEquals(listOf(d(2026, 10, 30), d(2026, 11, 27)), dates(lastFriday, d(2026, 10, 1), d(2026, 11, 30)))
        val lastDay = Recurrence(Repeat.Monthly(1, MonthlyRule.LastDay), d(2028, 1, 31))
        assertEquals(listOf(d(2028, 1, 31), d(2028, 2, 29), d(2028, 3, 31)), dates(lastDay, d(2028, 1, 1), d(2028, 3, 31)))
    }

    @Test
    fun yearlyKeepsLeapDaysSensible() {
        val r = Recurrence(Repeat.Yearly(), d(2024, 2, 29))
        assertEquals(listOf(d(2024, 2, 29), d(2025, 2, 28), d(2026, 2, 28), d(2027, 2, 28), d(2028, 2, 29)), dates(r, d(2024, 1, 1), d(2028, 12, 31)))
        assertEquals(listOf(d(2024, 2, 29), d(2026, 2, 28)), dates(Recurrence(Repeat.Yearly(2), d(2024, 2, 29)), d(2024, 1, 1), d(2027, 1, 1)))
    }

    @Test
    fun endsOnADateOrAfterACount() {
        val until = Recurrence(Repeat.Daily(), d(2026, 10, 1), end = RepeatEnd.On(d(2026, 10, 3)))
        assertEquals(3, dates(until, d(2026, 10, 1), d(2026, 12, 31)).size)
        val count = Recurrence(Repeat.Daily(), d(2026, 10, 1), end = RepeatEnd.After(3))
        assertEquals(listOf(d(2026, 10, 1), d(2026, 10, 2), d(2026, 10, 3)), dates(count, d(2026, 10, 1), d(2026, 12, 31)))
    }

    @Test
    fun skippedDatesAreNotGeneratedButStillCount() {
        val r = Recurrence(Repeat.Daily(), d(2026, 10, 1), end = RepeatEnd.After(3), skipped = setOf(d(2026, 10, 2)))
        assertEquals(listOf(d(2026, 10, 1), d(2026, 10, 3)), dates(r, d(2026, 10, 1), d(2026, 12, 31)))
    }

    @Test
    fun afterCompletionCountsFromTheDayYouFinished() {
        val r = Recurrence(Repeat.Daily(3), d(2026, 10, 1), RepeatMode.AFTER_COMPLETION)
        assertEquals(d(2026, 10, 13), r.nextAfterCompletion(d(2026, 10, 10)))
        assertEquals(d(2026, 10, 24), Recurrence(Repeat.Weekly(2), d(2026, 10, 1), RepeatMode.AFTER_COMPLETION).nextAfterCompletion(d(2026, 10, 10)))
        assertEquals(d(2026, 2, 28), Recurrence(Repeat.Monthly(1, MonthlyRule.DayOfMonth(31)), d(2026, 1, 1), RepeatMode.AFTER_COMPLETION).nextAfterCompletion(d(2026, 1, 31)))
        assertNull(Recurrence(Repeat.Daily(), d(2026, 10, 1), RepeatMode.AFTER_COMPLETION, RepeatEnd.On(d(2026, 10, 20))).nextAfterCompletion(d(2026, 10, 20)))
    }

    @Test
    fun invalidSchedulesAreRejected() {
        assertFailsWith<IllegalArgumentException> { Recurrence(Repeat.Daily(0), d(2026, 1, 1)) }
        assertFailsWith<IllegalArgumentException> { Recurrence(Repeat.Daily(), d(2026, 1, 1), end = RepeatEnd.After(0)) }
    }

    @Test
    fun anInstanceMovedToAnotherDayIsNotCreatedAgain() {
        val template = Task(id = "tpl", title = "Water plants", seriesId = "plants", createdAtMillis = 0)
        val r = Recurrence(Repeat.Daily(), d(2026, 10, 1))
        val today = d(2026, 10, 19)
        val all = RecurringTasks.instancesToCreate(template, r, emptyList(), today, 5)
        // Today's instance is put off to a day far outside the horizon: its own date now has no task planned on it.
        val moved = listOf(all.first().copy(planned = d(2026, 12, 1))) + all.drop(1)
        assertEquals(emptyList(), RecurringTasks.instancesToCreate(template, r, moved, today, 5), "no second task with the id plants-2026-10-19")
    }

    @Test
    fun instancesAreCreatedTwoWeeksAheadWithoutDuplicates() {
        val template = Task(id = "tpl", title = "Water plants", seriesId = "plants", plannedTime = LocalTime(9, 0), createdAtMillis = 0)
        val r = Recurrence(Repeat.Daily(), d(2026, 10, 1))
        val today = d(2026, 10, 19)
        val existing = RecurringTasks.instancesToCreate(template, r, emptyList(), today, 5).take(3)
        val more = RecurringTasks.instancesToCreate(template, r, existing, today, 5)
        assertEquals(15, existing.size + more.size, "today plus 14 days")
        assertTrue(more.none { m -> existing.any { it.id == m.id } })
        val first = existing.first()
        assertEquals("plants-2026-10-19", first.id)
        assertEquals(today, first.planned)
        assertEquals(LocalTime(9, 0), first.plannedTime)
        val noon = kotlinx.datetime.LocalDateTime(2026, 10, 19, 12, 0).toEpochMillis(kotlinx.datetime.TimeZone.UTC)
        assertTrue(TaskRules.paysOnCompletion(first, noon), "recurring instances pay straight away on their day")
        assertFalse(TaskRules.paysOnCompletion(existing[1], noon), "but not ahead of it")
        assertEquals(15, RecurringTasks.instancesToCreate(template, r, listOf(first.copy(seriesId = "other")), today, 5).size + 0 + 0)
    }

    @Test
    fun theTimeOfDayStaysTheSameAcrossDaylightSaving() {
        val ny = TimeZone.of("America/New_York")
        val before = RecurringTasks.momentOf(d(2026, 3, 7), LocalTime(9, 0), ny)
        val after = RecurringTasks.momentOf(d(2026, 3, 9), LocalTime(9, 0), ny)
        assertEquals(47L * 3_600_000, after - before, "the clocks sprang forward, so only 47 real hours apart")
        val gap = RecurringTasks.momentOf(d(2026, 3, 8), LocalTime(2, 30), ny)
        assertEquals(LocalDateTime(2026, 3, 8, 3, 30), gap.toLocalDateTime(ny), "2:30 does not exist; the clock jumps forward")
    }
}
