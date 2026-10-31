package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MissionsTest {
    private fun mon(id: String, species: Int, level: Int): OwnedPokemon =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    private val roster = listOf(mon("a", 25, 30), mon("b", 74, 30), mon("c", 10, 30), mon("d", 1, 30))
    /** Chapter 2 open: Bronze rank and one clear. */
    private val open = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1))

    @Test
    fun theBoardStaysClosedUntilTheStoryOpensIt() {
        assertTrue(Missions.available(WorldData()).isEmpty())
        assertNull(WorldData().acceptMission("explore-woods-3"))
        assertNotNull(open.acceptMission("explore-woods-3"))
    }

    @Test
    fun rescuesNameRealHatchableSpecies() {
        Missions.all.filter { it.kind == MissionKind.RESCUE }.forEach { assertNotNull(Dex.speciesOrNull(it.rescued!!), it.id) }
        Missions.all.forEach { m -> m.dungeonId?.let { assertTrue(m.floor <= Dungeons.byId(it)!!.floors, m.id) } }
    }

    @Test
    fun atMostThreeJobsAtOnce() {
        var d = open
        listOf("explore-woods-3", "explore-woods-5", "gather-10").forEach { d = d.acceptMission(it)!! }
        assertNull(d.acceptMission("rescue-vulpix"))
        assertNull(d.acceptMission("gather-10"), "already taken")
    }

    @Test
    fun anExploreJobFinishesWhenTheFloorIsReachedAndPaysOnClaim() {
        val d = open.acceptMission("explore-woods-3")!!.assign(1, listOf("a", "b", "c", "d"))!!.start(1, "tiny-woods", 0L)!!
        val r = WorldSim.settle(d, roster, 12L * 60 * 60 * 1000)
        assertTrue(r.events.any { it == WorldEvent.MissionDone("explore-woods-3") })
        assertTrue(r.data.missions.single().done)
        val poke = r.data.poke
        val claimed = r.data.claimMission("explore-woods-3")!!
        assertEquals(poke + 60, claimed.poke)
        assertTrue(claimed.missions.isEmpty())
        assertNull(claimed.acceptMission("explore-woods-3"), "ordinary jobs wait a week")
        assertNotNull(claimed.copy(lastSeenAt = claimed.lastSeenAt + 8 * 86_400_000L).acceptMission("explore-woods-3"), "then they are repeatable")
    }

    @Test
    fun aRescueLeavesAFindAndNeverComesBack() {
        val d = open.acceptMission("rescue-vulpix")!!.assign(1, listOf("a", "b", "c", "d"))!!.start(1, "tiny-woods", 0L)!!
        val r = WorldSim.settle(d, roster, 12L * 60 * 60 * 1000).data
        assertTrue(r.finds.any { it.speciesId == 37 })
        val after = r.claimMission("rescue-vulpix")!!
        assertTrue("rescue-vulpix" in after.missionsDone)
        assertNull(after.acceptMission("rescue-vulpix"))
    }

    @Test
    fun collectCountsBerriesAndAJobCanBeAbandoned() {
        val d = open.acceptMission("gather-10")!!.assign(1, listOf("a", "b", "c", "d"))!!.start(1, "tiny-woods", 0L)!!
        val r = WorldSim.settle(d, roster, 12L * 60 * 60 * 1000).data
        assertTrue(r.missions.single().progress >= 10 && r.missions.single().done)
        assertNull(r.abandonMission("gather-10"), "a finished job is claimed, not abandoned")
        assertTrue(open.acceptMission("gather-10")!!.abandonMission("gather-10")!!.missions.isEmpty())
    }
}

class EscortDefeatTest {
    private fun mon(id: String, species: Int, level: Int): OwnedPokemon =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    private val roster = listOf(mon("a", 74, 40), mon("b", 74, 40), mon("c", 74, 40), mon("d", 74, 40))
    private val open = WorldData(xp = 400, clears = mapOf("tiny-woods" to 1))
    private val day = 12L * 3_600_000

    @Test
    fun anEscortNeedsACrowd() {
        val small = open.acceptMission("escort-cubone")!!.assign(1, listOf("a", "b"))!!.start(1, "thunderwave-cave", 0L)!!
        assertTrue(WorldSim.settle(small, roster, day * 4).data.missions.single().done.not(), "two Pokémon are not enough")
        val crowd = open.acceptMission("escort-cubone")!!.assign(1, listOf("a", "b", "c"))!!.start(1, "thunderwave-cave", 0L)!!
        assertTrue(WorldSim.settle(crowd, roster, day).data.missions.single().done)
    }

    @Test
    fun aDefeatNeedsPower() {
        val weak = open.acceptMission("defeat-geodude")!!.assign(1, listOf("a"))!!.start(1, "mt-steel", 0L)!!
        val r = WorldSim.settle(weak, roster, day * 8).data
        assertTrue(r.missions.single().done.not(), "one level 40 Pokémon has about 50 power")
        val strong = open.copy(xp = 400).acceptMission("defeat-geodude")!!.assign(1, listOf("a", "b", "c", "d"))!!.start(1, "mt-steel", 0L)!!
        assertTrue(WorldSim.settle(strong, roster, day * 2).data.missions.single().done, "four level 40 Pokémon have 160")
    }
}
