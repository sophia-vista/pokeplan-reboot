package app.pokeplan.core.reward

import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.pokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
