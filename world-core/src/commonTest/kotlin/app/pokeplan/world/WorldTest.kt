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
        assertTrue(three.data.xp > 0 && three.data.poke > 0)
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
        assertEquals(once.data.poke, stepped.poke)
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
        val silver = WorldData(xp = 1_500)
        assertEquals(3, silver.teamSlots)
        assertNotNull(silver.assign(3, listOf("a"))!!.start(3, "mt-steel", 0L))
    }

    @Test
    fun theStoryOpensWithRankAndClears() {
        assertEquals(1, Story.opened(WorldData()))
        assertEquals(2, Story.opened(WorldData(xp = 300, clears = mapOf("tiny-woods" to 1))))
        assertEquals("Requests and Rescues", Story.next(WorldData())!!.title)
        assertNull(Story.next(WorldData(xp = 1_000_000, clears = SEVEN)))
    }
}

/** Seven different dungeons cleared: every chapter that rank allows. */
private val SEVEN = listOf("tiny-woods", "thunderwave-cave", "mt-steel", "waterfall-cave", "glacier-palace", "ancient-ruins", "sky-tower").associateWith { 1 }
