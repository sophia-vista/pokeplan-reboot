package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NudgesTest {
    private fun task(id: String, state: TaskState = TaskState.OPEN) = Task(id = id, title = id, state = state, createdAtMillis = 0)

    @Test
    fun pickForMeChoosesAnOpenTask() {
        val tasks = listOf(task("a"), task("b"), task("done", TaskState.DONE))
        val rng = Random(1)
        val picks = (1..200).map { PickForMe.choose(tasks, rng)!! }
        assertTrue(picks.all { it.task.id in setOf("a", "b") })
        assertTrue(picks.map { it.task.id }.toSet().size == 2, "both get picked sometimes")
        assertNull(PickForMe.choose(emptyList(), rng))
        assertNull(PickForMe.choose(listOf(task("done", TaskState.DONE)), rng))
    }

    @Test
    fun miniQuestsAreThreeDistinctOnesAndStableForADay() {
        val day = LocalDate(2026, 10, 19)
        val quests = MiniQuests.forDay(day)
        assertEquals(3, quests.size)
        assertEquals(3, quests.map { it.id }.toSet().size)
        assertEquals(quests, MiniQuests.forDay(day))
        assertNotEquals(quests, MiniQuests.forDay(day, rerolled = true))
        assertNotEquals(quests, MiniQuests.forDay(day.plusDays(1)))
    }

    @Test
    fun theQuestPoolIsBigAndHasTheAvoidedTaskQuest() {
        assertTrue(MiniQuests.pool.size >= 30)
        assertEquals(MiniQuests.pool.size, MiniQuests.pool.map { it.id }.toSet().size)
        assertEquals(1, MiniQuests.pool.count { it.kind == MiniQuest.Kind.AVOIDED_TASK })
    }
}
