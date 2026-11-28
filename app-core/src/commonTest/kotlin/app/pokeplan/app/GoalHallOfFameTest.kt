package app.pokeplan.app

import app.pokeplan.app.testing.FakeClock
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.Reward
import app.pokeplan.app.storage.Clock
import app.pokeplan.app.storage.MemoryStorage
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalHallOfFameTest {

    @Test
    fun finishingAGoalKeepsThePlanPartyLeadFirst() {
        val clock = FakeClock()
        val s = GameStore(MemoryStorage(), clock, TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }
        val egg = assertNotNull(s.completeTutorial(Tutorials.PLAN_FOCUS)).filterIsInstance<Reward.Hatched>().single().pokemon
        assertEquals(egg.id, s.data.player.planLead?.id)
        s.addGoal("Learn guitar")
        val goal = s.data.goals.single()
        assertTrue(goal.hallOfFameParty.isEmpty() && goal.completedAtMillis == null)
        s.addMilestone(goal.id, "First chord")
        val milestone = s.data.goals.single().milestones.single()
        clock.millis += 2 * 24 * 60 * 60 * 1000L
        s.completeMilestone(goal.id, milestone.id)
        val done = s.data.goals.single()
        assertNotNull(done.completedAtMillis)
        assertEquals(listOf(egg.form.id), done.hallOfFameParty.map { it.formId })
        assertEquals(egg.level, done.hallOfFameParty.single().level)
        assertTrue(s.data.player.pokemon.filter { it.partyId == PartyIds.PLAN && it.inParty }.size == 1)
    }
}
