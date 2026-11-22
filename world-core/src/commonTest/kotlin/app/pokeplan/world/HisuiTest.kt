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

class HisuiTest {
    private val layer = Dungeons.of(WorldLayer.HISUI)

    @Test
    fun hisuiHasNineDungeonsAndTheWildTablesFollowItsDex() {
        assertEquals(9, layer.size)
        assertEquals("temple-of-sinnoh", layer.single { it.guardian }.id)
        assertEquals("temple-of-sinnoh", layer.last().id)
        val dex = Region.entries.flatMap { Dex.regionDex(it) }.map { it.id }.toSet()
        layer.forEach { d ->
            assertEquals(d.wild.size, d.wild.map { it.speciesId }.toSet().size, "${d.id} lists a species twice")
            assertTrue(d.floors >= 8, d.id)
            d.wild.forEach {
                assertTrue(it.speciesId in dex, "${d.id} ${it.speciesId}")
                assertFalse(Dex.species(it.speciesId).isSpecial, "${d.id} ${it.speciesId}: legendaries are quests")
            }
            assertTrue(d.wild.count { HatchRules.rarity(it.speciesId) != null } >= 4, d.id)
            d.milestones.keys.forEach { assertTrue(it in 1..d.floors, d.id) }
            assertTrue(d.chapter in 1..HisuiStory.chapters.size, d.id)
        }
        assertTrue(layer.flatMap { it.wild }.map { it.speciesId }.toSet().size >= 40)
        assertEquals(5, HisuiStory.chapters.size)
        assertTrue(HisuiStory.GUARDIAN.isNotBlank() && HisuiStory.GUARDIAN_BONUS.isNotBlank())
    }

    @Test
    fun theStoryIsItsOwnAndOpenFromTheFirstDay() {
        assertEquals(1, Story.opened(WorldData(), WorldLayer.HISUI))
        assertEquals(HisuiStory.chapters.size, Story.opened(WorldData(xp = 1_000_000, clears = layer.associate { it.id to 1 }), WorldLayer.HISUI))
        assertNotNull(WorldData().assign(1, listOf("a"))!!.start(1, "obsidian-fieldlands", 0L))
        assertNull(WorldData().assign(1, listOf("a"))!!.start(1, "temple-of-sinnoh", 0L))
    }

}
