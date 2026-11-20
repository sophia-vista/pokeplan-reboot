package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The task reward focus (docs/economy.md "Task reward focus"): Exp is the old payout, Egg and Items trade half the candies for something else. */
class RewardFocusTest {
    private fun event(tier: Int, focus: RewardFocus? = null) =
        if (focus == null) AchievementEvent(tier, Region.KANTO, 12, false, false, false, "egg", 2_000L)
        else AchievementEvent(tier, Region.KANTO, 12, false, false, false, "egg", 2_000L, focus = focus)

    /** The task's own candy rolls: the Candies before its Poké (a hatched Task Egg pays "new species" candies after them, at any focus). */
    private fun candies(out: Outcome) = out.rewards.takeWhile { it !is Reward.Coins }.filterIsInstance<Reward.Candies>().map { it.amount }

    @Test
    fun expIsExactlyTheOrdinaryPayout() {
        for (tier in 1..5) for (seed in 1..40) {
            assertEquals(
                RewardEngine.achievement(PlayerState(), event(tier), Random(seed)),
                RewardEngine.achievement(PlayerState(), event(tier, RewardFocus.EXP), Random(seed)),
                "tier $tier seed $seed",
            )
        }
    }

    @Test
    fun anOldEventReadsAsExp() {
        assertEquals(RewardFocus.EXP, event(3).focus)
        assertEquals(RewardFocus.EXP, RewardFocus.DEFAULT)
    }

    @Test
    fun eggAndItemsPayHalfTheCandiesOfEachRoll() {
        for (focus in listOf(RewardFocus.EGG, RewardFocus.ITEMS)) for (tier in 1..5) for (seed in 1..30) {
            val full = candies(RewardEngine.achievement(PlayerState(), event(tier), Random(seed)))
            val half = candies(RewardEngine.achievement(PlayerState(), event(tier, focus), Random(seed)))
            assertEquals(full.map { maxOf(1, it * RewardEngine.FOCUS_CANDY_PERCENT / 100) }, half, "$focus tier $tier seed $seed")
        }
    }

    @Test
    fun eggFillsTheMeterOneAndAHalfTimesFasterRoundedUp() {
        // tier 2: 3 points instead of 2, so no egg yet; the meter carries what it filled.
        assertEquals(2, RewardEngine.achievement(PlayerState(), event(2), Random(1)).state.eggMeter)
        val egg = RewardEngine.achievement(PlayerState(), event(2, RewardFocus.EGG), Random(1))
        assertEquals(3, egg.state.eggMeter)
        assertTrue(egg.rewards.none { it is Reward.Hatched })
        // tier 3: 5 points fill the 5-point meter and hatch a Task Egg at once; Exp (3 points) would not.
        val hatched = RewardEngine.achievement(PlayerState(), event(3, RewardFocus.EGG), Random(1))
        assertTrue(hatched.rewards.any { it is Reward.Hatched }, "5 points fill the meter")
        assertEquals(0, hatched.state.eggMeter)
        assertTrue(RewardEngine.achievement(PlayerState(), event(3), Random(1)).rewards.none { it is Reward.Hatched })
    }

    @Test
    fun itemsDoublesTheDropChanceButNotThePoke() {
        fun drops(focus: RewardFocus) = (1..600).count { seed -> RewardEngine.achievement(PlayerState(), event(1, focus), Random(seed)).rewards.any { it is Reward.ItemDrop } }
        val ordinary = drops(RewardFocus.EXP)
        val items = drops(RewardFocus.ITEMS)
        assertTrue(items > ordinary * 3 / 2, "10% ordinary against 20%: $ordinary and $items")
        // Tier 1 fills no Egg meter to the top under either focus, so the only difference is the candies: the Poké is the same.
        for (focus in listOf(RewardFocus.EGG, RewardFocus.ITEMS)) {
            assertEquals(
                RewardEngine.achievement(PlayerState(), event(1), Random(3)).rewards.filterIsInstance<Reward.Coins>(),
                RewardEngine.achievement(PlayerState(), event(1, focus), Random(3)).rewards.filterIsInstance<Reward.Coins>(),
            )
        }
    }
}
