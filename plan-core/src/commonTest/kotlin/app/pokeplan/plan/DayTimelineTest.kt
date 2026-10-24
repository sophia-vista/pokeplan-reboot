package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

class DayTimelineTest {
    private val now = LocalDateTime(2026, 10, 19, 10, 0)
    private val today = now.date
    private val tomorrow = today.plus(DatePeriod(days = 1))

    private fun t(id: String, planned: LocalDate? = null, time: LocalTime? = null, estimate: Int? = null, due: LocalDateTime? = null, state: TaskState = TaskState.OPEN) =
        Task(id = id, title = id, planned = planned, plannedTime = time, estimateMinutes = estimate, due = due, state = state, createdAtMillis = 0)

    @Test
    fun timed_tasks_are_blocks_and_the_rest_of_the_day_waits_in_the_tray() {
        val plan = DayTimeline.build(
            listOf(
                t("report", today, LocalTime(9, 0), 45),
                t("mail", today),
                t("late", today.plus(DatePeriod(days = -1)), LocalTime(9, 0)),
                t("done", today, LocalTime(8, 0), state = TaskState.DONE),
                t("gone", today, LocalTime(11, 0), state = TaskState.DELETED),
                t("idea"),
                t("later", tomorrow, LocalTime(9, 0)),
            ),
            today, now,
        )
        assertEquals(listOf("done", "report"), plan.blocks.map { it.task.id })
        assertEquals(9 * 60 + 45, plan.blocks.last().endMinute)
        assertEquals(8 * 60 + 30, plan.blocks.first().endMinute, "no estimate: 30 minutes")
        assertEquals(setOf("mail", "late"), plan.tray.map { it.id }.toSet(), "today's tray has the overdue tasks too")
        assertEquals(listOf("idea"), plan.inbox.map { it.id })
    }

    @Test
    fun another_days_tray_has_its_untimed_and_due_tasks() {
        val plan = DayTimeline.build(
            listOf(t("planned", tomorrow), t("due", due = LocalDateTime(tomorrow, LocalTime(17, 0))), t("timed", tomorrow, LocalTime(10, 0)), t("late", today.plus(DatePeriod(days = -3)))),
            tomorrow, now,
        )
        assertEquals(setOf("planned", "due"), plan.tray.map { it.id }.toSet())
        assertEquals(listOf("timed"), plan.blocks.map { it.task.id })
    }

    @Test
    fun overlapping_blocks_share_the_width_and_separate_ones_do_not() {
        val plan = DayTimeline.build(
            listOf(
                t("a", today, LocalTime(9, 0), 60),
                t("b", today, LocalTime(9, 30), 60),
                t("c", today, LocalTime(10, 0), 30), // a ends at 10:00, so c takes its lane
                t("d", today, LocalTime(12, 0), 30),
            ),
            today, now,
        )
        val byId = plan.blocks.associateBy { it.task.id }
        assertEquals(0 to 2, byId.getValue("a").let { it.lane to it.lanes })
        assertEquals(1 to 2, byId.getValue("b").let { it.lane to it.lanes })
        assertEquals(0 to 2, byId.getValue("c").let { it.lane to it.lanes })
        assertEquals(0 to 1, byId.getValue("d").let { it.lane to it.lanes })
    }

    @Test
    fun the_hours_cover_waking_time_and_stretch_to_fit() {
        assertEquals(8 to 22, DayTimeline.build(emptyList(), today, now).let { it.firstHour to it.endHour })
        val early = DayTimeline.build(listOf(t("run", today, LocalTime(6, 15)), t("late", today, LocalTime(23, 45), 60)), today, now)
        assertEquals(6 to 24, early.firstHour to early.endHour)
        assertEquals(24 * 60, early.blocks.last().endMinute, "a block stops at midnight")
    }

    private fun e(id: String, start: LocalDateTime, end: LocalDateTime, allDay: Boolean = false) =
        CalendarEvent(id, calendarId = 1, title = id, start = start, end = end, allDay = allDay)

    @Test
    fun calendar_events_share_the_hours_and_take_awake_time() {
        val plan = DayTimeline.build(
            listOf(t("report", today, LocalTime(9, 0), 60)),
            today, now,
            events = listOf(
                e("standup", LocalDateTime(today, LocalTime(9, 30)), LocalDateTime(today, LocalTime(10, 0))),
                e("lunch", LocalDateTime(today, LocalTime(12, 0)), LocalDateTime(today, LocalTime(13, 0))),
                e("overlap", LocalDateTime(today, LocalTime(12, 30)), LocalDateTime(today, LocalTime(13, 30))),
                e("tomorrow", LocalDateTime(tomorrow, LocalTime(9, 0)), LocalDateTime(tomorrow, LocalTime(10, 0))),
                e("holiday", LocalDateTime(today, LocalTime(0, 0)), LocalDateTime(tomorrow, LocalTime(0, 0)), allDay = true),
            ),
        )
        assertEquals(listOf("standup", "lunch", "overlap"), plan.events.map { it.event.id })
        assertEquals(1 to 2, plan.events.first().let { it.lane to it.lanes }, "the event sits beside the task it overlaps")
        assertEquals(0 to 2, plan.blocks.single().let { it.lane to it.lanes })
        assertEquals(listOf("holiday"), plan.allDay.map { it.id })
        assertEquals(30 + 90, plan.busyMinutes, "overlapping events count once")
    }

    @Test
    fun events_crossing_midnight_are_cut_to_the_day() {
        val night = e("flight", LocalDateTime(today, LocalTime(22, 0)), LocalDateTime(tomorrow, LocalTime(6, 0)))
        assertEquals(22 * 60 until 24 * 60, night.span(today))
        assertEquals(0 until 6 * 60, night.span(tomorrow))
        val next = DayTimeline.build(emptyList(), tomorrow, now, events = listOf(night))
        assertEquals(0, next.firstHour, "the hours stretch to show it")
        assertEquals(0, next.busyMinutes, "before waking time is not busy")
        val trip = e("trip", LocalDateTime(today, LocalTime(0, 0)), LocalDateTime(today.plus(DatePeriod(days = 3)), LocalTime(0, 0)), allDay = true)
        assertEquals(listOf(true, true, true, false), (0..3).map { trip.isOn(today.plus(DatePeriod(days = it))) })
    }
}
