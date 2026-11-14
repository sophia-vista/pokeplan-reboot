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

class GalarTest {
    private val layer = Dungeons.of(WorldLayer.GALAR)

    @Test
    fun galarHasNineDungeonsAndTheWildTablesFollowItsDex() {
        assertEquals(9, layer.size)
        assertEquals("energy-plant", layer.single { it.guardian }.id)
        assertEquals("energy-plant", layer.last().id)
        val dex = Dex.regionDex(Region.GALAR).map { it.id }.toSet()
        layer.forEach { d ->
            assertEquals(d.wild.size, d.wild.map { it.speciesId }.toSet().size, "${d.id} lists a species twice")
            assertTrue(d.floors >= 8, d.id)
            d.wild.forEach {
                assertTrue(it.speciesId in dex, "${d.id} ${it.speciesId}")
                assertFalse(Dex.species(it.speciesId).isSpecial, "${d.id} ${it.speciesId}: legendaries are quests")
            }
            assertTrue(d.wild.count { HatchRules.rarity(it.speciesId) != null } >= 4, d.id)
            d.milestones.keys.forEach { assertTrue(it in 1..d.floors, d.id) }
            assertTrue(d.chapter in 1..GalarStory.chapters.size, d.id)
        }
        assertTrue(layer.flatMap { it.wild }.map { it.speciesId }.toSet().size >= 40)
        assertEquals(5, GalarStory.chapters.size)
        assertTrue(GalarStory.GUARDIAN.isNotBlank() && GalarStory.GUARDIAN_BONUS.isNotBlank())
    }

    @Test
    fun theStoryIsItsOwnAndOpenFromTheFirstDay() {
        assertEquals(1, Story.opened(WorldData(), WorldLayer.GALAR))
        assertEquals(GalarStory.chapters.size, Story.opened(WorldData(xp = 1_000_000, clears = layer.associate { it.id to 1 }), WorldLayer.GALAR))
        assertNotNull(WorldData().assign(1, listOf("a"))!!.start(1, "wild-area", 0L))
        assertNull(WorldData().assign(1, listOf("a"))!!.start(1, "energy-plant", 0L))
    }

}
