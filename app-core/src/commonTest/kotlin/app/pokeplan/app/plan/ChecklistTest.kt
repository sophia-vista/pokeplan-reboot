package app.pokeplan.app.plan

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.plan.ChecklistReset
import app.pokeplan.plan.ListKind
import app.pokeplan.plan.TaskState
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChecklistTest {
    private var millis = 1_760_000_000_000L
    private val s = GameStore(MemoryStorage(), object : Clock { override fun nowMillis() = millis }).also { it.start("A", 25) }

    private fun checklist(reset: ChecklistReset): String {
        val list = assertNotNull(s.addList("Groceries", kind = ListKind.CHECKLIST))
        s.updateList(s.data.lists.first { it.id == list.id }.copy(reset = reset))
        listOf("Milk", "Eggs", "Bread").forEach { s.addTask(it, listId = list.id) }
        return list.id
    }

    @Test
    fun aTemplateStartsANewChecklistWithTheSameItemsUnticked() {
        val id = checklist(ChecklistReset.NEVER)
        s.saveTemplate(id)
        assertEquals(listOf("Milk", "Eggs", "Bread"), s.data.lists.first { it.id == id }.template)
        val copy = assertNotNull(s.addList("Next shop", startFrom = id))
        assertEquals(ListKind.CHECKLIST, copy.kind)
        assertEquals(listOf("Milk", "Eggs", "Bread"), s.data.tasks.filter { it.listId == copy.id }.map { it.title })
        assertTrue(s.data.tasks.filter { it.listId == copy.id }.all { it.state == TaskState.OPEN })
    }

    @Test
    fun aDailyChecklistReopensItsTicksOnTheNextDayAndNeverPaysTwice() {
        val id = checklist(ChecklistReset.DAILY)
        val milk = s.data.tasks.first { it.title == "Milk" }
        s.updateTask(milk.copy(state = TaskState.DONE, completedAtMillis = millis, paid = true))
        s.refresh()
        assertEquals(TaskState.DONE, s.data.tasks.first { it.id == milk.id }.state, "same day: no reset")
        millis += 26 * 3_600_000L
        s.refresh()
        val after = s.data.tasks.first { it.id == milk.id }
        assertEquals(TaskState.OPEN, after.state)
        assertTrue(after.paid, "reopening keeps the paid mark, so ticking again pays nothing")
        s.refresh()
        assertEquals(s.today, s.data.lists.first { it.id == id }.resetOn)
    }

    @Test
    fun aNeverResettingChecklistKeepsItsTicks() {
        checklist(ChecklistReset.NEVER)
        val milk = s.data.tasks.first { it.title == "Milk" }
        s.updateTask(milk.copy(state = TaskState.DONE, completedAtMillis = millis))
        millis += 10 * 24 * 3_600_000L
        s.refresh()
        assertEquals(TaskState.DONE, s.data.tasks.first { it.id == milk.id }.state)
    }

    @Test
    fun aNudgeShowsOnTodayFromItsTimeAndIsNeverOverdue() {
        val t = s.addTask("Call the bank")
        val at = LocalDateTime(s.today, kotlinx.datetime.LocalTime((s.now.hour + 1) % 24, 0))
        s.updateTask(t.copy(nudge = s.now.let { LocalDateTime(it.date, it.time).let { n -> LocalDateTime(n.date, kotlinx.datetime.LocalTime(n.hour, 0)) } }))
        assertTrue(s.todayTasks().any { it.id == t.id })
        assertTrue(app.pokeplan.plan.TaskViews.overdue(s.data.tasks, s.now).none { it.id == t.id })
        s.updateTask(t.copy(nudge = LocalDateTime(s.today.let { kotlinx.datetime.LocalDate(it.year + 1, 1, 1) }, kotlinx.datetime.LocalTime(9, 0))))
        assertTrue(s.todayTasks().none { it.id == t.id }, "a nudge in the future is not on Today yet")
        assertTrue(at.date == s.today)
    }
}
