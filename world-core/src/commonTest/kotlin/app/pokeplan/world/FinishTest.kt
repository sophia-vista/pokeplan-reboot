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
            assertTrue(d.chapter in 1..Story.chaptersOf(d.layer).size)
        }
        WorldLayer.entries.forEach { layer -> assertEquals(1, Dungeons.of(layer).count { it.guardian }, layer.id) }
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
        assertNull(d.start(1, "temporal-tower", 0L), "chapter 6 needs seven different dungeons cleared")
        assertNull(d.start(1, "waterfall-cave", 0L), "chapter 3 needs three different dungeons cleared")
        val done = WorldData(xp = 1_000_000, clears = SEVEN)
        assertNotNull(done.assign(1, ids)!!.start(1, "temporal-tower", 0L))
        assertNull(done.assign(1, ids)!!.start(1, "distortion-rift", 0L), "a Distortion Dungeon opens after the story")
        assertNotNull(done.copy(guardianDone = true).assign(1, ids)!!.start(1, "distortion-rift", 0L))
    }

    @Test
    fun clearingTheGuardiansDungeonCompletesTheStoryOnceAndSpeedsUpFloors() {
        val start = WorldData(xp = 1_000_000, clears = SEVEN).assign(1, ids)!!.start(1, "temporal-tower", 0L)!!
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

class ClockAndDayTest {
    private fun mon(id: String) = Hatching.create(id, HatchOption(10, Dex.defaultForm(10).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = 30)
    private val roster = listOf(mon("a"), mon("b"))

    @Test
    fun aClockSetBackDoesNotFreezeTheClimb() {
        val trip = WorldData().assign(1, listOf("a", "b"))!!.start(1, "tiny-woods", 10L * 3_600_000)!!
        // The phone's clock is now earlier than the moment the expedition last settled.
        val back = WorldSim.settle(trip, roster, 2L * 3_600_000).data
        assertEquals(2L * 3_600_000, back.team(1)!!.expedition!!.settledTo, "the settle point follows the clock back")
        val later = WorldSim.settle(back, roster, 8L * 3_600_000).data
        assertTrue(later.team(1)!!.expedition!!.floorsClimbed > 0, "and the team climbs again from there")
    }

    @Test
    fun aDailyJobComesBackAtThePlayersMidnightNotUtcs() {
        val hour = 3_600_000L
        val day = 24 * hour
        val plus10 = 10 * hour   // a player ten hours ahead of UTC
        // Claimed at 20:00 UTC on day 5, which is 06:00 on day 6 for them.
        val claimedAt = 5 * day + 20 * hour
        val d = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1), lastSeenAt = claimedAt, missions = listOf(MissionProgress("daily-berries", 15, true))).claimMission("daily-berries", plus10)!!
        assertEquals(6L, d.missionClaimedDay["daily-berries"])
        // 13:00 UTC on day 6 is 23:00 on day 6 for them: still the same day. 14:00 UTC is their midnight.
        assertTrue(Missions.available(d, now = 6 * day + 13 * hour, utcOffsetMillis = plus10).none { it.id == "daily-berries" })
        assertTrue(Missions.available(d, now = 6 * day + 14 * hour, utcOffsetMillis = plus10).any { it.id == "daily-berries" })
    }
}

class AbilityPerksTest {
    @Test
    fun matchingAbilitiesAddLevelsUpToThree() {
        assertEquals(0, AbilityPerks.bonus(listOf("Static", "Blaze"), WorldSkill.FORAGING))
        assertEquals(2, AbilityPerks.bonus(listOf("Pickup", "Honey Gather", "Blaze"), WorldSkill.FORAGING))
        assertEquals(AbilityPerks.MAX_BONUS, AbilityPerks.bonus(List(6) { "Keen Eye" }, WorldSkill.SCOUTING))
        WorldSkill.entries.forEach { assertTrue(AbilityPerks.bySkill.getValue(it).isNotEmpty()) }
    }

    @Test
    fun aHelpfulTeamFindsMoreBerriesOnTheSameFloors() {
        fun mon(id: String, ability: String) = Hatching.create(id, HatchOption(10, Dex.defaultForm(10).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = 30, ability = ability)
        val plain = listOf(mon("a", "Blaze"), mon("b", "Blaze"), mon("c", "Blaze"))
        val pickers = plain.map { it.copy(ability = "Pickup") }
        fun berries(team: List<OwnedPokemon>): Int {
            val d = WorldData(xp = 400).assign(1, team.map { it.id })!!.start(1, "tiny-woods", 0L)!!
            return WorldSim.settle(d, team, 12L * 3_600_000).data.items.filterKeys { it.startsWith("berry-") }.values.sum()
        }
        assertTrue(berries(pickers) >= berries(plain), "three levels of Foraging is three points more berry chance")
    }
}

/** Seven different dungeons cleared: every chapter that rank allows. */
private val SEVEN = listOf("tiny-woods", "thunderwave-cave", "mt-steel", "waterfall-cave", "glacier-palace", "ancient-ruins", "sky-tower").associateWith { 1 }

class GuideAndLogTest {
    private fun mon(id: String, species: Int, level: Int) = Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    @Test
    fun theNextStepFollowsProgress() {
        assertEquals("Explore Tiny Woods", WorldGuide.nextStep(WorldData()).title)
        val cleared = WorldData(clears = mapOf("tiny-woods" to 1))
        assertEquals("Explore Thunderwave Cave", WorldGuide.nextStep(cleared).title)
        // Everything open is cleared: the next closed one says what it waits for.
        val twoCleared = WorldData(clears = mapOf("tiny-woods" to 1, "thunderwave-cave" to 1))
        val step = WorldGuide.nextStep(twoCleared)
        assertEquals("Work towards Mt. Steel", step.title)
        assertTrue("Bronze" in step.reason && "Exploration XP" in step.reason, step.reason)
        val all = WorldData(xp = 1_000_000, guardianDone = true, clears = Dungeons.all.associate { it.id to 1 })
        assertEquals("Everything is cleared", WorldGuide.nextStep(all).title)
    }

    @Test
    fun aTeamsFitIsItsSpeedAndHowManyAreFavoured() {
        val woods = Dungeons.byId("tiny-woods")!!
        val grass = mon("a", 1, 20)   // Bulbasaur: Grass, favoured in the woods
        val rock = mon("b", 74, 20)   // Geodude: not
        val fit = WorldSim.suitability(woods, listOf(grass, rock))
        assertEquals(1, fit.favored)
        assertEquals(2, fit.size)
        assertTrue(fit.floorMillis < woods.baseFloorSeconds * 1000L)
        assertTrue(WorldSim.suitability(woods, listOf(grass, grass.copy(id = "c"))).floorMillis < fit.floorMillis, "two favoured are quicker")
    }

    @Test
    fun theExpeditionLogKeepsTheLatestLinesWithFindsAndClears() {
        val roster = listOf(mon("a", 25, 30), mon("b", 74, 30), mon("c", 10, 30), mon("d", 1, 30))
        val trip = WorldData().assign(1, listOf("a", "b", "c", "d"))!!.start(1, "tiny-woods", 0L)!!
        val d = WorldSim.settle(trip, roster, 12L * 3_600_000).data
        assertTrue(d.journal.any { it.text.contains("cleared Tiny Woods") })
        assertTrue(d.journal.zipWithNext().none { (x, y) -> x.text == y.text }, "repeated clears are one line")
        assertTrue(d.journal.any { it.text.contains("cleared Tiny Woods (") && it.text.endsWith("times).") }, d.journal.map { it.text }.toString())
        assertTrue(d.journal.all { it.at == 12L * 3_600_000 })
        var more = d
        repeat(6) { i -> more = WorldSim.settle(more, roster, (13L + i * 12) * 3_600_000).data }
        assertTrue(more.journal.size <= WorldData.JOURNAL_SIZE)
    }
}
