package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemindersTest {
    private val zone = TimeZone.UTC
    private val day = LocalDate(2026, 10, 20)
    private fun task(planned: LocalDate? = day, time: LocalTime? = null, due: LocalDateTime? = null) =
        Task(id = "t", title = "t", planned = planned, plannedTime = time, due = due, createdAtMillis = 0)
    private fun at(h: Int, m: Int = 0, date: LocalDate = day) = LocalDateTime(date, LocalTime(h, m))

    @Test
    fun theBaseTimeIsPlannedThenDueThenNineAm() {
        assertEquals(at(17), Reminders.baseTime(task(time = LocalTime(17, 0))))
        assertEquals(at(15, 30), Reminders.baseTime(task(planned = null, due = at(15, 30))))
        assertEquals(at(9), Reminders.baseTime(task()))
        assertNull(Reminders.baseTime(task(planned = null)))
    }

    @Test
    fun relativeRemindersFireBeforeTheTimeInOrder() {
        val times = Reminders.fireTimes(
            task(time = LocalTime(17, 0)),
            listOf(ReminderOffset.Before(10), ReminderOffset.AtTime, ReminderOffset.Before(24 * 60)),
            zone,
        )
        assertEquals(listOf(at(17, 0, LocalDate(2026, 10, 19)), at(16, 50), at(17)), times)
    }

    @Test
    fun quietHoursMoveRemindersToTheMorning() {
        val quiet = QuietHours()
        assertEquals(at(7), Reminders.outOfQuietHours(at(3, 0), quiet))
        assertEquals(at(7, 0, LocalDate(2026, 10, 21)), Reminders.outOfQuietHours(at(23, 30), quiet))
        assertEquals(at(12), Reminders.outOfQuietHours(at(12), quiet))
        assertTrue(quiet.contains(LocalTime(22, 0)) && quiet.contains(LocalTime(6, 59)) && !quiet.contains(LocalTime(7, 0)))
        val early = Reminders.fireTimes(task(time = LocalTime(7, 30)), listOf(ReminderOffset.Before(60)), zone)
        assertEquals(listOf(at(7)), early, "6:30 is in quiet hours, so it waits until 7:00")
        val sameDayWindow = QuietHours(LocalTime(13, 0), LocalTime(15, 0))
        assertEquals(at(15), Reminders.outOfQuietHours(at(14), sameDayWindow))
    }

    @Test
    fun atMostThreeRemindersPerTask() {
        assertFailsWith<IllegalArgumentException> {
            Reminders.fireTimes(task(), List(4) { ReminderOffset.Before(it + 1) }, zone)
        }
    }

    @Test
    fun snoozeAndFocusDelay() {
        val now = at(14, 5)
        assertEquals(at(14, 15), Reminders.snoozedTo(now, Snooze.TEN_MINUTES, zone))
        assertEquals(at(15, 5), Reminders.snoozedTo(now, Snooze.ONE_HOUR, zone))
        assertEquals(at(9, 0, LocalDate(2026, 10, 21)), Reminders.snoozedTo(now, Snooze.TOMORROW, zone))
        assertEquals(at(15), Reminders.delayedByFocus(at(14, 30), at(14), at(15)))
        assertEquals(at(16), Reminders.delayedByFocus(at(16), at(14), at(15)))
    }

    private fun ms(t: LocalDateTime) = t.toEpochMillis(zone)

    @Test
    fun upcomingListsOpenTasksRemindersInOrder() {
        val a = task(time = LocalTime(15, 0)).copy(id = "a", title = "Essay", reminders = listOf(ReminderOffset.AtTime, ReminderOffset.Before(60)))
        val b = task(time = LocalTime(14, 30)).copy(id = "b", title = "Call", reminders = listOf(ReminderOffset.Before(10)))
        val done = task(time = LocalTime(16, 0)).copy(id = "c", reminders = listOf(ReminderOffset.AtTime), state = TaskState.DONE)
        val none = task(time = LocalTime(16, 0)).copy(id = "d")
        val got = Reminders.upcoming(listOf(a, b, done, none), ms(at(13, 0)), zone)
        assertEquals(listOf("a" to at(14), "b" to at(14, 20), "a" to at(15)), got.map { it.taskId to it.atMillis.toLocalDateTime(zone) })
        assertEquals("Essay", got.first().title)
        assertEquals(listOf("b", "a"), Reminders.upcoming(listOf(a, b), ms(at(14, 0)), zone).map { it.taskId }, "past ones are left out")
    }

    @Test
    fun upcomingSkipsRestDaysAndWaitsForFocus() {
        val a = task(time = LocalTime(15, 0)).copy(reminders = listOf(ReminderOffset.AtTime))
        assertEquals(emptyList(), Reminders.upcoming(listOf(a), ms(at(9)), zone, restDays = setOf(day)))
        val during = Reminders.upcoming(listOf(a), ms(at(14, 50)), zone, focusEndMillis = ms(at(15, 25)))
        assertEquals(listOf(ms(at(15, 25))), during.map { it.atMillis })
        val after = Reminders.upcoming(listOf(a), ms(at(14, 50)), zone, focusEndMillis = ms(at(14, 55)))
        assertEquals(listOf(ms(at(15))), after.map { it.atMillis })
    }

    @Test
    fun labelsAndTheLimitOnATask() {
        assertEquals(listOf("At the time", "10 min before", "30 min before", "1 hour before", "2 hours before", "1 day before"), ReminderOffset.PRESETS.map { it.label })
        assertFailsWith<IllegalArgumentException> { task().copy(reminders = List(4) { ReminderOffset.Before(it + 1) }) }
    }
}
