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

    @Test
    fun theBadgeCaseHasOneSlotPerMilestoneFilledWhenDone() {
        val g = GoalRules.completeMilestone(goal(0, 0, 0), "m1", day, 0).goal
        val case = GoalRules.badgeCase(g)
        assertEquals(listOf(false, true, false), case.map { it.earned })
        val set = GoalRules.badgeSet("g")
        assertEquals(listOf(1, 2, 3).map { set * 8 + it }, case.map { it.badgeId })
    }

    @Test
    fun badgesRepeatAfterEightAndStayInTheirSet() {
        val ids = (0..9).map { GoalRules.badgeId("some-goal", it) }
        assertEquals(ids[0], ids[8])
        assertTrue(ids.all { it in 1..GoalRules.BADGE_SETS * GoalRules.BADGES_PER_SET })
        assertEquals(1, ids.map { (it - 1) / 8 }.toSet().size, "one set for one goal")
        assertEquals(GoalRules.badgeSet("some-goal"), GoalRules.badgeSet("some-goal"))
    }

    @Test
    fun aGoalWithNoMilestonesHasOneSlot() {
        assertEquals(1, GoalRules.badgeCase(goal()).size)
    }

    @Test
    fun theHallOfFameKeepsThePartyOfTheDayTheGoalFinished() {
        val first = listOf(GoalPartyMember(25, 12), GoalPartyMember(4, 30, shiny = true))
        val finished = GoalRules.completeMilestone(goal(0), "m0", day, 0, first).goal
        assertEquals(first, finished.hallOfFameParty)
        // Reopening and finishing again with a different party records the later day's party.
        val reopened = GoalRules.reopenMilestone(finished, "m0")
        val second = listOf(GoalPartyMember(1, 5))
        assertEquals(second, GoalRules.completeMilestone(reopened, "m0", 2 * day, 0, second).goal.hallOfFameParty)
    }

    @Test
    fun hallOfFameListsFinishedGoalsNewestFirst() {
        val a = GoalRules.completeMilestone(goal(0), "m0", day, 0).goal.copy(id = "a")
        val b = GoalRules.completeMilestone(goal(0), "m0", 3 * day, 0).goal.copy(id = "b")
        val open = goal(0).copy(id = "c")
        assertEquals(listOf("b", "a"), GoalRules.hallOfFame(listOf(a, open, b)).map { it.id })
    }
}
