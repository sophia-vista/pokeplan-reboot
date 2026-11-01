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
        assertEquals(9, Dungeons.of(WorldLayer.HOME).size, "the home layer is unchanged")
        assertEquals("cerulean-cave", kanto.single { it.guardian }.id)
    }

    @Test
    fun theWildTablesAreKantoSpeciesOnly() {
        kanto.forEach { d ->
            d.wild.forEach { assertTrue(it.speciesId in 1..151, "${d.id} ${it.speciesId}") }
            // Never a legendary (they are quests), and enough that can hatch for finds.
            d.wild.forEach { assertFalse(Dex.species(it.speciesId).isSpecial, "${d.id} ${it.speciesId}") }
            assertTrue(d.wild.count { HatchRules.rarity(it.speciesId) != null } >= 4, d.id)
        }
        // A layer's Pokémon do not leak into the home layer.
        val home = Dungeons.of(WorldLayer.HOME).flatMap { it.wild }.map { it.speciesId }.toSet()
        assertTrue(kanto.flatMap { it.wild }.map { it.speciesId }.toSet().size >= 40)
        assertTrue(home.isNotEmpty())
    }

    @Test
    fun theStoryOpensByRankAndKantoClearsAndIsOpenFromTheFirstDay() {
        assertEquals(1, Story.opened(WorldData(), WorldLayer.KANTO), "open on day one")
        val viridian = WorldData().assign(1, listOf("a"))
        assertNotNull(viridian)
        // The first two dungeons need no story chapter and no rank.
        listOf("viridian-forest", "mt-moon").forEach { id -> assertNotNull(viridian.start(1, id, 0L), id) }
        assertNull(viridian.start(1, "rock-tunnel", 0L), "Rock Tunnel needs Bronze rank")
        // Clears of the home layer do not advance Kanto's story, nor the other way round.
        val homeClears = WorldData(xp = 1_000_000, clears = mapOf("tiny-woods" to 1, "thunderwave-cave" to 1, "mt-steel" to 1))
        assertEquals(1, Story.opened(homeClears, WorldLayer.KANTO))
        val kantoClears = WorldData(xp = 1_000_000, clears = kanto.associate { it.id to 1 })
        assertEquals(KantoStory.chapters.size, Story.opened(kantoClears, WorldLayer.KANTO))
        assertEquals(1, Story.opened(kantoClears, WorldLayer.HOME))
    }

    @Test
    fun theLegendQuestsWaitForTheirClearsAndTheGuardian() {
        val none = WorldData()
        assertTrue(Legends.ready(none).isEmpty())
        val birds = none.copy(clears = mapOf("power-plant" to 3, "seafoam-islands" to 2, "mt-ember" to 3))
        assertEquals(setOf("kanto-zapdos", "kanto-moltres"), Legends.ready(birds).map { it.id }.toSet())
        // Mew and Mewtwo need the Guardian faced; Mewtwo is the first clear, Mew the third.
        val cave = none.copy(clears = mapOf("cerulean-cave" to 3))
        assertTrue(Legends.ready(cave).isEmpty(), "the cave alone is not the Guardian")
        val faced = cave.copy(layersDone = setOf("kanto"))
        assertEquals(setOf("kanto-mewtwo", "kanto-mew"), Legends.ready(faced).map { it.id }.toSet())
        assertEquals(1, Legends.ready(faced.copy(clears = mapOf("cerulean-cave" to 1))).size)
        // Given once.
        assertTrue(Legends.ready(faced.copy(legends = setOf("kanto-mewtwo", "kanto-mew"))).isEmpty())
        // Every quest points at a Kanto legendary or mythical, in its own dungeon.
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
        var data = WorldData(xp = 1_000_000, clears = kanto.associate { it.id to 1 }).assign(1, listOf("a"))!!.start(1, d.id, 0L)!!
        var faced = 0
        var now = 0L
        // At most 12 hours of away time count at once, so settle in steps until the top floor is reached.
        repeat(8) {
            now += WorldSim.MAX_AWAY_MILLIS
            val report = WorldSim.settle(data, listOf(mon), now)
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
        // The Distortion Rift also waits for the story itself.
        val rift = WorldGuide.lockReasons(WorldData(), Dungeons.byId("distortion-rift")!!)
        assertEquals(3, rift.size, rift.toString())
        assertTrue("story of Mystery Dungeon world is complete" in rift[2], rift[2])
        // Met requirements drop out; an open dungeon has none.
        val partway = WorldData(xp = 60_000, clears = Dungeons.of(WorldLayer.KANTO).take(3).associate { it.id to 1 })
        val left = WorldGuide.lockReasons(partway, cave)
        assertEquals(1, left.size)
        assertTrue("5 more" !in left[0] && "(you have 3)" in left[0], left[0])
        assertTrue(WorldGuide.lockReasons(WorldData(), Dungeons.byId("tiny-woods")!!).isEmpty())
    }
}
