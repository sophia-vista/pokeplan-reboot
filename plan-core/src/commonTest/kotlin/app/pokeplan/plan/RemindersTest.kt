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
}
