package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainerRankTest {
    @Test
    fun oneRankPerRegionAndNamesMatch() {
        assertEquals(Region.entries.size, TrainerRank.names.size)
        assertEquals(TrainerRank.names.size, TrainerRank.thresholds.size)
        assertEquals(TrainerRank.thresholds.sorted(), TrainerRank.thresholds)
        assertEquals(0, TrainerRank.thresholds.first())
    }

    @Test
    fun rankFollowsPoints() {
        assertEquals(0, TrainerRank.rankFor(0))
        val first = TrainerRank.thresholds[1]
        assertEquals(0, TrainerRank.rankFor(first - 1))
        assertEquals(1, TrainerRank.rankFor(first))
        assertEquals("Apprentice", TrainerRank.nameFor(first + 100))
        assertEquals(9, TrainerRank.rankFor(1_000_000))
        assertEquals(1, TrainerRank.pointsToNext(first - 1))
        assertNull(TrainerRank.pointsToNext(TrainerRank.thresholds.last() + 1))
    }

    @Test
    fun regionsOpenOnePerRankStartingWithHome() {
        assertEquals(listOf(Region.HOME), TrainerRank.unlockedRegions(0))
        assertEquals(listOf(Region.HOME, Region.KANTO), TrainerRank.unlockedRegions(TrainerRank.thresholds[1]))
        assertEquals(Region.entries, TrainerRank.unlockedRegions(TrainerRank.thresholds.last()))
        assertTrue(TrainerRank.isUnlocked(Region.HOME, 0))
        assertFalse(TrainerRank.isUnlocked(Region.JOHTO, TrainerRank.thresholds[2] - 1))
        assertTrue(TrainerRank.isUnlocked(Region.JOHTO, TrainerRank.thresholds[2]))
        assertEquals(2, TrainerRank.rankToUnlock(Region.JOHTO))
    }

    @Test
    fun featuresAppearAsTheRankRises() {
        assertTrue(TrainerRank.isVisible(Feature.TASKS, 0))
        assertTrue(Feature.entries.filter { it.step == 0 }.all { TrainerRank.isVisible(it, 0) })
        assertFalse(TrainerRank.isVisible(Feature.SHOP, 0))
        assertTrue(TrainerRank.isVisible(Feature.SHOP, TrainerRank.thresholds[2]))
        assertFalse(TrainerRank.isVisible(Feature.CONTESTS, TrainerRank.thresholds[4]))
        assertTrue(TrainerRank.isVisible(Feature.CONTESTS, TrainerRank.thresholds[5]))
    }

    @Test
    fun theEngineAwardsPoints() {
        val effort = RewardEngine.effort(PlayerState(), EffortEvent(25, false, Region.KANTO, newPokemonId = "e", nowMillis = 0), Random(1)).state
        assertEquals(5, effort.trainerPoints, "1 point per 5 minutes")
        val work = RewardEngine.achievement(effort, AchievementEvent(5, Region.KANTO, newPokemonId = "e2", nowMillis = 0), Random(2)).state
        assertEquals(15, work.trainerPoints, "an A-rank task is worth 10")
        assertEquals(0, work.rank)
    }
}
