package app.pokeplan.core.reward

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.dex.Rarity
import app.pokeplan.core.pokemon.FriendshipRules
import app.pokeplan.core.pokemon.Ids
import app.pokeplan.core.pokemon.Inventory
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.Nature
import app.pokeplan.core.pokemon.pokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RewardEngineTest {
    private fun effort(minutes: Int = 25, lure: PokemonType? = null, epochDay: Long = 0, deep: Boolean = false) =
        EffortEvent(minutes, deep, Region.KANTO, lure, epochDay, "new", 1_000L)

    private fun achievement(tier: Int, onTime: Boolean = false, focused: Boolean = false, first: Boolean = false, hour: Int = 12) =
        AchievementEvent(tier, Region.KANTO, hour, first, onTime, focused, "egg", 2_000L)

    private fun stateWithBuddy(speciesId: Int = Ids.PIKACHU, level: Int = 100, friendship: Int = 255, nature: Nature = Nature.HARDY): PlayerState {
        val buddy = pokemon(speciesId, level = level, friendship = friendship, nature = nature)
        return PlayerState(pokemon = listOf(buddy), buddyId = buddy.id)
    }

    @Test
    fun anEffortHatchesAnEggAndPaysCoinsAndABerry() {
        val out = RewardEngine.effort(PlayerState(), effort(25), Random(1))
        val hatched = out.rewards.filterIsInstance<Reward.Hatched>().single()
        assertEquals("new", hatched.pokemon.id)
        assertEquals(false, hatched.fromTask)
        assertEquals(listOf(hatched.pokemon), out.state.pokemon)
        assertEquals(25, out.state.coins, "1 Poké a minute")
        assertEquals(1, out.state.inventory.counts.filterKeys { it.startsWith("berry-") }.values.sum())
    }

    @Test
    fun theSameSeedGivesTheSameOutcome() {
        assertEquals(RewardEngine.effort(PlayerState(), effort(), Random(7)), RewardEngine.effort(PlayerState(), effort(), Random(7)))
    }

    @Test
    fun theBuddyGainsFriendshipFromASession() {
        val before = stateWithBuddy(friendship = 100, nature = Nature.ADAMANT)
        val after = RewardEngine.effort(before, effort(), Random(2)).state
        val buddy = after.buddy!!
        assertEquals(100 + FriendshipRules.amount(FriendshipRules.Source.FOCUS_SESSION, buddy.bondStyle), buddy.friendship)
        assertEquals(2, after.pokemon.size)
    }

    @Test
    fun aLureIsRequiredAndUsedUp() {
        assertFailsWith<IllegalStateException> { RewardEngine.effort(PlayerState(), effort(lure = PokemonType.FIRE), Random(1)) }
        val state = PlayerState(inventory = Inventory().add(Items.lure(PokemonType.FIRE), 2))
        val out = RewardEngine.effort(state, effort(lure = PokemonType.FIRE), Random(1))
        assertEquals(1, out.state.inventory[Items.lure(PokemonType.FIRE)])
    }

    private fun shareOfType(type: PokemonType, trials: Int, seed: Int, event: (Int) -> EffortEvent, state: () -> PlayerState): Double {
        val rng = Random(seed)
        var hits = 0
        repeat(trials) {
            val p = RewardEngine.effort(state(), event(it), rng).rewards.filterIsInstance<Reward.Hatched>().single().pokemon
            if (type in p.form.types) hits++
        }
        return hits.toDouble() / trials
    }

    @Test
    fun aLureBiasesTheHatchTowardItsType() {
        val withLure = shareOfType(PokemonType.WATER, 3000, 3, { effort(lure = PokemonType.WATER) }) { PlayerState(inventory = Inventory().add(Items.lure(PokemonType.WATER))) }
        val without = shareOfType(PokemonType.WATER, 3000, 3, { effort() }) { PlayerState() }
        assertTrue(withLure > without * 1.5, "with lure $withLure, without $without")
    }

    @Test
    fun theSpotlightTypeIsMoreLikely() {
        val day = PokemonType.entries.indexOf(PokemonType.WATER).toLong()
        assertEquals(PokemonType.WATER, Spotlight.typeFor(day))
        assertEquals(PokemonType.WATER, Spotlight.typeFor(day + 18 * 5), "types take turns")
        assertEquals(PokemonType.entries.last(), Spotlight.typeFor(-1))
        val spot = shareOfType(PokemonType.WATER, 3000, 4, { effort(epochDay = day) }) { PlayerState() }
        val other = shareOfType(PokemonType.WATER, 3000, 4, { effort(epochDay = day + 1) }) { PlayerState() }
        assertTrue(spot > other * 1.3, "spotlight $spot, normal $other")
    }

    private fun highRarityShare(state: () -> PlayerState, seed: Int): Double {
        val rng = Random(seed)
        val n = 4000
        val count = (1..n).count {
            val hatched = RewardEngine.effort(state(), effort(10), rng).rewards.filterIsInstance<Reward.Hatched>().single().pokemon
            HatchRules.rarity(hatched.speciesId) in listOf(Rarity.RARE, Rarity.SUPER_RARE)
        }
        return count.toDouble() / n
    }

    @Test
    fun eggLuckPushesRaritiesUp() {
        val snorlax = 143 // Normal: Egg luck
        val lucky = highRarityShare({ stateWithBuddy(snorlax) }, 5)
        val plain = highRarityShare({ stateWithBuddy(Ids.CHARMANDER, level = 1, friendship = 70) }, 5)
        assertTrue(lucky > plain + 0.02, "lucky $lucky, plain $plain")
    }

    @Test
    fun finishedWorkPaysCandiesWithinTheTierRange() {
        val rng = Random(9)
        var bonuses = 0
        repeat(300) {
            val out = RewardEngine.achievement(PlayerState(), achievement(3), rng)
            val candies = out.rewards.filterIsInstance<Reward.Candies>()
            val rates = CandyRewards.rates(3)
            val rare = candies.single { it.type == CandyType.RARE }
            assertTrue(rare.amount in rates.rare, "$candies")
            assertEquals(rare.amount, out.state.inventory[Items.RARE_CANDY])
            val bonus = candies.singleOrNull { it.type == CandyType.SUPER }
            if (bonus != null) {
                bonuses++
                assertTrue(bonus.amount in rates.superCandy, "$candies")
            }
            assertEquals(bonus?.amount ?: 0, out.state.inventory[Items.SUPER_CANDY])
        }
        assertTrue(bonuses in 30..90, "Super Candy bonuses: $bonuses of 300")
    }

    @Test
    fun onTimeAndFocusedBonusesAddUp() {
        fun rareCandies(e: AchievementEvent, seed: Int) =
            RewardEngine.achievement(PlayerState(), e, Random(seed)).rewards.filterIsInstance<Reward.Candies>().first { it.type == CandyType.RARE }.amount // the task's own, before a Task Egg's
        val plain = (1..400).sumOf { i -> rareCandies(achievement(5), i) }
        val both = (1..400).sumOf { i -> rareCandies(achievement(5, onTime = true, focused = true), i) }
        assertTrue(both > plain * 1.1, "plain $plain, both $both")
    }

    @Test
    fun coinsFollowTheTier() {
        assertEquals(30, RewardEngine.achievement(PlayerState(), achievement(3), Random(1)).state.coins)
        assertEquals(50, RewardEngine.achievement(PlayerState(), achievement(5), Random(1)).state.coins)
    }

    @Test
    fun thePickedTaskPaysABitMore() {
        fun candies(picked: Boolean) = (1..300).sumOf { seed ->
            RewardEngine.achievement(PlayerState(), achievement(5).copy(picked = picked), Random(seed)).rewards.filterIsInstance<Reward.Candies>().sumOf { it.amount }
        }
        assertTrue(candies(true) > candies(false))
    }

    @Test
    fun taskFriendshipStopsAtTheDailyCap() {
        val before = stateWithBuddy(friendship = 100)
        val capped = RewardEngine.achievement(before, achievement(2).copy(friendshipTasksToday = RewardEngine.TASK_FRIENDSHIP_DAILY_CAP), Random(3)).state.buddy!!
        assertEquals(100, capped.friendship)
        val warm = RewardEngine.achievement(before, achievement(2).copy(friendshipTasksToday = RewardEngine.TASK_FRIENDSHIP_DAILY_CAP - 1), Random(3)).state.buddy!!
        assertTrue(warm.friendship > 100)
    }

    @Test
    fun finishingWorkWarmsTheBuddy() {
        val before = stateWithBuddy(friendship = 100, nature = Nature.ADAMANT)
        val after = RewardEngine.achievement(before, achievement(2), Random(3)).state.buddy!!
        assertEquals(100 + FriendshipRules.amount(FriendshipRules.Source.TASK, after.bondStyle), after.friendship)
    }

    @Test
    fun caughtSpeciesStayCaughtEvenAfterTheyLeave() {
        val out = RewardEngine.effort(PlayerState(), effort(), Random(1))
        val species = out.rewards.filterIsInstance<Reward.Hatched>().single().pokemon.speciesId
        assertEquals(setOf(species), out.state.caught)
        val gone = out.state.copy(pokemon = emptyList())
        assertEquals(setOf(species), gone.caught, "transferring does not un-catch")
    }

    @Test
    fun speciesYouHaveNotCaughtAreThreeTimesAsLikely() {
        // Catch everything in the Kanto Common tier except one species, then see how often that one still turns up.
        val commons = HatchRules.pool(Region.KANTO, Rarity.COMMON).map { it.speciesId }
        val missing = commons.first()
        val state = PlayerState(caught = commons.drop(1).toSet())
        val rng = Random(21)
        var hits = 0
        var commonEggs = 0
        repeat(6000) {
            val p = RewardEngine.effort(state, effort(10), rng).rewards.filterIsInstance<Reward.Hatched>().single().pokemon
            if (HatchRules.rarity(p.speciesId) == Rarity.COMMON) {
                commonEggs++
                if (p.speciesId == missing) hits++
            }
        }
        // Day 0's spotlight type is also in play, so work the expected share out from the real weights.
        val spotlight = Spotlight.typeFor(0)
        fun weight(id: Int) = (if (spotlight in Dex.defaultForm(id).types) RewardEngine.SPOTLIGHT_WEIGHT else 1.0) *
            (if (id == missing) RewardEngine.UNCAUGHT_WEIGHT else 1.0)
        val expected = weight(missing) / commons.sumOf { weight(it) }
        val share = hits.toDouble() / commonEggs
        assertTrue(share in expected - 0.03..expected + 0.03, "uncaught share $share, expected about $expected")
    }

    @Test
    fun theEggMeterTurnsIntoATaskEgg() {
        var state = PlayerState()
        val first = RewardEngine.achievement(state, achievement(3), Random(1))
        assertEquals(3, first.state.eggMeter)
        assertTrue(first.rewards.none { it is Reward.Hatched })
        val second = RewardEngine.achievement(first.state, achievement(3), Random(2))
        assertEquals(1, second.state.eggMeter, "the rest carries over")
        val egg = second.rewards.filterIsInstance<Reward.Hatched>().single()
        assertTrue(egg.fromTask)
        assertEquals(1, second.state.pokemon.size)
        assertTrue(Dex.regionDex(Region.KANTO).any { it.id == egg.pokemon.speciesId })
        state = second.state
        assertEquals(1, state.eggMeter)
    }

    @Test
    fun itemDropsHappenAboutAsOftenAsTheTable() {
        val rng = Random(11)
        val n = 6000
        val drops = (1..n).count { RewardEngine.achievement(PlayerState(), achievement(5), rng).rewards.any { it is Reward.ItemDrop } }
        val expected = (RewardEngine.DROP_BASE_PERCENT + RewardEngine.DROP_PER_TIER_PERCENT * 5) / 100.0
        assertTrue(drops.toDouble() / n in expected - 0.03..expected + 0.03, "$drops of $n, expected about $expected")
    }

    @Test
    fun everyDroppableItemIsInTheCatalogue() {
        for (id in DropTable.weights.keys) assertTrue(Items.get(id) != null, "no catalogue entry for $id")
        val rng = Random(1)
        repeat(500) { assertTrue(DropTable.roll(rng) in DropTable.weights) }
    }

    @Test
    fun fullStacksOverflowIntoCoins() {
        val state = PlayerState(inventory = Inventory().add(Items.RARE_CANDY, 9_999).add(Items.SUPER_CANDY, 9_999))
        val out = RewardEngine.achievement(state, achievement(3), Random(1))
        val candies = out.rewards.filterIsInstance<Reward.Candies>()
        assertEquals(9_999, out.state.inventory[Items.RARE_CANDY])
        assertEquals(9_999, out.state.inventory[Items.SUPER_CANDY])
        assertEquals(3 * RewardEngine.COINS_PER_TIER + candies.sumOf { it.amount }, out.state.coins)
    }
}
