package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanP2Test {
    // Monday 19 October 2026, 10:00.
    private val now = LocalDateTime(2026, 10, 19, 10, 0)
    private val today = now.date

    private fun t(id: String, planned: LocalDate? = null, due: LocalDateTime? = null, rank: Rank = Rank.C, estimate: Int? = null, time: LocalTime? = null, created: Long = 0, evening: Boolean = false) =
        Task(id = id, title = id, planned = planned, due = due, rank = rank, estimateMinutes = estimate, plannedTime = time, createdAtMillis = created, evening = evening)

    @Test fun leftoversAreTheOverdueOnesOldestFirstAndSkipTasksOtherModulesOwn() {
        val owned = t("chore", planned = LocalDate(2026, 10, 10)).copy(link = TaskLink(LinkKind.HOME_CHORE, "c1"))
        val found = OutstandingReview.leftovers(
            listOf(t("new", planned = LocalDate(2026, 10, 18)), t("old", planned = LocalDate(2026, 10, 12)), owned, t("today", planned = today), t("late", due = LocalDateTime(2026, 10, 19, 9, 0))),
            now,
        )
        assertEquals(listOf("old", "new", "late"), found.map { it.id })
    }

    @Test fun nextWeekIsTheFirstDayOfTheNextWeek() {
        assertEquals(LocalDate(2026, 10, 26), OutstandingReview.nextWeek(today, DayOfWeek.MONDAY))
        assertEquals(LocalDate(2026, 10, 25), OutstandingReview.nextWeek(today, DayOfWeek.SUNDAY))
        assertEquals(LocalDate(2026, 10, 26), OutstandingReview.nextWeek(LocalDate(2026, 10, 25), DayOfWeek.MONDAY))
    }

    @Test fun movingATaskMovesItsPastDeadlineWithIt() {
        val task = t("x", planned = LocalDate(2026, 10, 15), due = LocalDateTime(2026, 10, 16, 17, 0))
        val tomorrow = OutstandingReview.reschedule(task, OutstandingReview.Choice.TOMORROW, now, DayOfWeek.MONDAY)!!
        assertEquals(LocalDate(2026, 10, 20), tomorrow.planned)
        assertEquals(LocalDateTime(2026, 10, 20, 17, 0), tomorrow.due)
        // Today, with the time of day already gone: the end of the day, so it is not "from before" again at once.
        val early = t("y", planned = LocalDate(2026, 10, 15), due = LocalDateTime(2026, 10, 16, 9, 0))
        assertEquals(LocalDateTime(2026, 10, 19, 23, 59), OutstandingReview.reschedule(early, OutstandingReview.Choice.TODAY, now, DayOfWeek.MONDAY)!!.due)
        assertTrue(OutstandingReview.leftovers(listOf(OutstandingReview.reschedule(early, OutstandingReview.Choice.TODAY, now, DayOfWeek.MONDAY)!!), now).isEmpty())
    }

    @Test fun noDateClearsTheDayTheTimeAndTheDeadlineAndWontDoIsLeftToTheStore() {
        val task = t("x", planned = LocalDate(2026, 10, 15), due = LocalDateTime(2026, 10, 16, 17, 0), time = LocalTime(9, 0))
        val none = OutstandingReview.reschedule(task, OutstandingReview.Choice.NO_DATE, now, DayOfWeek.MONDAY)!!
        assertNull(none.planned); assertNull(none.plannedTime); assertNull(none.due)
        assertNull(OutstandingReview.reschedule(task, OutstandingReview.Choice.WONT_DO, now, DayOfWeek.MONDAY))
    }

    @Test fun suggestionsFillTheGapsAfterNowAndNeverOverlapWhatIsPlanned() {
        val busy = listOf(10 * 60 until 11 * 60) // 10:00 to 11:00
        val s = PlanSuggestions.suggest(listOf(t("a", estimate = 60), t("b", estimate = 30)), busy, today, now)
        assertEquals(2, s.size)
        s.forEach { assertTrue(it.startMinute >= 10 * 60 + 15, "not before the next quarter hour from now") }
        s.forEach { x -> assertTrue(busy.none { it.first < x.startMinute + x.minutes && it.last >= x.startMinute }, "$x clashes") }
        val (first, second) = s
        assertTrue(second.startMinute >= first.startMinute + first.minutes + PlanSuggestions.BUFFER)
    }

    @Test fun aDeadlineTodayComesFirstAndAnEveningTaskWaitsForTheEvening() {
        val s = PlanSuggestions.suggest(
            listOf(t("later", rank = Rank.A), t("urgent", due = LocalDateTime(2026, 10, 19, 15, 0)), t("night", evening = true)), emptyList(), today, now,
        )
        assertEquals(listOf("urgent", "later", "night"), s.map { it.taskId })
        assertTrue(s.first { it.taskId == "night" }.startMinute >= 18 * 60)
    }

    @Test fun aTaskThatDoesNotFitAnywhereIsLeftOutAndScheduledOnesAreNotSuggested() {
        val full = listOf(PlanSuggestions.DAY_START until PlanSuggestions.DAY_END)
        assertTrue(PlanSuggestions.suggest(listOf(t("a")), full, today.let { LocalDate(2026, 10, 21) }, now).isEmpty())
        assertTrue(PlanSuggestions.suggest(listOf(t("timed", time = LocalTime(9, 0))), emptyList(), today, now).isEmpty())
        assertTrue(PlanSuggestions.suggest(listOf(t("huge", estimate = 13 * 60)), emptyList(), today, now).isEmpty())
    }

    @Test fun aTemplateKeepsTheWorkAndNoDates() {
        val task = t("Weekly report", planned = today, due = now, estimate = 45, rank = Rank.B)
            .copy(notes = "Send to Sam", listId = "work", subtasks = listOf(Subtask("s1", "Gather numbers", done = true, createdAtMillis = 0)))
        val tpl = TaskTemplates.from(task, "tp1")
        assertEquals("Weekly report", tpl.name)
        assertEquals(listOf("Gather numbers"), tpl.steps)
        assertEquals(45, tpl.estimateMinutes); assertEquals("work", tpl.listId); assertEquals(Rank.B, tpl.rank)
    }

    @Test fun savingATemplateAgainUpdatesItAndTheListIsCapped() {
        val a = TaskTemplate("1", "Report", "Report", notes = "v1")
        val again = TaskTemplate("2", "report", "Report", notes = "v2")
        val all = TaskTemplates.saved(listOf(a), again)
        assertEquals(1, all.size); assertEquals("1", all[0].id); assertEquals("v2", all[0].notes)
        val full = (1..TaskTemplates.MAX).map { TaskTemplate("$it", "n$it", "t") }
        assertEquals(TaskTemplates.MAX, TaskTemplates.saved(full, TaskTemplate("x", "new", "t")).size)
        assertNotNull(TaskTemplates.saved(full, TaskTemplate("x", "N1", "t")).firstOrNull { it.name == "N1" || it.name == "n1" })
    }
}
