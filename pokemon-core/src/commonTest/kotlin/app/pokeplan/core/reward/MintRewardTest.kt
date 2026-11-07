package app.pokeplan.core.reward

import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.MintBucket
import app.pokeplan.core.pokemon.MintPerks
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.pokemon
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MintRewardTest {
    private fun mintState(): PlayerState {
        val lead = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.MINT)
        val buddy = pokemon(Ids.EEVEE)
        return PlayerState(pokemon = listOf(lead, buddy), buddyId = buddy.id, mintLeadId = lead.id, trainerPoints = 10)
    }

    private fun event(kind: MintKind) = MintEvent("mint:${kind.name}:k1", kind, 1_000L, "k1")

    @Test
    fun aReconciledAccountWarmsOnlyMintsLeadAndPaysCandiesAndALittleRank() {
        val out = RewardEngine.mint(mintState(), event(MintKind.RECONCILED), Random(1))
        assertTrue(out.state.mintLead!!.friendship > FriendshipRules.START)
        assertEquals(FriendshipRules.START, out.state.buddy!!.friendship, "the global buddy is never warmed by a module")
        assertTrue(out.rewards.any { it is Reward.Candies && it.amount >= RewardEngine.MINT_CANDIES.getValue(MintKind.RECONCILED) })
        assertEquals(10 + RewardEngine.MINT_POINTS, out.state.trainerPoints)
    }

    @Test
    fun aReviewPaysMoreThanAReconciliation() {
        assertTrue(RewardEngine.MINT_CANDIES.getValue(MintKind.REVIEW) > RewardEngine.MINT_CANDIES.getValue(MintKind.RECONCILED))
    }

    @Test
    fun withNoLeadStillPaysCandiesAndWarmsNobody() {
        val out = RewardEngine.mint(PlayerState(), event(MintKind.REVIEW), Random(1))
        assertTrue(out.rewards.any { it is Reward.Candies })
        assertTrue(out.rewards.none { it is Reward.Friendship })
    }

    @Test
    fun theEventCarriesNothingButTheContract() {
        val text = Json.encodeToString(MintEvent.serializer(), event(MintKind.RECONCILED))
        assertEquals("""{"id":"mint:RECONCILED:k1","kind":"account_reconciled","nowMillis":1000,"ref":"k1","module":"mint"}""", text)
        val review = Json.encodeToString(MintEvent.serializer(), event(MintKind.REVIEW))
        assertTrue("financial_review_completed" in review)
    }

    @Test
    fun mintPerksComeFromMintsPartyOnlyAndAreCapped() {
        val keeper = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.MINT)
        assertEquals(setOf(MintBucket.CANDY_FINDER), MintPerks.typePerks(keeper).keys)
        assertTrue(MintPerks.total(null).isEmpty())
        val crowd = List(6) { pokemon(Ids.PIKACHU) }
        assertTrue(MintPerks.total(crowd.first(), crowd).values.all { it <= 25.0 })
    }

    @Test
    fun aLedgerEggJoinsMintsPartyAndLeadsItWhenItHasNoLead() {
        val out = RewardEngine.mintEgg(PlayerState(), "egg1", 1_000L, Random(2))
        val p = out.state.pokemon.single()
        assertEquals(PartyIds.MINT, p.partyId)
        assertTrue(p.inParty)
        assertEquals(p.id, out.state.mintLeadId)
        assertTrue(out.rewards.first() is Reward.Hatched)
    }

    @Test
    fun theMintRegionHasEveryRarityToHatch() {
        for (r in Rarity.entries) assertTrue(HatchRules.pool(Region.MINT, r).isNotEmpty(), "no $r in the Mint Region")
    }

    @Test
    fun theTutorialEggLeadsMintsPartyAndAddsNoMeter() {
        val out = RewardEngine.tutorialEgg(PlayerState(), Region.MINT, PartyIds.MINT, "t1", 1_000L, Random(3))
        assertEquals("t1", out.state.mintLead!!.id)
        assertEquals(0, out.state.trainerPoints)
        assertNull(out.state.careLeadId)
    }
}
