package app.pokeplan.core.reward

import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.HomeBucket
import app.pokeplan.core.pokemon.HomePerks
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.pokemon
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeRewardTest {
    private fun homeState(): PlayerState {
        val lead = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.HOME)
        val buddy = pokemon(Ids.EEVEE)
        return PlayerState(pokemon = listOf(lead, buddy), buddyId = buddy.id, homeLeadId = lead.id, trainerPoints = 10)
    }

    private fun event(kind: HomeKind) = HomeEvent("home:${kind.name}:k1", kind, 1_000L, "k1")

    @Test
    fun aChoreWarmsOnlyHomesLeadAndPaysCandiesAndALittleRank() {
        val out = RewardEngine.home(homeState(), event(HomeKind.CHORE), Random(1))
        assertTrue(out.state.homeLead!!.friendship > FriendshipRules.START)
        assertEquals(FriendshipRules.START, out.state.buddy!!.friendship, "the global buddy is never warmed by a module")
        assertTrue(out.rewards.any { it is Reward.Candies && it.amount >= RewardEngine.HOME_CANDIES.getValue(HomeKind.CHORE) })
        assertEquals(10 + RewardEngine.HOME_POINTS, out.state.trainerPoints)
    }

    @Test
    fun maintenancePaysMoreThanAChore() {
        assertTrue(RewardEngine.HOME_CANDIES.getValue(HomeKind.MAINTENANCE) > RewardEngine.HOME_CANDIES.getValue(HomeKind.CHORE))
    }

    @Test
    fun withNoLeadStillPaysCandiesAndWarmsNobody() {
        val out = RewardEngine.home(PlayerState(), event(HomeKind.MAINTENANCE), Random(1))
        assertTrue(out.rewards.any { it is Reward.Candies })
        assertTrue(out.rewards.none { it is Reward.Friendship })
    }

    @Test
    fun theEventCarriesNothingButTheContract() {
        val text = Json.encodeToString(HomeEvent.serializer(), event(HomeKind.CHORE))
        assertEquals("""{"id":"home:CHORE:k1","kind":"chore_completed","nowMillis":1000,"ref":"k1","module":"home"}""", text)
        val review = Json.encodeToString(HomeEvent.serializer(), event(HomeKind.MAINTENANCE))
        assertTrue("maintenance_completed" in review)
    }

    @Test
    fun homePerksComeFromHomesPartyOnlyAndAreCapped() {
        val keeper = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.HOME)
        assertEquals(setOf(HomeBucket.FRIENDSHIP_GAIN), HomePerks.typePerks(keeper).keys)
        assertTrue(HomePerks.total(null).isEmpty())
        val crowd = List(6) { pokemon(Ids.PIKACHU) }
        assertTrue(HomePerks.total(crowd.first(), crowd).values.all { it <= 25.0 })
    }

    @Test
    fun aHabitatEggJoinsHomesPartyAndLeadsItWhenItHasNoLead() {
        val out = RewardEngine.homeEgg(PlayerState(), "egg1", 1_000L, Random(2))
        val p = out.state.pokemon.single()
        assertEquals(PartyIds.HOME, p.partyId)
        assertTrue(p.inParty)
        assertEquals(p.id, out.state.homeLeadId)
        assertTrue(out.rewards.first() is Reward.Hatched)
    }

    @Test
    fun theHomeRegionHasEveryRarityToHatch() {
        for (r in Rarity.entries) assertTrue(HatchRules.pool(Region.HOUSEHOLD, r).isNotEmpty(), "no $r in the Home Region")
    }

    @Test
    fun theTutorialEggLeadsHomesPartyAndAddsNoMeter() {
        val out = RewardEngine.tutorialEgg(PlayerState(), Region.HOUSEHOLD, PartyIds.HOME, "t1", 1_000L, Random(3))
        assertEquals("t1", out.state.homeLead!!.id)
        assertEquals(0, out.state.trainerPoints)
        assertNull(out.state.careLeadId)
    }
}
