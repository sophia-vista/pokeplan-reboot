package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import app.pokeplan.world.story.StoryCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlolaTest {
    private val region = Dungeons.of(WorldLayer.ALOLA)

    @Test
    fun alolaHasNineDungeonsAndTheWildTablesFollowItsRegionalDex() {
        assertEquals(9, region.size)
        assertEquals("ultra-megalopolis", region.single { it.guardian }.id)
        assertEquals(AlolaStory.GUARDIAN.isNotBlank(), true)
        val dex = Dex.regionDex(Region.ALOLA).map { it.id }.toSet()
        region.forEach { d ->
            assertEquals(d.wild.size, d.wild.map { it.speciesId }.toSet().size, "${d.id} lists a species twice")
            d.wild.forEach {
                assertTrue(it.speciesId in dex, "${d.id} ${it.speciesId}")
                assertFalse(Dex.species(it.speciesId).isSpecial, "${d.id} ${it.speciesId}: legendaries are quests")
                assertTrue(it.fromFloor <= d.floors, "${d.id} ${it.speciesId} appears below the last floor")
            }
            assertTrue(d.wild.count { HatchRules.rarity(it.speciesId) != null } >= 4, d.id)
            d.milestones.keys.forEach { assertTrue(it in 1..d.floors, d.id) }
            assertTrue(d.chapter in 1..AlolaStory.chapters.size, d.id)
            assertEquals(WorldLayer.ALOLA, d.layer, d.id)
        }
        assertEquals(region.size, region.map { it.id }.toSet().size)
        assertTrue(region.flatMap { it.wild }.map { it.speciesId }.toSet().size >= 40)
    }

    @Test
    fun theStoryHasFiveAuthoredChaptersThatPassTheChecks() {
        assertEquals(5, AlolaStory.chapters.size)
        (1..5).forEach { assertNotNull(StoryCatalog.episodeFor(WorldLayer.ALOLA, it), "chapter $it has no episode") }
        assertEquals(emptyList(), StoryCatalog.problems())
    }

    @Test
    fun theStoryIsItsOwnAndOpenFromTheFirstDay() {
        assertEquals(1, Story.opened(WorldData(), WorldLayer.ALOLA))
        val others = WorldData(xp = 1_000_000, clears = (Dungeons.of(WorldLayer.KANTO) + Dungeons.of(WorldLayer.JOHTO) + Dungeons.of(WorldLayer.HOENN)).associate { it.id to 1 })
        assertEquals(1, Story.opened(others, WorldLayer.ALOLA))
        assertEquals(AlolaStory.chapters.size, Story.opened(WorldData(xp = 1_000_000, clears = region.associate { it.id to 1 }), WorldLayer.ALOLA))
        assertNotNull(WorldData().assign(1, listOf("a"))!!.start(1, "verdant-cavern", 0L))
        assertNull(WorldData().assign(1, listOf("a"))!!.start(1, "ultra-megalopolis", 0L))
        assertEquals("Explore Verdant Cavern", WorldGuide.nextStep(WorldData(), WorldLayer.ALOLA).title)
    }
}
