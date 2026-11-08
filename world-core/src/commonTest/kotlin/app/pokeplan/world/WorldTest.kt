package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldTest {
    private fun mon(id: String, species: Int, level: Int = 10): OwnedPokemon =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    private val roster = listOf(mon("a", 1, 15), mon("b", 25, 12), mon("c", 74, 10), mon("d", 10, 8))

    private fun out(data: WorldData = WorldData(), members: List<String> = listOf("a", "b"), dungeon: String = "tiny-woods", start: Long = 0L) =
        data.assign(1, members)!!.start(1, dungeon, start)!!

    @Test
    fun aTeamOfTheRankAllowsAndNoPokemonTwice() {
        val d = WorldData()
        assertEquals(2, d.teamSlots)
        assertNotNull(d.assign(1, listOf("a", "b")))
        assertNull(d.assign(3, listOf("a")), "a third team needs more rank")
        assertNull(d.assign(1, listOf("a", "a")))
        assertNull(d.assign(1, listOf("a", "b", "c", "d", "e")))
        val one = d.assign(1, listOf("a", "b"))!!
        assertNull(one.assign(2, listOf("b", "c")), "b is already on team 1")
        assertNotNull(one.assign(2, listOf("c")))
    }

    @Test
    fun aTeamMustHaveMembersAndAnOpenDungeon() {
        val d = WorldData()
        assertNull(d.assign(1, emptyList())!!.start(1, "tiny-woods", 0L))
        assertNull(d.assign(1, listOf("a"))!!.start(1, "mt-steel", 0L), "Mt. Steel needs Bronze")
        assertNull(d.assign(1, listOf("a"))!!.start(1, "nowhere", 0L))
        val busy = out()
        assertNull(busy.start(1, "tiny-woods", 5L))
        assertNull(busy.assign(1, listOf("a")), "an expedition under way keeps its team")
        assertNotNull(busy.recall(1)!!.assign(1, listOf("a")))
    }

    @Test
    fun powerSpeedsTheFloorsUpToALimit() {
        val dungeon = Dungeons.byId("tiny-woods")!!
        val slow = WorldSim.floorMillis(dungeon, 0.0)
        val quick = WorldSim.floorMillis(dungeon, 100.0)
        assertEquals(dungeon.baseFloorSeconds * 1000L, slow)
        assertTrue(quick < slow)
        assertEquals(dungeon.baseFloorSeconds * 1000L / 4, WorldSim.floorMillis(dungeon, 100_000.0))
        // Grass, bug and flying lines explore the woods faster.
        assertTrue(WorldSim.power(listOf(roster[0]), dungeon) > roster[0].level.toDouble() - 0.001)
    }

    @Test
    fun settlingClimbsTheFloorsThatTimeAllows() {
        val data = out()
        val dungeon = Dungeons.byId("tiny-woods")!!
        val per = WorldSim.floorMillis(dungeon, WorldSim.power(listOf(roster[0], roster[1]), dungeon))
        val none = WorldSim.settle(data, roster, per - 1)
        assertTrue(none.isEmpty)
        val three = WorldSim.settle(data, roster, per * 3 + per / 2)
        assertEquals(3, three.perTeam.getValue(1).count { it is WorldEvent.FloorCleared })
        assertEquals(3, three.data.team(1)!!.expedition!!.floorsClimbed)
        assertEquals(3, three.data.deepest["tiny-woods"])
        assertTrue(three.data.xp > 0 && three.data.pokeEarned > 0)
    }

    @Test
    fun offlineIsExactlyAsGoodAsStayingAndIsCapped() {
        val data = out()
        val end = 5L * 60 * 60 * 1000
        val once = WorldSim.settle(data, roster, end)
        var stepped = data
        var t = 0L
        while (t < end) { t += 37_000; stepped = WorldSim.settle(stepped, roster, minOf(t, end)).data }
        assertEquals(once.data.xp, stepped.xp)
        assertEquals(once.data.pokeEarned, stepped.pokeEarned)
        assertEquals(once.data.items, stepped.items)
        assertEquals(once.data.finds.map { it.speciesId }, stepped.finds.map { it.speciesId })
        // Three days away counts only the cap.
        val away = WorldSim.settle(data, roster, 3L * 24 * 60 * 60 * 1000)
        val capped = WorldSim.settle(data, roster, WorldSim.MAX_AWAY_MILLIS)
        assertEquals(capped.data.xp, away.data.xp)
    }

    @Test
    fun aDungeonIsClearedRepeatedlyAndMilestonesPayOnce() {
        val data = out(members = listOf("a", "b", "c", "d"))
        val report = WorldSim.settle(data, roster, 6L * 60 * 60 * 1000)
        val clears = report.data.clears["tiny-woods"] ?: 0
        assertTrue(clears >= 2, "a long trip clears the five floors more than once: $clears")
        val milestones = report.events.filterIsInstance<WorldEvent.Milestone>()
        assertEquals(2, milestones.size, "the floor-3 and floor-5 milestones pay once each")
        assertTrue("tiny-woods:3" in report.data.milestonesPaid)
    }

    @Test
    fun findsAreOrdinaryPokemonFromTheDungeonsTable() {
        val data = out(members = listOf("a", "b", "c", "d"))
        val report = WorldSim.settle(data, roster, 11L * 60 * 60 * 1000)
        val allowed = Dungeons.byId("tiny-woods")!!.wild.map { it.speciesId }.toSet()
        assertTrue(report.data.finds.isNotEmpty(), "eleven hours finds someone")
        report.data.finds.forEach {
            assertTrue(it.speciesId in allowed)
            assertNotNull(HatchRules.rarity(it.speciesId), "legendary and mythical Pokémon are never random finds")
        }
        assertEquals(report.data.finds.map { it.speciesId }.toSet(), report.data.discovered)
        assertEquals(1, report.data.withoutFind(0).finds.size.let { report.data.finds.size - it })
    }

    @Test
    fun rankCountsTheXpAndOpensTeamsAndDungeons() {
        assertEquals(ExplorerRank.NORMAL, ExplorerRank.at(0))
        assertEquals(ExplorerRank.BRONZE, ExplorerRank.at(300))
        assertEquals(ExplorerRank.DIAMOND, ExplorerRank.at(1_000_000))
        val silver = WorldData(xp = 1_500, clears = mapOf("tiny-woods" to 1))
        assertEquals(3, silver.teamSlots)
        assertNotNull(silver.assign(3, listOf("a"))!!.start(3, "mt-steel", 0L))
    }

    @Test
    fun everyMemberIsCountedForEveryFloorTheTeamClimbsAndAnEmptyStretchCountsNone() {
        val start = out(members = listOf("a", "b", "c"))
        val per = WorldSim.floorMillis(Dungeons.byId("tiny-woods")!!, WorldSim.power(listOf(roster[0], roster[1], roster[2]), Dungeons.byId("tiny-woods")!!))
        val first = WorldSim.settle(start, roster, 7 * per + per / 2)
        val floors = first.events.count { it is WorldEvent.FloorCleared }
        assertEquals(7, floors)
        // The full count for each member, not a share of it; a Pokémon outside the team gets none.
        assertEquals(mapOf("a" to 7, "b" to 7, "c" to 7), first.memberFloors)
        // Settling again at once finds no new floor, so nothing is counted twice.
        assertEquals(emptyMap(), WorldSim.settle(first.data, roster, 7 * per + per / 2).memberFloors)
        // However often it is settled, the floors add up to the same: one settle at 20 floors equals settles in between.
        val once = WorldSim.settle(start, roster, 20 * per).memberFloors.getValue("a")
        var d = start
        var sum = 0
        for (step in 1..20) { val r = WorldSim.settle(d, roster, step * per); d = r.data; sum += r.memberFloors["a"] ?: 0 }
        assertEquals(once, sum)
    }

    @Test
    fun theBestTeamForADungeonCountsTypesNotJustLevels() {
        val woods = Dungeons.byId("tiny-woods")!!   // favors Grass, Bug and Flying
        val pool = listOf(mon("water", 7, 40), mon("fire", 4, 39), mon("grass", 1, 34), mon("bug", 10, 33), mon("flying", 16, 33), mon("rock", 74, 20))
        val best = WorldSim.bestTeam(woods, pool)
        // 34 x 1.2 = 40.8 and 33 x 1.2 = 39.6 beat a plain level 40 and 39: levels alone would have picked Water and Fire. (Moves, abilities and items add
        // a little on top, so the order inside the team follows the whole contribution, but the three favoured types are in and Fire is out.)
        assertEquals(pool.sortedByDescending { WorldSim.contribution(it, woods) }.take(4).map { it.id }, best.map { it.id })
        assertTrue(listOf("grass", "bug", "flying").all { id -> id in best.map { it.id } } && "fire" !in best.map { it.id })
        val byLevel = pool.sortedByDescending { it.level }.take(4)
        assertTrue(WorldSim.power(best, woods) > WorldSim.power(byLevel, woods))
        // The same choice every time, whatever the order given; fewer than four is fine; nothing gives nothing.
        assertEquals(best.map { it.id }, WorldSim.bestTeam(woods, pool.reversed()).map { it.id })
        assertEquals(2, WorldSim.bestTeam(woods, pool.take(2)).size)
        assertEquals(emptyList(), WorldSim.bestTeam(woods, emptyList()))
        // Another dungeon favors other types, and so another team.
        val other = Dungeons.all.first { it.favored.intersect(woods.favored).isEmpty() }
        assertTrue(WorldSim.bestTeam(other, pool).map { it.id } != best.map { it.id } || other.favored.none { f -> pool.any { f in it.form.types } })
    }

    @Test
    fun theStoryOpensWithRankAndClears() {
        assertEquals(1, Story.opened(WorldData()))
        assertEquals(2, Story.opened(WorldData(xp = 300, clears = mapOf("tiny-woods" to 1))))
        assertEquals("The World Needs Helping", Story.next(WorldData())!!.title)
        assertNull(Story.next(WorldData(xp = 1_000_000, clears = SEVEN)))
    }
}

/** Seven different dungeons cleared: every chapter that rank allows. */
private val SEVEN = listOf("tiny-woods", "thunderwave-cave", "mt-steel", "waterfall-cave", "glacier-palace", "ancient-ruins", "sky-tower").associateWith { 1 }
