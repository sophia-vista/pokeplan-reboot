package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskRulesTest {
    private val minute = 60_000L
    private val zone = TimeZone.UTC
    private fun task(rank: Rank = Rank.C, created: Long = 0, change: (Task) -> Task = { it }): Task =
        change(Task(id = "t", title = "Task", rank = rank, createdAtMillis = created))

    @Test
    fun minimumAgeScalesWithRank() {
        assertEquals(listOf(2, 5, 10, 15, 20), Rank.entries.map { it.minAgeMinutes })
        for (rank in Rank.entries) {
            val t = task(rank)
            val limit = rank.minAgeMinutes * minute
            assertFalse(TaskRules.paysOnCompletion(t, limit - 1), "$rank just under")
            assertTrue(TaskRules.paysOnCompletion(t, limit), "$rank at the limit")
        }
    }

    @Test
    fun recurringAndTutorialTasksPayStraightAway() {
        assertTrue(TaskRules.paysOnCompletion(task { it.copy(seriesId = "daily") }, 0))
        assertTrue(TaskRules.paysOnCompletion(task { it.copy(tutorial = true) }, 0))
        assertFalse(TaskRules.paysOnCompletion(task(), 0))
    }

    @Test
    fun completingMarksTheTaskAndShowsTheTierAndBonuses() {
        val due = LocalDateTime(2026, 10, 19, 12, 0)
        val t = task(Rank.B) { it.copy(due = due, focusedMinutes = 25) }
        val at = LocalDateTime(2026, 10, 19, 9, 0).let { 1_000_000_000_000L }
        val c = TaskRules.complete(t, 20 * minute, zone)
        assertEquals(TaskState.DONE, c.task.state)
        assertEquals(4, c.tier)
        assertTrue(c.pays && c.focusedOnIt)
        assertEquals(20 * minute + TaskRules.UNDO_WINDOW_MILLIS, c.finalAtMillis)
        assertTrue(at > 0)
    }

    @Test
    fun onTimeMeansBeforeTheDeadlineAndLateStillPays() {
        // 1970-01-01 00:00 UTC is epoch 0
        val early = task { it.copy(due = LocalDateTime(1970, 1, 1, 12, 0)) }
        assertTrue(TaskRules.complete(early, 11 * 60 * minute, zone).onTime)
        val late = TaskRules.complete(early, 13 * 60 * minute, zone)
        assertFalse(late.onTime)
        assertTrue(late.pays, "overdue tasks still pay, only the bonus is lost")
        assertFalse(TaskRules.complete(task(), 20 * minute, zone).onTime, "no deadline, no on-time bonus")
    }

    @Test
    fun undoWorksOnlyInsideTheWindow() {
        val t = task()
        val c = TaskRules.complete(t, 20 * minute, zone)
        assertTrue(TaskRules.canUndo(c, 20 * minute + 9_999))
        assertEquals(t, TaskRules.undo(t, c, 20 * minute + 5_000))
        assertFalse(TaskRules.canUndo(c, 20 * minute + 10_000))
        assertFailsWith<IllegalStateException> { TaskRules.undo(t, c, 20 * minute + 10_000) }
    }

    @Test
    fun reopeningNeverPaysTwice() {
        val first = TaskRules.complete(task(), 20 * minute, zone)
        assertTrue(first.pays)
        val reopened = TaskRules.reopen(first.task)
        val second = TaskRules.complete(reopened, 30 * minute, zone)
        assertFalse(second.pays)
    }

    @Test
    fun aTooYoungTaskCompletesWithoutPayingAndStaysUnpaid() {
        val c = TaskRules.complete(task(Rank.A), 5 * minute, zone)
        assertFalse(c.pays)
        assertFalse(c.task.paid)
        assertTrue(TaskRules.complete(TaskRules.reopen(c.task), 25 * minute, zone).pays, "paid later once it is old enough")
    }

    @Test
    fun wontDoPaysNothingAndDeletedTasksCanBeRestoredForThirtyDays() {
        val w = TaskRules.wontDo(task(), 5)
        assertEquals(TaskState.WONT_DO, w.state)
        assertFailsWith<IllegalStateException> { TaskRules.complete(w, 10, zone) }
        val deleted = TaskRules.delete(task(), 1_000)
        assertTrue(TaskRules.canRestore(deleted, 1_000 + TaskRules.RESTORE_WINDOW_MILLIS - 1))
        assertFalse(TaskRules.canRestore(deleted, 1_000 + TaskRules.RESTORE_WINDOW_MILLIS))
        assertEquals(TaskState.OPEN, TaskRules.restore(deleted, 2_000).state)
    }

    @Test
    fun subtasksPayOneEachUpToFiveAndNeedTheMinimumAge() {
        val subs = (1..7).map { Subtask("s$it", "step $it", createdAtMillis = 0) }
        var t = task(Rank.C) { it.copy(subtasks = subs) }
        val now = 15 * minute
        var paid = 0
        for (s in subs) {
            val (next, pays) = TaskRules.completeSubtask(t, s.id, now)
            t = next
            if (pays) paid++
        }
        assertEquals(TaskRules.MAX_PAID_SUBTASKS, paid)
        assertTrue(t.subtasks.all { it.done })
        val (_, tooYoung) = TaskRules.completeSubtask(task(Rank.C) { it.copy(subtasks = listOf(Subtask("a", "a", createdAtMillis = 10 * minute))) }, "a", 15 * minute)
        assertFalse(tooYoung, "5 minutes old is under the 10 minute minimum for rank C")
    }

    @Test
    fun titlesAndEstimatesAreValidated() {
        assertFailsWith<IllegalArgumentException> { Task(id = "x", title = " ", createdAtMillis = 0) }
        assertFailsWith<IllegalArgumentException> { Task(id = "x", title = "x", estimateMinutes = 0, createdAtMillis = 0) }
        assertEquals('B', Rank.B.letter)
        assertEquals(Rank.A, Rank.fromLetter('a'))
        assertEquals(null, Rank.fromLetter('S'))
        assertEquals(LocalDate(2026, 1, 1), LocalDate(2026, 1, 1))
    }
}
