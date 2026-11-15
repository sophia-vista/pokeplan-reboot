package app.pokeplan.app.plan

import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.plan.Rank
import app.pokeplan.plan.TaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DuplicateTaskTest {
    private val s = GameStore(MemoryStorage(), object : Clock { override fun nowMillis() = 1_760_000_000_000L }).also { it.start("A", 25) }

    @Test
    fun aCopyKeepsTheWordsAndStartsFresh() {
        val list = assertNotNull(s.addList("Chores"))
        val original = s.addTask("Water plants", rank = Rank.B, planned = s.today, estimateMinutes = 15, notes = "Front room first", listId = list.id)
        s.addSubtask(original.id, "Kitchen")
        s.addSubtask(original.id, "Balcony")
        val firstStep = s.data.tasks.first { it.id == original.id }.subtasks.first().id
        s.completeSubtask(original.id, firstStep)
        val before = s.data.tasks.size

        val copy = assertNotNull(s.duplicateTask(original.id))

        assertEquals(before + 1, s.data.tasks.size)
        assertNotEquals(original.id, copy.id)
        assertEquals("Water plants", copy.title)
        assertEquals("Front room first", copy.notes)
        assertEquals(Rank.B, copy.rank)
        assertEquals(list.id, copy.listId)
        assertEquals(s.today, copy.planned)
        assertEquals(15, copy.estimateMinutes)
        assertEquals(listOf("Kitchen", "Balcony"), copy.subtasks.map { it.title })
        assertTrue(copy.subtasks.none { it.done || it.paid })
        assertEquals(original.subtasks.size + 2, copy.subtasks.size)
        assertTrue(copy.subtasks.map { it.id }.toSet().intersect(s.data.tasks.first { it.id == original.id }.subtasks.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun aCopyOfAFinishedOrRepeatingTaskIsOpenAndStandsAlone() {
        val done = s.addTask("Pay rent", planned = s.today)
        s.completeTask(done.id)
        val pending = s.data.pending.size
        val copy = assertNotNull(s.duplicateTask(done.id))
        assertEquals(TaskState.OPEN, copy.state)
        assertNull(copy.completedAtMillis)
        assertFalse(copy.paid)
        assertNull(copy.seriesId)
        assertEquals(pending, s.data.pending.size, "copying pays nothing")
    }

    @Test
    fun aDeletedTaskHasNothingToCopy() {
        val t = s.addTask("Gone")
        s.deleteTask(t.id)
        assertNull(s.duplicateTask(t.id))
        assertNull(s.duplicateTask("missing"))
    }
}
