package app.pokeplan.app

import app.pokeplan.plan.MonthlyRule
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Recurrence
import app.pokeplan.plan.ReminderOffset
import app.pokeplan.plan.Repeat
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** REP-057: a repeating task's next occurrence carries its reminder, and gets made without the app being opened daily. */
class RecurringReminderTest {
    private class FakeClock(var millis: Long) : Clock {
        override fun nowMillis() = millis
        fun advanceDays(n: Int) { millis += n * 86_400_000L }
    }

    private val utc = TimeZone.UTC
    private val start = LocalDateTime(2026, 10, 19, 9, 0).toInstant(utc).toEpochMilliseconds()

    private fun started(): Pair<GameStore, FakeClock> {
        val clock = FakeClock(start)
        return GameStore(MemoryStorage(), clock, utc, Random(7)).also { it.start("Sophia", 25) } to clock
    }

    @Test
    fun completingARepeatingTaskLeavesTheNextOneWithItsReminder() {
        val (s, _) = started()
        val task = s.addTask("Pay rent", Rank.C, due = LocalDateTime(2026, 10, 19, 17, 0))
        s.setReminders(task.id, listOf(ReminderOffset.AtTime))
        assertTrue(s.makeRepeating(task.id, Repeat.Daily()))
        s.completeTask(task.id)
        val tomorrow = LocalDateTime(2026, 10, 20, 17, 0).toEpochMillis(utc)
        val next = s.data.tasks.first { it.seriesId != null && it.planned == s.today.let { d -> kotlinx.datetime.LocalDate(2026, 10, 20) } }
        assertEquals(listOf(ReminderOffset.AtTime), next.reminders)
        assertTrue(tomorrow in s.upcomingReminders().map { it.atMillis }, "tomorrow's instance reminds at its own time")
    }

    @Test
    fun aMonthlyBillGetsItsReminderOnceRefreshHasRunWithinTheHorizon() {
        val (s, clock) = started()
        val task = s.addTask("Pay rent", Rank.C, due = LocalDateTime(2026, 10, 19, 17, 0))
        s.setReminders(task.id, listOf(ReminderOffset.AtTime))
        assertTrue(s.makeRepeating(task.id, Repeat.Monthly(1, MonthlyRule.DayOfMonth(19))))
        val november = LocalDateTime(2026, 11, 19, 17, 0).toEpochMillis(utc)
        assertTrue(november !in s.upcomingReminders().map { it.atMillis }, "19 November is beyond the 14 day horizon on 19 October")
        clock.advanceDays(10)
        s.refresh()
        assertTrue(november in s.upcomingReminders().map { it.atMillis }, "after refresh() the instance and its reminder exist")
    }
}
