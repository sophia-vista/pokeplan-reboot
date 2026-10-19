package app.pokeplan.plan

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsAndReviewTest {
    private val zone = TimeZone.UTC
    private val monday = LocalDate(2026, 10, 19)
    private fun at(date: LocalDate, hour: Int = 12) = LocalDateTime(date, kotlinx.datetime.LocalTime(hour, 0)).toEpochMillis(zone)
    private fun done(id: String, date: LocalDate, category: Category = Category.WORK, estimate: Int? = null, focused: Int = 0) =
        Task(id = id, title = id, category = category, state = TaskState.DONE, completedAtMillis = at(date), createdAtMillis = 0, estimateMinutes = estimate, focusedMinutes = focused)

    @Test
    fun theBalanceWheelCountsTasksAndFocusByCategory() {
        val tasks = listOf(
            done("a", monday, Category.WORK), done("b", monday.plusDays(1), Category.WORK),
            done("c", monday.plusDays(2), Category.HEALTH), done("old", monday.plusDays(-30), Category.HOME),
        )
        val focus = listOf(
            FocusRecord(at(monday), 60, true, taskId = "c"),
            FocusRecord(at(monday), 25, false, taskId = "a"),
            FocusRecord(at(monday), 30, true, taskId = null),
        )
        val wheel = Insights.balanceWheel(tasks, focus, monday, monday.plusDays(6), zone)
        assertEquals(2.0, wheel.score(Category.WORK))
        assertEquals(1.0 + 2.0, wheel.score(Category.HEALTH), "a finished task plus 60 minutes of focus on it")
        assertEquals(1.0, wheel.score(Category.PERSONAL), "focus without a task counts as Personal")
        assertEquals(0.0, wheel.score(Category.HOME))
        assertEquals(1.0, Category.entries.sumOf { wheel.share(it) }, 1e-9)
        assertEquals(0.0, Insights.Wheel(emptyMap(), emptyMap()).share(Category.WORK))
    }

    @Test
    fun weeklyTrendListsTheLastWeeksOldestFirst() {
        val tasks = listOf(done("a", monday), done("b", monday.plusDays(-7)), done("c", monday.plusDays(-6)))
        val focus = listOf(FocusRecord(at(monday), 40, true), FocusRecord(at(monday.plusDays(-7)), 20, true), FocusRecord(at(monday), 99, false))
        val trend = Insights.weeklyTrend(tasks, focus, 3, monday.plusDays(2), zone)
        assertEquals(listOf(monday.plusDays(-14), monday.plusDays(-7), monday), trend.map { it.weekStart })
        assertEquals(listOf(0, 20, 40), trend.map { it.focusMinutes })
        assertEquals(listOf(0, 2, 1), trend.map { it.tasksDone })
    }

    @Test
    fun estimateAccuracyNeedsEnoughTasks() {
        assertNull(Insights.estimateAccuracy(listOf(done("a", monday, estimate = 30, focused = 60))))
        val slow = (1..3).map { done("t$it", monday, estimate = 30, focused = 45) }
        val result = Insights.estimateAccuracy(slow)!!
        assertEquals(1.5, result.averageRatio, 1e-9)
        assertEquals(Insights.Bias.UNDERESTIMATES, result.bias)
        assertEquals(Insights.Bias.OVERESTIMATES, Insights.estimateAccuracy((1..3).map { done("t$it", monday, estimate = 60, focused = 30) })!!.bias)
        assertEquals(Insights.Bias.ABOUT_RIGHT, Insights.estimateAccuracy((1..3).map { done("t$it", monday, estimate = 30, focused = 31) })!!.bias)
    }

    @Test
    fun theEstimateLine() {
        assertEquals("You guessed 20 min; it took 35 min.", Insights.estimateLine(done("a", monday, estimate = 20, focused = 35)))
        assertNull(Insights.estimateLine(done("b", monday, estimate = 20)))
        assertNull(Insights.estimateLine(done("c", monday, focused = 10)))
    }

    @Test
    fun theReviewSummarisesTheWeek() {
        val open = Task(id = "open", title = "open", planned = monday.plusDays(3), createdAtMillis = 0)
        val later = Task(id = "later", title = "later", planned = monday.plusDays(20), createdAtMillis = 0)
        val tasks = listOf(done("a", monday), done("b", monday.plusDays(4), Category.HEALTH), done("old", monday.plusDays(-1)), open, later)
        val focus = listOf(FocusRecord(at(monday), 25, true), FocusRecord(at(monday), 10, false), FocusRecord(at(monday.plusDays(8)), 50, true))
        val habit = Habit(id = "h", title = "Read", target = HabitTarget.Daily, createdOn = monday.plusDays(-30), completions = setOf(monday, monday.plusDays(1), monday.plusDays(9)))
        val s = Reviews.summary(monday, tasks, focus, listOf(habit), zone)
        assertEquals(2, s.tasksDone)
        assertEquals(25, s.focusMinutes)
        assertEquals(1, s.sessionsCompleted)
        assertEquals(1, s.sessionsFailed)
        assertEquals(listOf(Reviews.HabitResult("h", "Read", 2)), s.habits)
        assertEquals(listOf("open"), s.unfinished.map { it.id })
        assertEquals(mapOf(Category.WORK to 1, Category.HEALTH to 1), s.categorySplit)
        assertEquals(monday.plusDays(7), Reviews.carryForward(open, monday.plusDays(7)).planned)
    }

    @Test
    fun oneReviewPerWeekWithAPrivateOptionalNote() {
        val first = Reviews.complete(monday, 1_000, "  Good week  ", emptyList(), emptyList())
        assertEquals("Good week", first.note)
        assertTrue(first.rewarded)
        assertEquals(Reviews.promptFor(monday), first.prompt)
        assertNull(Reviews.complete(monday.plusDays(-7), 1_000, "   ", emptyList(), emptyList()).note)
        assertTrue(Reviews.canComplete(monday, monday.plusDays(6), emptyList()))
        assertTrue(!Reviews.canComplete(monday, monday.plusDays(6), listOf(first)))
        assertTrue(!Reviews.canComplete(monday.plusDays(7), monday, emptyList()), "not before the week starts")
        assertFailsWith<IllegalStateException> { Reviews.complete(monday, 2_000, null, emptyList(), listOf(first)) }
    }

    @Test
    fun theReflectionPromptRotatesWeeklyThroughAFiftyQuestionPool() {
        assertEquals(50, Reviews.prompts.size)
        assertEquals(50, Reviews.prompts.toSet().size)
        assertEquals(Reviews.promptFor(monday), Reviews.promptFor(monday))
        assertNotEquals(Reviews.promptFor(monday), Reviews.promptFor(monday.plusDays(7)))
        assertEquals(Reviews.promptFor(monday), Reviews.promptFor(monday.plusDays(7 * 50)))
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
    }
}
