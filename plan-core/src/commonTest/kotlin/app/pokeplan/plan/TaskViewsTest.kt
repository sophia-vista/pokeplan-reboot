package app.pokeplan.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskViewsTest {
    private val now = LocalDateTime(2026, 10, 19, 10, 0)
    private val today = now.date
    private fun day(offset: Int) = today.plus(DatePeriod(days = offset))

    private fun t(id: String, planned: LocalDate? = null, due: LocalDateTime? = null, time: LocalTime? = null, rank: Rank = Rank.C, state: TaskState = TaskState.OPEN, evening: Boolean = false, estimate: Int? = null) =
        Task(id = id, title = id, planned = planned, due = due, plannedTime = time, rank = rank, state = state, evening = evening, estimateMinutes = estimate, createdAtMillis = 0)

    private val tasks = listOf(
        t("inbox"),
        t("today", planned = today),
        t("dueToday", due = LocalDateTime(2026, 10, 19, 17, 0)),
        t("lateDue", due = LocalDateTime(2026, 10, 18, 9, 0)),
        t("oldPlan", planned = day(-2)),
        t("tomorrow", planned = day(1)),
        t("nextWeek", planned = day(6)),
        t("farAway", planned = day(30)),
        t("done", planned = today, state = TaskState.DONE),
        t("evening", planned = today, time = LocalTime(19, 0)),
    )

    @Test
    fun undatedHoldsOnlyUnscheduledOpenTasks() {
        assertEquals(listOf("inbox"), TaskViews.undated(tasks).map { it.id })
    }

    @Test
    fun theInboxHoldsOpenTasksWithNoListAndNoDate() {
        val filed = tasks.map { if (it.id == "today") it.copy(listId = "l1") else it }
        assertEquals(listOf("inbox"), TaskViews.inbox(filed).map { it.id })
        assertTrue(TaskViews.inbox(filed.map { if (it.id == "inbox") it.copy(listId = "l1") else it }).isEmpty())
        assertEquals(setOf("today"), TaskViews.inList(filed, "l1").map { it.id }.toSet())
    }

    @Test
    fun aListGroupsItsTasksByWhenTheyAre() {
        val groups = TaskViews.byWhen(tasks, now).associate { (w, ts) -> w to ts.map { it.id } }
        assertEquals(TaskViews.When.entries.toList(), groups.keys.toList())
        assertEquals(setOf("lateDue", "oldPlan"), groups.getValue(TaskViews.When.BEFORE).toSet())
        assertEquals(listOf("tomorrow"), groups.getValue(TaskViews.When.TOMORROW))
        assertEquals(listOf("nextWeek"), groups.getValue(TaskViews.When.WEEK))
        assertEquals(listOf("farAway"), groups.getValue(TaskViews.When.LATER))
        assertEquals(listOf("inbox"), groups.getValue(TaskViews.When.NO_DATE))
    }

    @Test
    fun overdueIsPastDueOrPlannedEarlier() {
        assertEquals(setOf("lateDue", "oldPlan"), TaskViews.overdue(tasks, now).map { it.id }.toSet())
        val justNow = LocalDateTime(2026, 10, 19, 17, 1)
        assertTrue("dueToday" in TaskViews.overdue(tasks, justNow).map { it.id })
    }

    @Test
    fun todayHasPlannedDueAndOverdueTasksOverdueFirst() {
        val ids = TaskViews.today(tasks, now).map { it.id }
        assertEquals(setOf("today", "dueToday", "lateDue", "oldPlan", "evening"), ids.toSet())
        assertTrue(ids.indexOf("lateDue") < ids.indexOf("today") && ids.indexOf("oldPlan") < ids.indexOf("today"))
    }

    @Test
    fun theEveningIsSeparate() {
        val (main, evening) = TaskViews.todaySplit(tasks, now)
        assertEquals(listOf("evening"), evening.map { it.id })
        assertTrue("today" in main.map { it.id })
        val flagged = listOf(t("flag", planned = today, evening = true))
        assertEquals(listOf("flag"), TaskViews.todaySplit(flagged, now).second.map { it.id })
    }

    @Test
    fun upcomingLooksAWeekAheadExcludingToday() {
        assertEquals(listOf("tomorrow", "nextWeek"), TaskViews.upcoming(tasks, now, 7).map { it.id })
        assertEquals(listOf("tomorrow", "nextWeek", "farAway"), TaskViews.upcoming(tasks, now).map { it.id })
    }

    @Test
    fun harderRanksComeFirstWithinTheSameTime() {
        val two = listOf(t("easy", planned = today, rank = Rank.E), t("hard", planned = today, rank = Rank.A))
        assertEquals(listOf("hard", "easy"), TaskViews.today(two, now).map { it.id })
    }

    @Test
    fun dayLoadComparesPlannedMinutesWithFreeTime() {
        val light = DayLoad.compute(listOf(t("a", planned = today, estimate = 60), t("b", planned = today)), busyMinutes = 120)
        assertEquals(90, light.plannedMinutes, "an unestimated task counts as 30 minutes")
        assertEquals(14 * 60 - 120, light.freeMinutes)
        assertEquals(DayLoad.Level.CALM, light.level)
        assertEquals(DayLoad.Level.FULL, DayLoad(80, 90).level)
        assertEquals(DayLoad.Level.OVER, DayLoad(120, 90).level)
        assertEquals(DayLoad.Level.OVER, DayLoad(10, 0).level, "no free time at all")
    }
}
