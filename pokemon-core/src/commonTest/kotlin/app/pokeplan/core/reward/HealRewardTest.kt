package app.pokeplan.core.reward

import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.HealBucket
import app.pokeplan.core.pokemon.HealPerks
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.pokemon
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealRewardTest {
    private fun healState(): PlayerState {
        val lead = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.HEAL)
        val buddy = pokemon(Ids.EEVEE)
        return PlayerState(pokemon = listOf(lead, buddy), buddyId = buddy.id, healLeadId = lead.id, trainerPoints = 10)
    }

    private fun event(kind: HealKind) = HealEvent("heal:${kind.name}:k1", kind, 1_000L, "k1")

    @Test
    fun anActivityWarmsOnlyHealsLeadAndPaysCandiesABerryAndALittleRank() {
        val s = healState()
        val out = RewardEngine.heal(s, event(HealKind.ACTIVITY), Random(1))
        assertTrue(out.state.healLead!!.friendship > FriendshipRules.START)
        assertEquals(FriendshipRules.START, out.state.buddy!!.friendship, "the global buddy is never warmed by a module")
        assertTrue(out.rewards.any { it == Reward.Candies(CandyType.RARE, RewardEngine.HEAL_CANDIES.getValue(HealKind.ACTIVITY)) })
        assertTrue(out.rewards.any { it is Reward.ItemDrop })
        assertEquals(10 + RewardEngine.HEAL_POINTS, out.state.trainerPoints)
    }

    @Test
    fun participationPaysOnlyAFewCandiesAndWarmsNobody() {
        val out = RewardEngine.heal(healState(), event(HealKind.PARTICIPATION), Random(1))
        assertEquals(listOf<Reward>(Reward.Candies(CandyType.RARE, RewardEngine.HEAL_CANDIES.getValue(HealKind.PARTICIPATION)), Reward.Coins(RewardEngine.MODULE_POKE)), out.rewards)
        assertEquals(FriendshipRules.START, out.state.healLead!!.friendship)
    }

    @Test
    fun withNoLeadStillPaysCandiesAndWarmsNobody() {
        val out = RewardEngine.heal(PlayerState(), event(HealKind.ACTIVITY), Random(1))
        assertTrue(out.rewards.any { it is Reward.Candies })
        assertTrue(out.rewards.none { it is Reward.Friendship })
    }

    @Test
    fun theEventCarriesNothingButTheContract() {
        val text = Json.encodeToString(HealEvent.serializer(), event(HealKind.ACTIVITY))
        assertEquals("""{"id":"heal:ACTIVITY:k1","kind":"activity_logged","nowMillis":1000,"ref":"k1","module":"heal"}""", text)
    }

    @Test
    fun healPerksComeFromHealsPartyOnlyAndAreCapped() {
        val fighter = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.HEAL)
        assertEquals(setOf(HealBucket.FRIENDSHIP_GAIN), HealPerks.typePerks(fighter).keys)
        assertTrue(HealPerks.total(null).isEmpty())
        val crowd = List(6) { pokemon(Ids.PIKACHU) }
        assertTrue(HealPerks.total(crowd.first(), crowd).values.all { it <= 25.0 })
    }

    @Test
    fun aFieldEggJoinsHealsPartyAndLeadsItWhenItHasNoLead() {
        val out = RewardEngine.healEgg(PlayerState(), "egg1", 1_000L, Random(2))
        val p = out.state.pokemon.single()
        assertEquals(PartyIds.HEAL, p.partyId)
        assertTrue(p.inParty)
        assertEquals(p.id, out.state.healLeadId)
        assertTrue(out.rewards.first() is Reward.Hatched)
    }

    /** The reported bug: with no lead ever chosen, the first member is the lead, and a new Pokémon used to take over. A newcomer leads only an empty party. */
    @Test
    fun aNewPokemonNeverTakesTheLeadOfAPartyThatHasOne() {
        val first = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.HEAL)
        val implicit = PlayerState(pokemon = listOf(first)) // healLeadId was never set: the first member leads implicitly
        assertEquals(first.id, implicit.healLead?.id)
        val egg = RewardEngine.healEgg(implicit, "egg1", 1_000L, Random(2)).state
        assertEquals(2, egg.partyOf(PartyIds.HEAL).size)
        assertEquals(first.id, egg.healLead?.id, "the lead did not change when a new Pokémon arrived")
        assertEquals(first.id, egg.healLeadId, "and it is now the stored lead")
        val chosen = pokemon(Ids.EEVEE).copy(inParty = true, partyId = PartyIds.HEAL)
        val withChoice = PlayerState(pokemon = listOf(first, chosen), healLeadId = chosen.id)
        assertEquals(chosen.id, RewardEngine.healEgg(withChoice, "egg2", 1_000L, Random(3)).state.healLead?.id, "a chosen lead stays too")
    }

    @Test
    fun theHealRegionHasEveryRarityToHatch() {
        for (r in Rarity.entries) assertTrue(HatchRules.pool(Region.HEAL, r).isNotEmpty(), "no $r in the Heal Region")
    }

    @Test
    fun theTutorialEggLeadsHealsPartyAndAddsNoMeter() {
        val out = RewardEngine.tutorialEgg(PlayerState(), Region.HEAL, PartyIds.HEAL, "t1", 1_000L, Random(3))
        assertEquals("t1", out.state.healLead!!.id)
        assertEquals(0, out.state.trainerPoints)
        assertNull(out.state.careLeadId)
    }
}
