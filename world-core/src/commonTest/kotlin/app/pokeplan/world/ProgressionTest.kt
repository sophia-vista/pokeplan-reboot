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

class ProgressionTest {
    private fun mon(id: String, species: Int, level: Int): OwnedPokemon =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    private val roster = listOf(mon("a", 25, 30), mon("b", 74, 30))
    private fun trip() = WorldData().assign(1, listOf("a", "b"))!!.start(1, "tiny-woods", 0L)!!

    @Test
    fun skillsLevelWithUseAndStopAtTen() {
        assertEquals(0, WorldSkill.levelAt(29))
        assertEquals(1, WorldSkill.levelAt(30))
        assertEquals(10, WorldSkill.levelAt(1_000_000))
        val d = WorldSim.settle(trip(), roster, 12L * 3_600_000).data
        assertTrue(d.skillXp.getValue("EXPLORATION") > 10)
        assertTrue(d.skillXp.getValue("FORAGING") > 0)
    }

    @Test
    fun facilitiesCostPokeAndTopOut() {
        val d = WorldData(poke = 400)
        assertEquals(1, d.upgrade(Facility.CAMP)!!.facilityLevel(Facility.CAMP))
        assertEquals(0, d.upgrade(Facility.CAMP)!!.poke)
        assertNull(d.upgrade(Facility.LAB), "the lab costs 600")
        assertEquals(1_200, Facility.CAMP.cost(1))
        assertNull(WorldData(poke = 10_000_000, facilities = mapOf("CAMP" to 5)).upgrade(Facility.CAMP))
    }

    @Test
    fun theLabRaisesExplorationXp() {
        val plain = WorldSim.settle(trip(), roster, 3_600_000 * 4).data.xp
        val lab = WorldSim.settle(trip().copy(facilities = mapOf("LAB" to 5)), roster, 3_600_000 * 4).data.xp
        assertTrue(lab > plain * 13 / 10, "level 5 is about 50% more (each floor rounds down)")
    }

    @Test
    fun researchPaysOncePerStep() {
        val d = WorldData(discovered = setOf(1, 2, 3, 4))
        assertEquals(listOf(3), d.researchReady().map { it.species })
        val c = d.claimResearch(3)!!
        assertEquals(100, c.poke)
        assertNull(c.claimResearch(3))
        assertNull(d.claimResearch(6))
        assertNotNull(Research.steps.size)
    }

    @Test
    fun settlingOnceOrOftenGivesTheSameResultWithBonuses() {
        val start = trip().copy(facilities = mapOf("CAMP" to 2, "LAB" to 2), skillXp = mapOf("FORAGING" to 2_000, "SCOUTING" to 2_000))
        val once = WorldSim.settle(start, roster, 10L * 3_600_000).data
        var often = start
        for (h in 1..10) often = WorldSim.settle(often, roster, h * 3_600_000L).data
        assertEquals(once.poke, often.poke)
        assertEquals(once.xp, often.xp)
        assertEquals(once.finds, often.finds)
    }
}
