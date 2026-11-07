package app.pokeplan.core.reward

import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.dex.Region
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.LinkBucket
import app.pokeplan.core.pokemon.LinkPerks
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.pokemon
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkRewardTest {
    private fun linkState(): PlayerState {
        val lead = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.LINK)
        val buddy = pokemon(Ids.EEVEE)
        return PlayerState(pokemon = listOf(lead, buddy), buddyId = buddy.id, linkLeadId = lead.id, trainerPoints = 10)
    }

    private fun event() = LinkEvent("link:followup:k1", LinkMoment.FOLLOW_UP, 1_000L, "k1")

    @Test
    fun aFinishedFollowUpWarmsOnlyLinksLeadAndPaysCandiesAndALittleRank() {
        val out = RewardEngine.link(linkState(), event(), Random(1))
        assertTrue(out.state.linkLead!!.friendship > FriendshipRules.START)
        assertEquals(FriendshipRules.START, out.state.buddy!!.friendship, "the global buddy is never warmed by a module")
        assertTrue(out.rewards.any { it is Reward.Candies && it.amount >= RewardEngine.LINK_CANDIES.getValue(LinkMoment.FOLLOW_UP) })
        assertEquals(10 + RewardEngine.LINK_POINTS, out.state.trainerPoints)
    }

    @Test
    fun withNoLeadStillPaysCandiesAndWarmsNobody() {
        val out = RewardEngine.link(PlayerState(), event(), Random(1))
        assertTrue(out.rewards.any { it is Reward.Candies })
        assertTrue(out.rewards.none { it is Reward.Friendship })
    }

    @Test
    fun theEventCarriesNothingButTheContract() {
        val text = Json.encodeToString(LinkEvent.serializer(), event())
        assertEquals("""{"id":"link:followup:k1","kind":"followup_completed","nowMillis":1000,"ref":"k1","policy":1,"module":"link"}""", text)
    }

    @Test
    fun threeFollowUpsFillTheTrack() {
        assertEquals(3, RewardEngine.LINK_EGG_METER_SIZE / RewardEngine.LINK_EGG_POINTS.getValue(LinkMoment.FOLLOW_UP))
    }

    @Test
    fun linkPerksComeFromLinksPartyOnlyAndAreCapped() {
        val keeper = pokemon(Ids.PIKACHU).copy(inParty = true, partyId = PartyIds.LINK)
        assertEquals(setOf(LinkBucket.FRIENDSHIP_GAIN), LinkPerks.typePerks(keeper).keys)
        assertTrue(LinkPerks.total(null).isEmpty())
        val crowd = List(6) { pokemon(Ids.PIKACHU) }
        assertTrue(LinkPerks.total(crowd.first(), crowd).values.all { it <= 25.0 })
    }

    @Test
    fun aPostcardEggJoinsLinksPartyAndLeadsItWhenItHasNoLead() {
        val out = RewardEngine.linkEgg(PlayerState(), "egg1", 1_000L, Random(2))
        val p = out.state.pokemon.single()
        assertEquals(PartyIds.LINK, p.partyId)
        assertTrue(p.inParty)
        assertEquals(p.id, out.state.linkLeadId)
        assertTrue(out.rewards.first() is Reward.Hatched)
    }

    @Test
    fun aNewcomerDoesNotTakeTheLeadOfAPartyThatHasOne() {
        val first = RewardEngine.linkEgg(PlayerState(), "egg1", 1_000L, Random(2)).state
        assertEquals("egg1", RewardEngine.linkEgg(first, "egg2", 2_000L, Random(3)).state.linkLeadId)
    }

    @Test
    fun theLinkRegionHasEveryRarityToHatch() {
        for (r in Rarity.entries) assertTrue(HatchRules.pool(Region.LINK, r).isNotEmpty(), "no $r in the Link Region")
    }

    @Test
    fun theTutorialEggLeadsLinksPartyAndAddsNoMeter() {
        val out = RewardEngine.tutorialEgg(PlayerState(), Region.LINK, PartyIds.LINK, "t1", 1_000L, Random(3))
        assertEquals("t1", out.state.linkLead!!.id)
        assertEquals(0, out.state.trainerPoints)
        assertNull(out.state.careLeadId)
    }
}
