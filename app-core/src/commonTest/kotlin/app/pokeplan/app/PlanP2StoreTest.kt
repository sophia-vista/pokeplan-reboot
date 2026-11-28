package app.pokeplan.app

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.plan.OutstandingReview
import app.pokeplan.plan.Rank
import app.pokeplan.plan.Suggestion
import app.pokeplan.plan.TaskState
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanP2StoreTest {

    private val utc = TimeZone.UTC
    private fun store() = GameStore(MemoryStorage(), FakeClock(LocalDateTime(2026, 10, 19, 9, 0).toInstant(utc).toEpochMilliseconds()), utc, Random(7)).also { it.start("Sophia", 25) }

    @Test fun aTemplateMakesAFreshTaskWithItsStepsAndNoDates() {
        val s = store()
        val list = assertNotNull(s.addList("Work"))
        val src = s.addTask("Weekly report", Rank.B, planned = s.today, due = s.now, estimateMinutes = 45, notes = "Send to Sam", listId = list.id)
        s.addSubtask(src.id, "Gather numbers"); s.addSubtask(src.id, "Write summary")
        val tpl = assertNotNull(s.saveTaskTemplate(src.id))
        assertEquals("Weekly report", tpl.name)

        val made = assertNotNull(s.useTaskTemplate(tpl.id, s.today.plus(DatePeriod(days = 1))))
        assertEquals(listOf("Gather numbers", "Write summary"), made.subtasks.map { it.title })
        assertEquals(45, made.estimateMinutes); assertEquals(list.id, made.listId); assertEquals(Rank.B, made.rank)
        assertNull(made.due); assertEquals(s.today.plus(DatePeriod(days = 1)), made.planned)
        assertTrue(made.id != src.id && made.subtasks.none { it.done })

        s.saveTaskTemplate(src.id)
        assertEquals(1, s.data.taskTemplates.size, "saving the same name again updates it")
        s.deleteTaskTemplate(tpl.id)
        assertTrue(s.data.taskTemplates.isEmpty())
        assertNull(s.useTaskTemplate(tpl.id))
    }

    @Test fun theReviewMovesOrDropsAndThenTheTaskIsNoLongerLeftBehind() {
        val s = store()
        val old = s.addTask("Old", planned = s.today.minus(DatePeriod(days = 3)))
        val drop = s.addTask("Drop", planned = s.today.minus(DatePeriod(days = 2)))
        assertEquals(2, OutstandingReview.leftovers(s.data.tasks, s.now).size)
        s.reviewLeftover(old.id, OutstandingReview.Choice.TOMORROW)
        s.reviewLeftover(drop.id, OutstandingReview.Choice.WONT_DO)
        assertEquals(s.today.plus(DatePeriod(days = 1)), s.data.tasks.first { it.id == old.id }.planned)
        assertEquals(TaskState.WONT_DO, s.data.tasks.first { it.id == drop.id }.state)
        assertTrue(OutstandingReview.leftovers(s.data.tasks, s.now).isEmpty())
    }

    @Test fun acceptingASuggestionSchedulesTheTask() {
        val s = store()
        val t = s.addTask("Plan trip")
        s.acceptSuggestions(listOf(Suggestion(t.id, s.today, LocalTime(14, 0), 30)))
        val saved = s.data.tasks.first { it.id == t.id }
        assertEquals(s.today, saved.planned); assertEquals(LocalTime(14, 0), saved.plannedTime)
    }
}
