package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.HatchOption
import app.pokeplan.core.pokemon.Hatching
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KantoTest {
    private val kanto = Dungeons.of(WorldLayer.KANTO)

    @Test
    fun kantoHasNineDungeonsWithIdsThatAreUniqueAcrossLayers() {
        assertEquals(9, kanto.size)
        assertEquals(Dungeons.all.size, Dungeons.all.map { it.id }.toSet().size)
        assertEquals(5, Dungeons.of(WorldLayer.RESCUE).size, "the Rescue Team arc")
        assertEquals(9, Dungeons.of(WorldLayer.EXPLORERS).size, "the Explorers arc")
        assertEquals("cerulean-cave", kanto.single { it.guardian }.id)
    }

    @Test
    fun theWildTablesAreKantoSpeciesOnly() {
        kanto.forEach { d ->
            d.wild.forEach { assertTrue(it.speciesId in 1..151, "${d.id} ${it.speciesId}") }
            d.wild.forEach { assertFalse(Dex.species(it.speciesId).isSpecial, "${d.id} ${it.speciesId}") }
            assertTrue(d.wild.count { HatchRules.rarity(it.speciesId) != null } >= 4, d.id)
        }
        val home = (Dungeons.of(WorldLayer.RESCUE) + Dungeons.of(WorldLayer.EXPLORERS)).flatMap { it.wild }.map { it.speciesId }.toSet()
        assertTrue(kanto.flatMap { it.wild }.map { it.speciesId }.toSet().size >= 40)
        assertTrue(home.isNotEmpty())
    }

    @Test
    fun theStoryOpensByRankAndKantoClearsAndIsOpenFromTheFirstDay() {
        assertEquals(1, Story.opened(WorldData(), WorldLayer.KANTO), "open on day one")
        val viridian = WorldData().assign(1, listOf("a"))
        assertNotNull(viridian)
        listOf("viridian-forest", "mt-moon").forEach { id -> assertNotNull(viridian.start(1, id, 0L), id) }
        assertNull(viridian.start(1, "rock-tunnel", 0L), "Rock Tunnel needs Bronze rank")
        val homeClears = WorldData(xp = 1_000_000, clears = mapOf("tiny-woods" to 1, "thunderwave-cave" to 1, "mt-steel" to 1))
        assertEquals(1, Story.opened(homeClears, WorldLayer.KANTO))
        val kantoClears = WorldData(xp = 1_000_000, clears = kanto.associate { it.id to 1 })
        assertEquals(KantoStory.chapters.size, Story.opened(kantoClears, WorldLayer.KANTO))
        assertEquals(1, Story.opened(kantoClears, WorldLayer.EXPLORERS))
    }

    @Test
    fun theLegendQuestsWaitForTheirClearsAndTheGuardian() {
        val none = WorldData()
        assertTrue(Legends.ready(none).isEmpty())
        val birds = none.copy(clears = mapOf("power-plant" to 3, "seafoam-islands" to 2, "mt-ember" to 3))
        assertEquals(setOf("kanto-zapdos", "kanto-moltres"), Legends.ready(birds).map { it.id }.toSet())
        val cave = none.copy(clears = mapOf("cerulean-cave" to 3))
        assertTrue(Legends.ready(cave).isEmpty(), "the cave alone is not the Guardian")
        val faced = cave.copy(layersDone = setOf("kanto"))
        assertEquals(setOf("kanto-mewtwo", "kanto-mew"), Legends.ready(faced).map { it.id }.toSet())
        assertEquals(1, Legends.ready(faced.copy(clears = mapOf("cerulean-cave" to 1))).size)
        assertTrue(Legends.ready(faced.copy(legends = setOf("kanto-mewtwo", "kanto-mew"))).isEmpty())
        Legends.of(WorldLayer.KANTO).forEach { q ->
            assertTrue(Dex.species(q.speciesId).isSpecial, q.id)
            assertEquals(WorldLayer.KANTO, Dungeons.byId(q.dungeonId)?.layer, q.id)
        }
    }

    @Test
    fun aBirdMakesItsTypesDungeonsFasterOnlyInKanto() {
        val plant = Dungeons.byId("power-plant")!!
        val moon = Dungeons.byId("mt-moon")!!
        val zapdos = WorldData(legends = setOf("kanto-zapdos"))
        assertEquals(Legends.FASTER_PERCENT, zapdos.legendFaster(plant))
        assertEquals(0, zapdos.legendFaster(moon), "Mt. Moon does not favour Electric")
        assertEquals(0, zapdos.legendFaster(Dungeons.byId("thunderwave-cave")!!), "the home layer is not helped")
        assertEquals(WorldSim.floorMillis(plant, 100.0) * 9 / 10, WorldSim.floorMillis(plant, 100.0, fasterPercent = 10))
    }

    @Test
    fun facingMewtwoFinishesKantoOnceAndNotTheHomeStory() {
        val d = Dungeons.byId("cerulean-cave")!!
        val mon = Hatching.create("a", HatchOption(25, Dex.defaultForm(25).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = 100)
        val team = listOf(mon, mon.copy(id = "b"), mon.copy(id = "c"), mon.copy(id = "d"))
        var data = WorldData(xp = 1_000_000, clears = kanto.associate { it.id to 1 }).assign(1, team.map { it.id })!!.start(1, d.id, 0L)!!
        var faced = 0
        var now = 0L
        repeat(8) {
            now += WorldSim.MAX_AWAY_MILLIS
            val report = WorldSim.settle(data, team, now)
            data = report.data
            faced += report.events.count { it == WorldEvent.LayerGuardianFaced(WorldLayer.KANTO) }
        }
        assertTrue(data.layerDone(WorldLayer.KANTO))
        assertFalse(data.guardianDone, "the home Guardian is a different story")
        assertEquals(1, faced)
    }

    @Test
    fun theGuideSuggestsKantoDungeonsInOrder() {
        assertEquals("Explore Viridian Forest", WorldGuide.nextStep(WorldData(), WorldLayer.KANTO).title)
        val first = WorldData(clears = mapOf("viridian-forest" to 1))
        assertEquals("Explore Mt. Moon", WorldGuide.nextStep(first, WorldLayer.KANTO).title)
        val all = WorldData(xp = 1_000_000, layersDone = setOf("kanto"), clears = kanto.associate { it.id to 1 })
        assertEquals("Everything is cleared", WorldGuide.nextStep(all, WorldLayer.KANTO).title)
        assertEquals("Explore Tiny Woods", WorldGuide.nextStep(WorldData()).title, "the home layer's guide is unchanged")
    }
}

class LockReasonsTest {
    @Test
    fun everyUnmetRequirementIsListedNotOnlyTheFirst() {
        val cave = Dungeons.byId("cerulean-cave")!!
        val fresh = WorldGuide.lockReasons(WorldData(), cave)
        assertEquals(2, fresh.size, fresh.toString())
        assertTrue("Diamond rank" in fresh[0] && "60000" in fresh[0], fresh[0])
        assertTrue("story chapter 5" in fresh[1] && "Platinum rank" in fresh[1] && "8 different Kanto dungeons cleared (you have 0)" in fresh[1], fresh[1])
        val rift = WorldGuide.lockReasons(WorldData(), Dungeons.byId("distortion-rift")!!)
        assertEquals(3, rift.size, rift.toString())
        assertTrue("story of Explorers is complete" in rift[2], rift[2])
        val partway = WorldData(xp = 60_000, clears = Dungeons.of(WorldLayer.KANTO).take(3).associate { it.id to 1 })
        val left = WorldGuide.lockReasons(partway, cave)
        assertEquals(1, left.size)
        assertTrue("5 more" !in left[0] && "(you have 3)" in left[0], left[0])
        assertTrue(WorldGuide.lockReasons(WorldData(), Dungeons.byId("tiny-woods")!!).isEmpty())
    }
}

class SpeedExplanationTest {
    private fun mon(id: String, species: Int, level: Int) =
        Hatching.create(id, HatchOption(species, Dex.defaultForm(species).identifier), OwnedPokemon.PARTY_SIZE, 0L, 60, Random(1)).copy(level = level)

    @Test
    fun theExplanationGivesTheSameFloorTimeAsTheSimulation() {
        val members = listOf(mon("a", 25, 40), mon("b", 74, 30), mon("c", 10, 20))
        for (d in Dungeons.all) for (data in listOf(WorldData(), WorldData(guardianDone = true, legends = setOf("kanto-zapdos", "kanto-moltres", "johto-lugia")))) {
            val e = WorldSim.explainSpeed(d, members, data)
            assertEquals(WorldSim.floorMillis(d, WorldSim.power(members, d), data.guardianDone, data.legendFaster(d)), e.floorMillis, d.id)
            assertEquals(90, e.levels)
            assertEquals(e.levels + e.favoredBonus + e.identityBonus, e.power, 1e-9, d.id)
        }
        val strong = listOf(mon("a", 25, 100), mon("b", 74, 100), mon("c", 10, 100), mon("d", 1, 100))
        val tiny = WorldSim.explainSpeed(Dungeons.byId("tiny-woods")!!, strong, WorldData())
        assertTrue(tiny.atMinimum && tiny.afterPowerMillis == tiny.baseMillis / 4)
        assertTrue(tiny.skillHelp.values.all { it in 1..AbilityPerks.MAX_BONUS })
    }
}
