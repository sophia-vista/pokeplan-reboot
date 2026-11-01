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

class JohtoTest {
    private val johto = Dungeons.of(WorldLayer.JOHTO)

    @Test
    fun johtoHasNineDungeonsAndTheWildTablesFollowItsRegionalDex() {
        assertEquals(9, johto.size)
        assertEquals("tin-tower", johto.single { it.guardian }.id)
        val dex = Dex.regionDex(Region.JOHTO).map { it.id }.toSet()
        johto.forEach { d ->
            d.wild.forEach {
                assertTrue(it.speciesId in dex, "${d.id} ${it.speciesId}")
                assertFalse(Dex.species(it.speciesId).isSpecial, "${d.id} ${it.speciesId}: legendaries are quests")
            }
            assertTrue(d.wild.count { HatchRules.rarity(it.speciesId) != null } >= 4, d.id)
            d.milestones.keys.forEach { assertTrue(it in 1..d.floors, d.id) }
            assertTrue(d.chapter in 1..JohtoStory.chapters.size, d.id)
        }
        assertTrue(johto.flatMap { it.wild }.map { it.speciesId }.toSet().size >= 40)
    }

    @Test
    fun theStoryIsItsOwnAndOpenFromTheFirstDay() {
        assertEquals(1, Story.opened(WorldData(), WorldLayer.JOHTO))
        val kantoClears = WorldData(xp = 1_000_000, clears = Dungeons.of(WorldLayer.KANTO).associate { it.id to 1 })
        assertEquals(1, Story.opened(kantoClears, WorldLayer.JOHTO), "Kanto's clears do not open Johto's chapters")
        val johtoClears = WorldData(xp = 1_000_000, clears = johto.associate { it.id to 1 })
        assertEquals(JohtoStory.chapters.size, Story.opened(johtoClears, WorldLayer.JOHTO))
        assertEquals(1, Story.opened(johtoClears, WorldLayer.KANTO))
        assertNotNull(WorldData().assign(1, listOf("a"))!!.start(1, "sprout-tower", 0L))
        assertNull(WorldData().assign(1, listOf("a"))!!.start(1, "tin-tower", 0L), "the last dungeon needs Diamond rank and chapter 5")
        assertEquals("Explore Sprout Tower", WorldGuide.nextStep(WorldData(), WorldLayer.JOHTO).title)
    }

    @Test
    fun lugiaAndHoOhAreBothGuardiansOfJohto() {
        val none = WorldData()
        val whirl = none.copy(clears = mapOf("whirl-islands" to 3, "tin-tower" to 1))
        assertEquals(setOf("johto-lugia"), Legends.ready(whirl).map { it.id }.toSet(), "Ho-Oh waits for the Guardian to be faced")
        val faced = whirl.copy(layersDone = setOf("johto"))
        assertEquals(setOf("johto-lugia", "johto-ho-oh"), Legends.ready(faced).map { it.id }.toSet())
        Legends.of(WorldLayer.JOHTO).forEach { assertTrue(Dex.species(it.speciesId).isSpecial, it.id) }
        // Lugia makes Water and Flying dungeons of Johto faster; Ho-Oh adds treasure and finds there, and nothing in Kanto.
        val has = none.copy(legends = setOf("johto-lugia", "johto-ho-oh"))
        assertEquals(Legends.FASTER_PERCENT, has.legendFaster(Dungeons.byId("whirl-islands")!!))
        assertEquals(0, has.legendFaster(Dungeons.byId("sprout-tower")!!))
        assertEquals(0, has.legendFaster(Dungeons.byId("power-plant")!!))
        assertEquals(3, has.legendTreasurePercent(Dungeons.byId("ilex-forest")!!))
        assertEquals(0, has.legendTreasurePercent(Dungeons.byId("viridian-forest")!!))
        assertEquals(5, has.legendFindPermille(Dungeons.byId("tin-tower")!!))
    }
}
