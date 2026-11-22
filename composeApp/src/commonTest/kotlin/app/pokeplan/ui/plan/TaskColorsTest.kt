package app.pokeplan.ui.plan

import app.pokeplan.plan.TaskList
import kotlin.test.Test
import kotlin.test.assertEquals

/** The screen readers' colour names must stay beside [TaskList.COLORS]: one name per colour, in the same order. */
class TaskColorsTest {
    @Test
    fun thereIsOneNameForEachListColour() {
        assertEquals(TaskList.COLORS.size, taskColorNames.size)
        assertEquals(taskColorNames.size, taskColorNames.distinct().size, "each name is unique")
    }
}
