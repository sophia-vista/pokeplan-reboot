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

class SinnohTest {
    private val sinnoh = Dungeons.of(WorldLayer.SINNOH)

    @Test
    fun sinnohHasNineDungeonsAndTheWildTablesFollowItsRegionalDex() {
        assertEquals(9, sinnoh.size)
        assertEquals("distortion-world", sinnoh.single { it.guardian }.id)
        val dex = Dex.regionDex(Region.SINNOH).map { it.id }.toSet()
        sinnoh.forEach { d ->
            assertEquals(d.wild.size, d.wild.map { it.speciesId }.toSet().size, "${d.id} lists a species twice")
            d.wild.forEach {
                assertTrue(it.speciesId in dex, "${d.id} ${it.speciesId}")
                assertFalse(Dex.species(it.speciesId).isSpecial, "${d.id} ${it.speciesId}: legendaries are quests")
            }
            assertTrue(d.wild.count { HatchRules.rarity(it.speciesId) != null } >= 4, d.id)
            d.milestones.keys.forEach { assertTrue(it in 1..d.floors, d.id) }
            assertTrue(d.chapter in 1..SinnohStory.chapters.size, d.id)
        }
        assertTrue(sinnoh.flatMap { it.wild }.map { it.speciesId }.toSet().size >= 40)
    }

    @Test
    fun theStoryIsItsOwnAndOpenFromTheFirstDay() {
        assertEquals(1, Story.opened(WorldData(), WorldLayer.SINNOH))
        val others = WorldData(xp = 1_000_000, clears = (Dungeons.of(WorldLayer.KANTO) + Dungeons.of(WorldLayer.JOHTO) + Dungeons.of(WorldLayer.HOENN)).associate { it.id to 1 })
        assertEquals(1, Story.opened(others, WorldLayer.SINNOH))
        assertEquals(SinnohStory.chapters.size, Story.opened(WorldData(xp = 1_000_000, clears = sinnoh.associate { it.id to 1 }), WorldLayer.SINNOH))
        assertNotNull(WorldData().assign(1, listOf("a"))!!.start(1, "eterna-forest", 0L))
        assertNull(WorldData().assign(1, listOf("a"))!!.start(1, "distortion-world", 0L))
        assertEquals("Explore Eterna Forest", WorldGuide.nextStep(WorldData(), WorldLayer.SINNOH).title)
    }

    @Test
    fun dialgaPalkiaAndGiratinaAreGuardiansOfSinnoh() {
        val cleared = WorldData(clears = mapOf("mt-coronet" to 3, "spear-pillar" to 2, "distortion-world" to 1))
        assertEquals(setOf("sinnoh-dialga"), Legends.ready(cleared).map { it.id }.toSet(), "Palkia needs a third clear, Giratina the Guardian")
        val faced = cleared.copy(clears = cleared.clears + ("spear-pillar" to 3), layersDone = setOf("sinnoh"))
        assertEquals(setOf("sinnoh-dialga", "sinnoh-palkia", "sinnoh-giratina"), Legends.ready(faced).map { it.id }.toSet())
        Legends.of(WorldLayer.SINNOH).forEach { assertTrue(Dex.species(it.speciesId).isSpecial, it.id) }
        val has = WorldData(legends = setOf("sinnoh-dialga", "sinnoh-palkia", "sinnoh-giratina"))
        assertEquals(Legends.FASTER_PERCENT, has.legendFaster(Dungeons.byId("mt-coronet")!!), "Dialga: Steel")
        assertEquals(Legends.FASTER_PERCENT, has.legendFaster(Dungeons.byId("spear-pillar")!!), "Palkia: Water")
        assertEquals(30, has.legendTreasurePercent(Dungeons.byId("distortion-world")!!) * 10, "Giratina adds 3% treasure")
        assertTrue(Legends.coveredSpecies.containsAll(listOf(483, 484, 487)), "Plan has no quest for them any more")
    }
}
