package app.pokeplan.core.reward

import app.pokeplan.core.pokemon.CareBucket
import app.pokeplan.core.pokemon.CarePerks
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.core.pokemon.PartyIds
import app.pokeplan.core.pokemon.Perks
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.pokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CareRewardTest {
    private fun state(nature: Nature = Nature.HARDY, friendship: Int = FriendshipRules.START): PlayerState {
        val buddy = pokemon(Ids.PIKACHU, nature = nature, friendship = friendship)
        return PlayerState(pokemon = listOf(buddy), buddyId = buddy.id, trainerPoints = 10)
    }

    private fun care(kind: CareKind, s: PlayerState = state()) = RewardEngine.care(s, CareEvent(kind, 1_000L), Random(1))

    @Test
    fun aRoutineGrowsTheBondAndALittleRank() {
        val out = care(CareKind.ROUTINE, state(Nature.BOLD))
        assertEquals(FriendshipRules.START + 2, out.state.buddy!!.friendship, "a comfort-loving buddy doubles a routine's friendship")
        assertEquals(listOf<Reward>(Reward.Friendship(2)), out.rewards)
        assertEquals(10 + RewardEngine.CARE_POINTS, out.state.trainerPoints)
    }

    private fun withCareLead(lead: OwnedPokemon): PlayerState {
        val base = state()
        val l = lead.copy(inParty = true, partyId = PartyIds.CARE)
        return base.copy(pokemon = base.pokemon + l, careLeadId = l.id)
    }

    @Test
    fun theCareLeadTakesCaresFriendshipInsteadOfThePlanBuddy() {
        val lead = pokemon(Ids.EEVEE, friendship = FriendshipRules.START)
        val s = withCareLead(lead)
        val out = care(CareKind.ROUTINE, s)
        assertTrue(out.state.pokemon.first { it.id == lead.id }.friendship > FriendshipRules.START)
        assertEquals(FriendshipRules.START, out.state.buddy!!.friendship)
    }

    @Test
    fun careBucketsComeFromTheirTypesAndStayCapped() {
        val water = CarePerks.typePerks(pokemon(7))
        assertTrue((water[CareBucket.FRIENDSHIP_GAIN] ?: 0.0) > 0.0)
        val big = pokemon(7, level = 100, friendship = FriendshipRules.MAX)
        val total = CarePerks.total(big, List(6) { big.copy(id = "x$it") })
        assertTrue(total.values.all { it <= Perks.BUCKET_CAP })
        assertTrue(CarePerks.total(null).isEmpty())
    }

    @Test
    fun aCalmBreakGivesFriendshipAndABerry() {
        val out = care(CareKind.CALM_BREAK, state(Nature.HASTY))
        assertEquals(FriendshipRules.START + 2, out.state.buddy!!.friendship, "a presence-loving buddy doubles a calm break")
        val berry = out.rewards.filterIsInstance<Reward.ItemDrop>().single()
        assertTrue(berry.itemId.startsWith("berry-"))
        assertEquals(1, out.state.inventory[berry.itemId])
    }

    @Test
    fun aPractisedSkillGivesFriendshipAndABerry() {
        val out = care(CareKind.SKILL, state(Nature.HASTY))
        assertEquals(FriendshipRules.START + 2, out.state.buddy!!.friendship, "a presence-loving buddy doubles a practised skill")
        assertEquals(1, out.rewards.filterIsInstance<Reward.ItemDrop>().size)
        assertEquals(10 + RewardEngine.CARE_POINTS, out.state.trainerPoints)
    }

    @Test
    fun aWeeklyRhythmGivesBerriesAndPointsButNoFriendship() {
        val out = care(CareKind.WEEKLY_RHYTHM)
        assertEquals(RewardEngine.RHYTHM_BERRIES, out.rewards.filterIsInstance<Reward.ItemDrop>().size)
        assertEquals(FriendshipRules.START, out.state.buddy!!.friendship)
        assertEquals(10 + RewardEngine.RHYTHM_POINTS, out.state.trainerPoints)
    }

    @Test
    fun careNeverPaysCandiesEggsOrCoins() {
        for (kind in CareKind.entries) {
            val out = care(kind)
            assertTrue(out.rewards.none { it is Reward.Candies || it is Reward.Hatched || it is Reward.Coins }, "$kind")
            assertEquals(1, out.state.pokemon.size)
        }
    }

    @Test
    fun noFriendshipRewardAtTheMaximum() {
        assertTrue(care(CareKind.ROUTINE, state(friendship = FriendshipRules.MAX)).rewards.isEmpty())
    }

    @Test
    fun theEventJsonCarriesOnlyTheKindAndTime() {
        val text = RewardJson.json.encodeToString(CareEvent.serializer(), CareEvent(CareKind.CALM_BREAK, 5L))
        assertEquals("""{"kind":"calm_break_completed","nowMillis":5}""", text)
    }

    private fun befriend(s: PlayerState, seed: Int = 1) =
        RewardEngine.befriend(s, BefriendEvent(Ids.EEVEE, app.pokeplan.core.dex.Dex.defaultForm(Ids.EEVEE).identifier, "friend", 5_000L), Random(seed))

    @Test
    fun aBefriendedVisitorHatchesIntoCaresPartyWithAnIvFloor() {
        val out = befriend(state())
        val friend = out.state.pokemon.first { it.id == "friend" }
        assertEquals(Ids.EEVEE, friend.speciesId)
        assertEquals(PartyIds.CARE, friend.partyId)
        assertTrue(friend.inParty)
        assertEquals(friend.id, out.state.careLeadId, "the first member of Care's party leads")
        assertTrue(app.pokeplan.core.pokemon.Stat.entries.all { friend.ivs[it] >= RewardEngine.BEFRIEND_IV_FLOOR })
        assertEquals(10 + RewardEngine.BEFRIEND_POINTS, out.state.trainerPoints)
        assertTrue(Ids.EEVEE in out.state.caught)
        assertEquals(listOf(Reward.Hatched(friend, fromTask = false)), out.rewards)
    }

    @Test
    fun aFullCarePartySendsTheFriendToThePcAndKeepsItsLead() {
        val full = (1..6).map { pokemon(Ids.PIKACHU).copy(id = "c$it", inParty = true, partyId = PartyIds.CARE) }
        val s = state().let { it.copy(pokemon = it.pokemon + full, careLeadId = "c3") }
        val out = befriend(s)
        assertFalse(out.state.pokemon.first { it.id == "friend" }.inParty)
        assertEquals("c3", out.state.careLeadId)
    }

    @Test
    fun directJoinsAreRareAndStartWithHighFriendship() {
        val joins = (1..400).count { befriend(state(), it).state.pokemon.first { p -> p.id == "friend" }.friendship == RewardEngine.BEFRIEND_JOIN_FRIENDSHIP }
        assertTrue(joins in 20..70, "joined $joins of 400, about 10%")
    }

    @Test
    fun usuallyAFriendLeavesAnEggOfItsFamilysFirstStageAndRarelyJoinsItself() {
        val pikachu = app.pokeplan.core.dex.Dex.defaultForm(Ids.PIKACHU).identifier
        val pichu = app.pokeplan.core.dex.Dex.lineRoot(Ids.PIKACHU)
        assertTrue(pichu != Ids.PIKACHU)
        val friends = (1..400).map { seed ->
            RewardEngine.befriend(state(), BefriendEvent(Ids.PIKACHU, pikachu, "friend", 5_000L), Random(seed)).state.pokemon.first { it.id == "friend" }
        }
        val (joined, eggs) = friends.partition { it.speciesId == Ids.PIKACHU }
        assertTrue(eggs.all { it.speciesId == pichu && it.friendship < RewardEngine.BEFRIEND_JOIN_FRIENDSHIP }, "an egg is its family's first stage")
        assertTrue(joined.all { it.friendship == RewardEngine.BEFRIEND_JOIN_FRIENDSHIP }, "the visitor itself joins with high friendship")
        assertTrue(joined.size in 20..70, "joined ${joined.size} of 400")
        assertTrue((joined + eggs).all { p -> app.pokeplan.core.pokemon.Stat.entries.all { p.ivs[it] >= RewardEngine.BEFRIEND_IV_FLOOR } })
    }
}
