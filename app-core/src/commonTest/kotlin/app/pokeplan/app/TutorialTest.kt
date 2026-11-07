package app.pokeplan.app

import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.reward.Reward
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Each module's first-run tutorial gives one Pokémon through its own method, once (POKEMON_ECOSYSTEM.md section 10.2.1). */
class TutorialTest {
    private class FakeClock(var millis: Long = 1_793_000_000_000L) : Clock { override fun nowMillis() = millis }

    private fun store(): GameStore = GameStore(MemoryStorage(), FakeClock(), TimeZone.UTC, Random(5)).also { it.start("Sophia", 25) }

    @Test
    fun aNewGameStartsWithTheStarterAsBuddyAndBothTutorialsOffered() {
        val s = store()
        assertEquals(Place.Buddy, s.placeOf(s.data.player.buddyId!!))
        assertTrue(s.data.player.party.isEmpty())
        assertTrue(s.tutorialOffered(Tutorials.PLAN_FOCUS))
        assertTrue(s.tutorialOffered(Tutorials.CARE_BREATH))
        assertFalse(s.tutorialOffered("nothing"))
    }

    @Test
    fun theFocusDemoHatchesOneEggIntoPlansPartyAndLeadsItOnce() {
        val s = store()
        val coins = s.data.player.coins
        val rewards = assertNotNull(s.completeTutorial(Tutorials.PLAN_FOCUS))
        val egg = rewards.filterIsInstance<Reward.Hatched>().single().pokemon
        assertEquals(Place.Party(PartyIds.PLAN), s.placeOf(egg.id))
        assertEquals(egg.id, s.data.player.planLead?.id)
        assertEquals(2, s.data.player.pokemon.size)
        assertEquals(s.data.settings.region.open, s.data.origins[egg.id]?.region)
        // No focus session, streak, meter or history came of it, and it cannot be done again or for pay twice.
        assertTrue(s.data.focusHistory.isEmpty() && s.data.activeDays.isEmpty() && s.data.player.eggMeter == 0)
        assertEquals(coins, s.data.player.coins)
        assertFalse(s.tutorialOffered(Tutorials.PLAN_FOCUS))
        assertNull(s.completeTutorial(Tutorials.PLAN_FOCUS))
        assertEquals(2, s.data.player.pokemon.size)
        // Care's is still on offer: each module teaches its own way.
        assertTrue(s.tutorialOffered(Tutorials.CARE_BREATH))
    }

    @Test
    fun theBreathHatchesACareEggIntoCaresPartyAndLeadsIt() {
        val s = store()
        val egg = assertNotNull(s.completeTutorial(Tutorials.CARE_BREATH)).filterIsInstance<Reward.Hatched>().single().pokemon
        assertEquals(Place.Party(PartyIds.CARE), s.placeOf(egg.id))
        assertEquals(egg.id, s.data.player.careLead?.id)
        assertEquals(0, s.careEggMeter(), "it is not a calm break: nothing fills Care's meter")
        assertNull(s.completeTutorial(Tutorials.CARE_BREATH))
        assertTrue(s.data.player.party.isEmpty(), "Plan's party is unaffected")
    }

    @Test
    fun aPlayerWhoHasDoneTheRealThingIsNotOfferedItAndTheDemoIsUnknownOrNotStartedWithoutAGame() {
        val s = store()
        assertFalse(s.tutorialOffered(Tutorials.CARE_BREATH, careBreaks = 2))
        assertNull(GameStore(MemoryStorage(), FakeClock(), TimeZone.UTC, Random(1)).completeTutorial(Tutorials.PLAN_FOCUS), "nothing before a game has started")
        assertNull(s.completeTutorial("unknown"))
    }

    @Test
    fun theTutorialSurvivesAReloadSoItCannotPayTwice() {
        val storage = MemoryStorage()
        val a = GameStore(storage, FakeClock(), TimeZone.UTC, Random(5)).also { it.start("S", 25); it.completeTutorial(Tutorials.PLAN_FOCUS) }
        val b = GameStore(storage, FakeClock(), TimeZone.UTC, Random(9))
        assertEquals(a.data.player.pokemon.size, b.data.player.pokemon.size)
        assertNull(b.completeTutorial(Tutorials.PLAN_FOCUS))
    }
}
