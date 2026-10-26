package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanEventTest {
    private val day = LocalDate(2026, 10, 19)
    private val now = LocalDateTime(day, LocalTime(7, 0))

    @Test
    fun an_own_event_sits_on_the_timeline_like_a_phone_event_and_counts_as_busy() {
        val dentist = PlanEvent("dentist", "Dentist", day, LocalTime(9, 30), 45).toCalendarEvent()
        assertTrue(dentist.isOwn)
        assertEquals(LocalDateTime(day, LocalTime(10, 15)), dentist.end)
        val plan = DayTimeline.build(
            listOf(Task(id = "report", title = "Report", planned = day, plannedTime = LocalTime(9, 0), estimateMinutes = 60, createdAtMillis = 0)),
            day, now, events = listOf(dentist),
        )
        assertEquals(listOf("dentist"), plan.events.map { it.event.id })
        assertEquals(1 to 2, plan.events.single().let { it.lane to it.lanes }, "it shares the width with the task")
        assertEquals(45, plan.busyMinutes)
    }

    @Test
    fun an_event_never_runs_past_midnight() {
        val late = PlanEvent("late", "Late show", day, LocalTime(23, 0), 120).toCalendarEvent()
        assertEquals(LocalDateTime(day.plus(DatePeriod(days = 1)), LocalTime(0, 0)), late.end)
        assertEquals(60, PlanEvent.fit(23 * 60, 120))
        assertEquals(15, PlanEvent.fit(9 * 60, 0), "at least 15 minutes")
        assertFalse(CalendarEvent("x", 3, "Phone", now, now).isOwn)
    }

    @Test
    fun an_all_day_event_covers_its_days_in_the_band_and_is_never_busy() {
        val home = PlanEvent("home", "Home", day, LocalTime(0, 0), allDay = true, lastDay = day.plus(DatePeriod(days = 2)), color = TaskList.COLORS[0])
        val e = home.toCalendarEvent()
        assertTrue(e.allDay && e.isOwn)
        assertTrue((0..2).all { e.isOn(day.plus(DatePeriod(days = it))) })
        assertFalse(e.isOn(day.plus(DatePeriod(days = 3))))
        assertTrue(home.isOn(day.plus(DatePeriod(days = 2))) && !home.isOn(day.plus(DatePeriod(days = 3))))
        val plan = DayTimeline.build(emptyList(), day.plus(DatePeriod(days = 1)), now, events = listOf(e))
        assertEquals(listOf("home"), plan.allDay.map { it.id })
        assertEquals(0, plan.busyMinutes)
    }
}
