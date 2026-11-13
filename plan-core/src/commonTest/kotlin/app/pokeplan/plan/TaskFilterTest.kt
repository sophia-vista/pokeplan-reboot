package app.pokeplan.plan

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskFilterTest {
    private fun t(
        id: String, title: String = id, notes: String = "", category: Category = Category.PERSONAL, listId: String? = null,
        rank: Rank = Rank.C, state: TaskState = TaskState.OPEN, planned: LocalDate? = null, completedAt: Long? = null,
    ) = Task(id = id, title = title, notes = notes, category = category, listId = listId, rank = rank, state = state, planned = planned, completedAtMillis = completedAt, createdAtMillis = 0)

    private val tasks = listOf(
        t("a", "Buy milk", category = Category.HOME, listId = "groceries", rank = Rank.E),
        t("b", "Essay draft", "chapter two about milk prices", category = Category.SCHOOL, rank = Rank.A),
        t("c", "Run", category = Category.HEALTH, state = TaskState.DONE, completedAt = 5),
        t("d", "Old run", category = Category.HEALTH, state = TaskState.DONE, completedAt = 9),
        t("e", "Gone", state = TaskState.DELETED),
        t("f", "Skipped", state = TaskState.WONT_DO, completedAt = 7),
    )

    private fun ids(f: TaskFilter) = f.apply(tasks).map { it.id }

    @Test
    fun showsOpenTasksByDefaultAndNeverDeletedOnes() {
        assertEquals(listOf("a", "b"), ids(TaskFilter()))
        assertEquals(listOf("a", "b", "d", "f", "c"), ids(TaskFilter(status = TaskFilter.Status.ANY)))
    }

    @Test
    fun searchLooksInTitleAndNotesCaseInsensitivelyWithEveryWord() {
        assertEquals(listOf("a", "b"), ids(TaskFilter(query = "MILK")))
        assertEquals(listOf("b"), ids(TaskFilter(query = "prices  milk")))
        assertEquals(emptyList(), ids(TaskFilter(query = "milk run")))
    }

    @Test
    fun filtersByCategoryListRankAndStatus() {
        assertEquals(listOf("b"), ids(TaskFilter(category = Category.SCHOOL)))
        assertEquals(listOf("a"), ids(TaskFilter(listId = "groceries")))
        assertEquals(listOf("b"), ids(TaskFilter(listId = TaskFilter.INBOX)))
        assertEquals(listOf("b"), ids(TaskFilter(rank = Rank.A)))
        assertEquals(listOf("d", "f", "c"), ids(TaskFilter(status = TaskFilter.Status.FINISHED)))
        assertEquals(listOf("d", "c"), ids(TaskFilter(status = TaskFilter.Status.FINISHED, category = Category.HEALTH)))
    }

    @Test
    fun datedTasksComeBeforeUndatedOnes() {
        val list = listOf(t("x"), t("y", planned = LocalDate(2026, 10, 20)), t("z", planned = LocalDate(2026, 10, 19)))
        assertEquals(listOf("z", "y", "x"), TaskFilter().apply(list).map { it.id })
    }

    @Test
    fun isNarrowedOnlyWhenSomethingBeyondTheDefaultIsChosen() {
        assertFalse(TaskFilter().isNarrowed)
        assertTrue(TaskFilter(query = "x").isNarrowed)
        assertTrue(TaskFilter(status = TaskFilter.Status.ANY).isNarrowed)
    }
}
