package app.pokeplan.plan

import app.pokeplan.core.pokemon.Items
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalTest {
    private val day = 24L * 60 * 60 * 1000
    private fun goal(vararg milestoneAges: Long): Goal = Goal(
        id = "g", title = "Learn guitar", createdAtMillis = 0,
        milestones = milestoneAges.mapIndexed { i, created -> Milestone("m$i", "Step $i", createdAtMillis = created) },
    )

    @Test
    fun aMilestoneMustBeADayOldToPay() {
        val g = goal(0, 0)
        assertFalse(GoalRules.completeMilestone(g, "m0", day - 1, 0).pays)
        assertTrue(GoalRules.completeMilestone(g, "m0", day, 0).pays)
    }

    @Test
    fun milestoneRewardsStopAfterThreeADay() {
        val g = goal(0, 0, 0, 0)
        assertTrue(GoalRules.completeMilestone(g, "m2", day, rewardedToday = 2).pays)
        assertFalse(GoalRules.completeMilestone(g, "m3", day, rewardedToday = 3).pays)
    }

    @Test
    fun finishingTheLastMilestoneCompletesTheGoalWithABonusOnce() {
        val g = goal(0, 0)
        val first = GoalRules.completeMilestone(g, "m0", day, 0)
        assertFalse(first.goalCompleted)
        assertNull(first.bonusItem)
        val last = GoalRules.completeMilestone(first.goal, "m1", day + 1, 1)
        assertTrue(last.goalCompleted)
        assertEquals(Items.NATURE_MINT, last.bonusItem)
        assertEquals(day + 1, last.goal.completedAtMillis)
        // reopen and finish again: no second bonus, no second reward
        val reopened = GoalRules.reopenMilestone(last.goal, "m1")
        assertNull(reopened.completedAtMillis)
        val again = GoalRules.completeMilestone(reopened, "m1", day + 2, 0)
        assertTrue(again.goalCompleted)
        assertNull(again.bonusItem)
        assertFalse(again.pays)
    }

    @Test
    fun addingAMilestoneReopensACompletedGoal() {
        val done = GoalRules.completeMilestone(goal(0), "m0", day, 0).goal
        val more = GoalRules.addMilestone(done, Milestone("new", "One more", createdAtMillis = day))
        assertNull(more.completedAtMillis)
        assertEquals(0.5, GoalRules.progress(more, emptyList()))
    }

    @Test
    fun progressUsesMilestonesOrLinkedTasks() {
        assertEquals(0.0, GoalRules.progress(goal(), emptyList()))
        val tasks = listOf(
            Task(id = "a", title = "a", state = TaskState.DONE, createdAtMillis = 0),
            Task(id = "b", title = "b", createdAtMillis = 0),
            Task(id = "c", title = "c", state = TaskState.WONT_DO, createdAtMillis = 0),
        )
        val linked = Goal(id = "g", title = "Run a 10k", createdAtMillis = 0, linkedTaskIds = setOf("a", "b", "c"))
        assertEquals(0.5, GoalRules.progress(linked, tasks), "won't-do tasks do not count")
    }

    @Test
    fun doingAMilestoneTwiceIsRefused() {
        val g = GoalRules.completeMilestone(goal(0), "m0", day, 0).goal
        assertFailsWith<IllegalStateException> { GoalRules.completeMilestone(g, "m0", day, 0) }
    }
}
