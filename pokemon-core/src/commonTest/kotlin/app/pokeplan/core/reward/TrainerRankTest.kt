package app.pokeplan.core.reward

import app.pokeplan.core.dex.Region
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainerRankTest {
    @Test
    fun tenRanksAndNamesMatch() {
        assertEquals(10, TrainerRank.names.size)
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
        assertEquals("Apprentice", TrainerRank.nameFor(first + 10))
        assertEquals(9, TrainerRank.rankFor(1_000_000))
        assertEquals(1, TrainerRank.pointsToNext(first - 1))
        assertNull(TrainerRank.pointsToNext(TrainerRank.thresholds.last() + 1))
    }

    @Test
    fun onlyThePlanAndCareRegionsOpen() {
        // The games' regions are retired: they never open, at any rank, and send their eggs to the Plan Region.
        for (points in listOf(0, TrainerRank.thresholds[1], TrainerRank.thresholds.last())) {
            assertEquals(listOf(Region.PLAN, Region.CARE), TrainerRank.unlockedRegions(points))
        }
        assertTrue(Region.games.isNotEmpty() && Region.games.none { it.released })
        assertTrue(Region.games.all { it.open == Region.PLAN })
        assertEquals(Region.CARE, Region.CARE.open)
        assertFalse(TrainerRank.isUnlocked(Region.JOHTO, TrainerRank.thresholds.last()))
        assertEquals(0, TrainerRank.rankToUnlock(Region.HOUSEHOLD))
        assertFalse(TrainerRank.isUnlocked(Region.HOUSEHOLD, TrainerRank.thresholds.last()), "a module region opens when its module ships")
    }

    @Test
    fun thePlanRegionKeepsItsStoredName() {
        assertEquals("\"HOME\"", Json.encodeToString(Region.PLAN))
        assertEquals(Region.PLAN, Json.decodeFromString<Region>("\"HOME\""))
        assertEquals("\"KANTO\"", Json.encodeToString(Region.KANTO))
    }

    @Test
    fun featuresAppearAsTheRankRises() {
        assertTrue(TrainerRank.isVisible(Feature.TASKS, 0))
        assertTrue(Feature.entries.filter { it.step == 0 }.all { TrainerRank.isVisible(it, 0) })
        assertFalse(TrainerRank.isVisible(Feature.HELD_ITEMS, TrainerRank.thresholds[1]))
        assertTrue(TrainerRank.isVisible(Feature.HELD_ITEMS, TrainerRank.thresholds[2]))
        assertFalse(TrainerRank.isVisible(Feature.GUARDIANS, TrainerRank.thresholds[3]))
        assertTrue(TrainerRank.isVisible(Feature.GUARDIANS, TrainerRank.thresholds[4]))
        assertEquals(listOf(Feature.HELD_ITEMS, Feature.PERKS, Feature.GROWTH_SCREEN, Feature.COINS), Feature.at(2))
        assertTrue((1..4).all { Feature.at(it).isNotEmpty() }, "every early rank opens something")
    }

    @Test
    fun ranksComeQuicklyThenSlowly() {
        val gaps = TrainerRank.thresholds.zipWithNext { a, b -> b - a }
        assertEquals(gaps.sorted(), gaps, "each rank takes longer than the one before")
        assertTrue(TrainerRank.thresholds[1] <= 60, "the first rank comes within a couple of days of play")
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
