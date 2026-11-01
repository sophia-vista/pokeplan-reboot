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

class HoennTest {
    private val hoenn = Dungeons.of(WorldLayer.HOENN)

    @Test
    fun hoennHasNineDungeonsAndTheWildTablesFollowItsRegionalDex() {
        assertEquals(9, hoenn.size)
        assertEquals("sky-pillar", hoenn.single { it.guardian }.id)
        val dex = Dex.regionDex(Region.HOENN).map { it.id }.toSet()
        hoenn.forEach { d ->
            assertEquals(d.wild.size, d.wild.map { it.speciesId }.toSet().size, "${d.id} lists a species twice")
            d.wild.forEach {
                assertTrue(it.speciesId in dex, "${d.id} ${it.speciesId}")
                assertFalse(Dex.species(it.speciesId).isSpecial, "${d.id} ${it.speciesId}: legendaries are quests")
            }
            assertTrue(d.wild.count { HatchRules.rarity(it.speciesId) != null } >= 4, d.id)
            d.milestones.keys.forEach { assertTrue(it in 1..d.floors, d.id) }
            assertTrue(d.chapter in 1..HoennStory.chapters.size, d.id)
        }
        assertTrue(hoenn.flatMap { it.wild }.map { it.speciesId }.toSet().size >= 40)
    }

    @Test
    fun theStoryIsItsOwnAndOpenFromTheFirstDay() {
        assertEquals(1, Story.opened(WorldData(), WorldLayer.HOENN))
        val others = WorldData(xp = 1_000_000, clears = (Dungeons.of(WorldLayer.KANTO) + Dungeons.of(WorldLayer.JOHTO)).associate { it.id to 1 })
        assertEquals(1, Story.opened(others, WorldLayer.HOENN))
        assertEquals(HoennStory.chapters.size, Story.opened(WorldData(xp = 1_000_000, clears = hoenn.associate { it.id to 1 }), WorldLayer.HOENN))
        assertNotNull(WorldData().assign(1, listOf("a"))!!.start(1, "petalburg-woods", 0L))
        assertNull(WorldData().assign(1, listOf("a"))!!.start(1, "sky-pillar", 0L))
        assertEquals("Explore Petalburg Woods", WorldGuide.nextStep(WorldData(), WorldLayer.HOENN).title)
    }

    @Test
    fun groudonKyogreAndRayquazaAreGuardiansOfHoenn() {
        val cleared = WorldData(clears = mapOf("mt-chimney" to 3, "seafloor-cavern" to 2, "sky-pillar" to 1))
        assertEquals(setOf("hoenn-groudon"), Legends.ready(cleared).map { it.id }.toSet(), "Kyogre needs a third clear, Rayquaza the Guardian")
        val faced = cleared.copy(clears = cleared.clears + ("seafloor-cavern" to 3), layersDone = setOf("hoenn"))
        assertEquals(setOf("hoenn-groudon", "hoenn-kyogre", "hoenn-rayquaza"), Legends.ready(faced).map { it.id }.toSet())
        Legends.of(WorldLayer.HOENN).forEach { assertTrue(Dex.species(it.speciesId).isSpecial, it.id) }
        val has = WorldData(legends = setOf("hoenn-groudon", "hoenn-kyogre", "hoenn-rayquaza"))
        assertEquals(Legends.FASTER_PERCENT, has.legendFaster(Dungeons.byId("mt-chimney")!!), "Groudon: Fire")
        assertEquals(Legends.FASTER_PERCENT, has.legendFaster(Dungeons.byId("seafloor-cavern")!!), "Kyogre: Water")
        assertEquals(0, has.legendFaster(Dungeons.byId("viridian-forest")!!))
        assertEquals(5, has.legendFindPermille(Dungeons.byId("sky-pillar")!!))
    }
}
