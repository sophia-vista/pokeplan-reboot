package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinishTest {
    private fun mon(id: String, species: Int, level: Int): OwnedPokemon =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    private val roster = listOf(mon("a", 25, 60), mon("b", 74, 60), mon("c", 10, 60), mon("d", 1, 60))
    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun everyDungeonUsesRealSpeciesAndMilestonesInsideItsFloors() {
        Dungeons.all.forEach { d ->
            d.wild.forEach { assertNotNull(Dex.speciesOrNull(it.speciesId), "${d.id} ${it.speciesId}") }
            assertTrue(d.wild.any { HatchRules.rarity(it.speciesId) != null }, d.id)
            d.milestones.keys.forEach { assertTrue(it in 1..d.floors, d.id) }
            assertTrue(d.chapter in 1..Story.chapters.size)
        }
        assertEquals(1, Dungeons.all.count { it.guardian })
        assertEquals(8, WorldRegion.entries.size)
    }

    @Test
    fun researchStepsAreReachableWithTheDungeonsTables() {
        val findable = Dungeons.all.flatMap { it.wild }.map { it.speciesId }.filter { HatchRules.rarity(it) != null }.toSet().size
        assertTrue(Research.steps.last().species <= findable, "$findable findable species")
    }

    @Test
    fun chapterAndStoryGateDungeons() {
        val high = WorldData(xp = 1_000_000, clears = mapOf("tiny-woods" to 1)) // rank Diamond, one clear: only chapter 2
        val d = high.assign(1, ids)!!
        assertNull(d.start(1, "temporal-tower", 0L), "chapter 6 needs 15 clears")
        assertNull(d.start(1, "waterfall-cave", 0L), "chapter 3 needs 3 clears")
        val done = WorldData(xp = 1_000_000, clears = mapOf("a" to 15))
        assertNotNull(done.assign(1, ids)!!.start(1, "temporal-tower", 0L))
        assertNull(done.assign(1, ids)!!.start(1, "distortion-rift", 0L), "a Distortion Dungeon opens after the story")
        assertNotNull(done.copy(guardianDone = true).assign(1, ids)!!.start(1, "distortion-rift", 0L))
    }

    @Test
    fun clearingTheGuardiansDungeonCompletesTheStoryOnceAndSpeedsUpFloors() {
        val start = WorldData(xp = 1_000_000, clears = mapOf("a" to 15)).assign(1, ids)!!.start(1, "temporal-tower", 0L)!!
        val r = WorldSim.settle(start, roster, 12L * 3_600_000)
        val d = Dungeons.byId("temporal-tower")!!
        val per = WorldSim.floorMillis(d, WorldSim.power(roster, d))
        if (12L * 3_600_000 / per >= d.floors) {
            assertTrue(r.data.guardianDone)
            assertEquals(1, r.events.count { it == WorldEvent.GuardianFaced })
        }
        val forced = WorldSim.settle(start.copy(teams = start.teams.map { it.copy(expedition = it.expedition!!.copy(floorsClimbed = d.floors - 1)) }), roster, 12L * 3_600_000)
        assertTrue(forced.data.guardianDone && Story.complete(forced.data))
        assertEquals(WorldSim.floorMillis(d, 100.0) * 9 / 10, WorldSim.floorMillis(d, 100.0, mastery = true))
    }

    @Test
    fun cafeShopAndDojoSpendPoke() {
        val d = WorldData(poke = 10_000)
        assertEquals(7_500, d.buyCandy()!!.poke)
        assertEquals(3_000, d.buyCandy()!!.candyPrice, "the second candy costs 500 more")
        assertNull(WorldData(poke = 100).buyCandy())
        val trained = d.train(WorldSkill.FORAGING)!!
        assertEquals(9_800, trained.poke)
        assertEquals(60, trained.skillXp["FORAGING"])
        assertTrue(d.brew(1_000)!!.brewUntil > 1_000)
    }

    @Test
    fun aBrewMakesFloorsLuckierWithoutChangingTheirPace() {
        val base = WorldData(xp = 1_000_000).assign(1, ids)!!.start(1, "tiny-woods", 0L)!!
        val plain = WorldSim.settle(base, roster, 6L * 3_600_000).data
        val brewed = WorldSim.settle(base.brew(0)!!, roster, 6L * 3_600_000).data
        assertEquals(plain.teams.single().expedition!!.floorsClimbed, brewed.teams.single().expedition!!.floorsClimbed)
        assertTrue(brewed.xp - 1_000_000 > plain.xp - 1_000_000)
    }

    @Test
    fun dailyJobsComeBackTomorrowAndWeeklyInAWeek() {
        val day = 86_400_000L
        val open = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1), lastSeenAt = 10 * day, missions = listOf(MissionProgress("daily-berries", 15, true)))
        val claimed = open.claimMission("daily-berries")!!
        assertTrue(Missions.available(claimed).none { it.id == "daily-berries" })
        assertTrue(Missions.available(claimed.copy(lastSeenAt = 11 * day)).any { it.id == "daily-berries" })
        val weekly = claimed.copy(missionClaimedDay = mapOf("weekly-steel" to 10L), lastSeenAt = 16 * day)
        assertTrue(Missions.available(weekly).none { it.id == "weekly-steel" })
        assertTrue(Missions.available(weekly.copy(lastSeenAt = 17 * day, xp = 1_500)).any { it.id == "weekly-steel" })
    }

    @Test
    fun deliverJobsFinishAtTheirFloor() {
        val d = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1)).acceptMission("deliver-berries")!!.assign(1, ids)!!.start(1, "tiny-woods", 0L)!!
        val r = WorldSim.settle(d, roster, 12L * 3_600_000).data
        assertTrue(r.missions.single().done)
    }

    @Test
    fun aBrewIsNotWastedAndEveryFloorCanShowSomeone() {
        val brewing = WorldData().brew(1_000)!!
        assertNull(brewing.brew(2_000), "still brewing")
        assertNotNull(brewing.brew(1_000 + Cafe.HOURS * 3_600_000L))
        Dungeons.all.forEach { d ->
            for (floor in 1..d.floors) {
                val pool = d.wild.filter { floor in it.fromFloor..it.toFloor && Dex.speciesOrNull(it.speciesId)?.isSpecial == false && HatchRules.rarity(it.speciesId) != null }
                assertTrue(pool.isNotEmpty(), "${d.id} floor $floor has nobody to find")
            }
        }
    }

    @Test
    fun jobAvailabilityFollowsTheClockPassedInNotTheLastSettle() {
        val day = 86_400_000L
        val claimed = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1), lastSeenAt = 10 * day, missions = listOf(MissionProgress("daily-berries", 15, true))).claimMission("daily-berries")!!
        assertTrue(Missions.available(claimed).none { it.id == "daily-berries" })
        assertTrue(Missions.available(claimed, now = 12 * day).any { it.id == "daily-berries" }, "a quiet World, two days later")
    }

    @Test
    fun aTransferredPokemonLeavesItsTeamAndAnEmptyTeamComesHome() {
        val out = WorldData().assign(1, listOf("a", "b"))!!.start(1, "tiny-woods", 0L)!!
        val one = out.pruned(setOf("a", "c"))
        assertEquals(listOf("a"), one.team(1)!!.members)
        assertNotNull(one.team(1)!!.expedition)
        val none = out.pruned(setOf("z"))
        assertTrue(none.team(1)!!.members.isEmpty() && none.team(1)!!.expedition == null)
        assertTrue(out.pruned(setOf("a", "b")) === out, "nothing to prune")
    }
}
