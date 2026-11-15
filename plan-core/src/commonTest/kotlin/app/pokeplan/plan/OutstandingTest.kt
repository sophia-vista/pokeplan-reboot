package app.pokeplan.plan

import kotlin.test.Test
import kotlin.test.assertEquals

class OutstandingTest {
    private fun t(id: String, list: String? = null, cat: Category = Category.WORK, state: TaskState = TaskState.OPEN) =
        Task(id = id, title = id, createdAtMillis = 1, listId = list, category = cat, state = state)

    private val tasks = listOf(t("a", "L2"), t("b"), t("c", "L1"), t("d", "L2", state = TaskState.DONE), t("e", "gone"), t("f", "L1", Category.SCHOOL))

    @Test
    fun groupsOpenTasksByListWithInboxFirstThenSavedOrder() {
        val g = TaskViews.outstanding(tasks, listOf("L1", "L2"))
        assertEquals(listOf(null, "L1", "L2"), g.map { it.listId })
        assertEquals(listOf(listOf("b", "e"), listOf("c", "f"), listOf("a")), g.map { grp -> grp.tasks.map { it.id }.sorted() })
    }

    @Test
    fun filtersByListAndCategoryAndDropsEmptyGroups() {
        assertEquals(listOf("L1"), TaskViews.outstanding(tasks, listOf("L1", "L2"), TaskFilter(listId = "L1")).map { it.listId })
        val study = TaskViews.outstanding(tasks, listOf("L1", "L2"), TaskFilter(category = Category.SCHOOL))
        assertEquals(listOf("f"), study.single().tasks.map { it.id })
        assertEquals(listOf(null), TaskViews.outstanding(tasks, listOf("L1", "L2"), TaskFilter(listId = TaskFilter.INBOX, status = TaskFilter.Status.FINISHED)).map { it.listId })
    }
}
