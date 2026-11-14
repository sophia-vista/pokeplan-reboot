package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Region
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaldeaTest {
    private val layer = Dungeons.of(WorldLayer.PALDEA)

    @Test
    fun paldeaHasNineDungeonsAndTheWildTablesFollowItsDex() {
        assertEquals(9, layer.size)
        assertEquals("area-zero", layer.single { it.guardian }.id)
        assertEquals("area-zero", layer.last().id)
        val dex = Dex.regionDex(Region.PALDEA).map { it.id }.toSet()
        layer.forEach { d ->
            assertEquals(d.wild.size, d.wild.map { it.speciesId }.toSet().size, "${d.id} lists a species twice")
            assertTrue(d.floors >= 8, d.id)
            d.wild.forEach {
                assertTrue(it.speciesId in dex, "${d.id} ${it.speciesId}")
                assertFalse(Dex.species(it.speciesId).isSpecial, "${d.id} ${it.speciesId}: legendaries are quests")
            }
            assertTrue(d.wild.count { HatchRules.rarity(it.speciesId) != null } >= 4, d.id)
            d.milestones.keys.forEach { assertTrue(it in 1..d.floors, d.id) }
            assertTrue(d.chapter in 1..PaldeaStory.chapters.size, d.id)
        }
        assertTrue(layer.flatMap { it.wild }.map { it.speciesId }.toSet().size >= 40)
        assertEquals(5, PaldeaStory.chapters.size)
        assertTrue(PaldeaStory.GUARDIAN.isNotBlank() && PaldeaStory.GUARDIAN_BONUS.isNotBlank())
    }

    @Test
    fun theStoryIsItsOwnAndOpenFromTheFirstDay() {
        assertEquals(1, Story.opened(WorldData(), WorldLayer.PALDEA))
        assertEquals(PaldeaStory.chapters.size, Story.opened(WorldData(xp = 1_000_000, clears = layer.associate { it.id to 1 }), WorldLayer.PALDEA))
        assertNotNull(WorldData().assign(1, listOf("a"))!!.start(1, "tagtree-thicket", 0L))
        assertNull(WorldData().assign(1, listOf("a"))!!.start(1, "area-zero", 0L))
    }

    @Test
    fun everyPaldeaDungeonPaysGimmighoulCoins() {
        layer.forEach { d -> assertTrue(d.milestones.values.any { it.itemId == app.pokeplan.core.pokemon.Items.GIMMIGHOUL_COIN }, d.id) }
    }
}
