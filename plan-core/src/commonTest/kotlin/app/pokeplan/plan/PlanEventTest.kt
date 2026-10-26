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

    private fun d(n: Int) = day.plus(DatePeriod(days = n))

    @Test
    fun a_weekly_event_shows_once_a_week_each_with_its_own_id() {
        val gym = PlanEvent("gym", "Gym", day, LocalTime(18, 0), repeat = EventRepeat.WEEKLY)
        val shown = gym.occurrences(d(-7), d(20))
        assertEquals(listOf(day, d(7), d(14)), shown.map { it.day }, "from its first day on, every Monday")
        assertEquals(PlanEvent.occurrenceId("gym", d(7)), shown[1].id)
        assertEquals("gym", PlanEvent.seriesOf(shown[1].id))
        assertEquals(d(7), PlanEvent.occurrenceDate(shown[1].id))
        assertEquals(null, PlanEvent.occurrenceDate("gym"))
        assertEquals(LocalDateTime(d(14), LocalTime(19, 0)), shown[2].toCalendarEvent().end)
        assertTrue(gym.isOn(d(70)) && !gym.isOn(d(71)))
    }

    @Test
    fun a_repeating_event_stops_after_until_and_leaves_out_skipped_days() {
        val standup = PlanEvent("s", "Stand-up", day, LocalTime(9, 0), 15, repeat = EventRepeat.WEEKDAYS, until = d(11), skipped = setOf(d(2)))
        assertEquals(listOf(day, d(1), d(3), d(4), d(7), d(8), d(9), d(10), d(11)), standup.occurrences(day, d(30)).map { it.day })
        val fortnight = PlanEvent("f", "Review", day, LocalTime(9, 0), repeat = EventRepeat.FORTNIGHTLY)
        assertEquals(listOf(day, d(14), d(28)), fortnight.startDates(day, d(30)))
    }

    @Test
    fun a_repeating_all_day_event_keeps_its_length_and_shows_when_it_started_before_the_range() {
        val saturday = d(5)
        val home = PlanEvent("home", "Home", saturday, LocalTime(0, 0), allDay = true, lastDay = d(6), repeat = EventRepeat.WEEKLY)
        val sunday = home.occurrences(d(13), d(13)).single()
        assertEquals(d(12) to d(13), sunday.day to sunday.endDay, "the weekend that began on Saturday")
        assertTrue(home.isOn(d(13)) && !home.isOn(d(14)))
        val plan = DayTimeline.build(emptyList(), d(13), now, events = home.occurrences(d(13), d(13)).map { it.toCalendarEvent() })
        assertEquals(0, plan.busyMinutes)
    }

    @Test
    fun monthly_keeps_the_date_and_falls_on_the_last_day_in_short_months() {
        val rent = PlanEvent("rent", "Rent", LocalDate(2027, 1, 31), LocalTime(8, 0), repeat = EventRepeat.MONTHLY)
        assertEquals(
            listOf(LocalDate(2027, 1, 31), LocalDate(2027, 2, 28), LocalDate(2027, 3, 31)),
            rent.startDates(LocalDate(2027, 1, 1), LocalDate(2027, 3, 31)),
        )
        val birthday = PlanEvent("b", "Birthday", day, LocalTime(0, 0), allDay = true, repeat = EventRepeat.YEARLY)
        assertEquals(listOf(day, LocalDate(2027, 10, 19)), birthday.startDates(day, LocalDate(2027, 12, 31)))
    }
}
