package app.pokeplan.core.reward

import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.BaseBucket
import app.pokeplan.core.pokemon.BasePerks
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.pokemon
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseRewardTest {
    private fun homeState(): PlayerState {
        val lead = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.BASE)
        val buddy = pokemon(Ids.EEVEE)
        return PlayerState(pokemon = listOf(lead, buddy), buddyId = buddy.id, baseLeadId = lead.id, trainerPoints = 10)
    }

    private fun event(kind: BaseKind) = BaseEvent("home:${kind.name}:k1", kind, 1_000L, "k1")

    @Test
    fun aChoreWarmsOnlyBasesLeadAndPaysCandiesAndALittleRank() {
        val out = RewardEngine.base(homeState(), event(BaseKind.CHORE), Random(1))
        assertTrue(out.state.baseLead!!.friendship > FriendshipRules.START)
        assertEquals(FriendshipRules.START, out.state.buddy!!.friendship, "the global buddy is never warmed by a module")
        assertTrue(out.rewards.any { it is Reward.Candies && it.amount >= RewardEngine.HOME_CANDIES.getValue(BaseKind.CHORE) })
        assertEquals(10 + RewardEngine.HOME_POINTS, out.state.trainerPoints)
    }

    @Test
    fun maintenancePaysMoreThanAChore() {
        assertTrue(RewardEngine.HOME_CANDIES.getValue(BaseKind.MAINTENANCE) > RewardEngine.HOME_CANDIES.getValue(BaseKind.CHORE))
    }

    @Test
    fun withNoLeadStillPaysCandiesAndWarmsNobody() {
        val out = RewardEngine.base(PlayerState(), event(BaseKind.MAINTENANCE), Random(1))
        assertTrue(out.rewards.any { it is Reward.Candies })
        assertTrue(out.rewards.none { it is Reward.Friendship })
    }

    @Test
    fun theEventCarriesNothingButTheContract() {
        val text = Json.encodeToString(BaseEvent.serializer(), event(BaseKind.CHORE))
        assertEquals("""{"id":"home:CHORE:k1","kind":"chore_completed","nowMillis":1000,"ref":"k1","module":"home"}""", text)
        val review = Json.encodeToString(BaseEvent.serializer(), event(BaseKind.MAINTENANCE))
        assertTrue("maintenance_completed" in review)
    }

    @Test
    fun homePerksComeFromBasesPartyOnlyAndAreCapped() {
        val keeper = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.BASE)
        assertEquals(setOf(BaseBucket.FRIENDSHIP_GAIN), BasePerks.typePerks(keeper).keys)
        assertTrue(BasePerks.total(null).isEmpty())
        val crowd = List(6) { pokemon(Ids.PIKACHU) }
        assertTrue(BasePerks.total(crowd.first(), crowd).values.all { it <= 25.0 })
    }

    @Test
    fun aHabitatEggJoinsBasesPartyAndLeadsItWhenItHasNoLead() {
        val out = RewardEngine.homeEgg(PlayerState(), "egg1", 1_000L, Random(2))
        val p = out.state.pokemon.single()
        assertEquals(PartyIds.BASE, p.partyId)
        assertTrue(p.inParty)
        assertEquals(p.id, out.state.baseLeadId)
        assertTrue(out.rewards.first() is Reward.Hatched)
    }

    @Test
    fun theHomeRegionHasEveryRarityToHatch() {
        for (r in Rarity.entries) assertTrue(HatchRules.pool(Region.HOUSEHOLD, r).isNotEmpty(), "no $r in the Home Region")
    }

    @Test
    fun theTutorialEggLeadsBasesPartyAndAddsNoMeter() {
        val out = RewardEngine.tutorialEgg(PlayerState(), Region.HOUSEHOLD, PartyIds.BASE, "t1", 1_000L, Random(3))
        assertEquals("t1", out.state.baseLead!!.id)
        assertEquals(0, out.state.trainerPoints)
        assertNull(out.state.careLeadId)
    }
}
