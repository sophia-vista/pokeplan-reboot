package app.pokeplan.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotebookTest {
    @Test
    fun eachLayerHasItsOwnSpeciesAndMarksWhatWasMetAndOwned() {
        for (layer in WorldLayer.entries) assertTrue(Notebook.species(layer).isNotEmpty(), layer.id)
        val kanto = Notebook.species(WorldLayer.KANTO)
        assertTrue(35 in kanto, "the Clefairy to rescue is in Kanto's notebook")
        val data = WorldData(discovered = setOf(10, 35))
        val entries = Notebook.of(WorldLayer.KANTO, data, ownedSpecies = setOf(10))
        assertEquals(true, entries.first { it.speciesId == 10 }.let { it.met && it.owned })
        assertEquals(true, entries.first { it.speciesId == 35 }.let { it.met && !it.owned })
        assertEquals(2, Notebook.progress(WorldLayer.KANTO, data).first)
        assertEquals(kanto.size, Notebook.progress(WorldLayer.KANTO, data).second)
    }

    @Test
    fun regionalJobsAreInRangeAndOnlyOpenWhereTheStoryAllows() {
        val regional = Missions.all.filter { it.id.startsWith("kanto-") || it.id.startsWith("johto-") || it.id.startsWith("hoenn-") }
        assertEquals(24, regional.size)
        for (m in regional) {
            val d = Dungeons.byId(m.dungeonId!!)!!
            assertTrue(m.floor in 1..d.floors, "${m.id} floor within ${d.name}")
            assertTrue(m.needs >= d.needs, "${m.id} rank is not below the dungeon's")
        }
        // A fresh save has the board shut. With everything cleared and a high rank the regional jobs are open; with a high rank alone, the ones
        // whose dungeon needs a later chapter are not.
        val kantoJob = Missions.byId("kanto-road-18")!!
        val ranked = WorldData(xp = 10_000_000)
        assertEquals(false, Missions.dungeonOpen(ranked, kantoJob), "Victory Road needs the story to get that far")
        assertEquals(false, kantoJob.id in Missions.available(ranked).map { it.id })
        val cleared = ranked.copy(clears = Dungeons.all.associate { it.id to 5 }, layersDone = WorldLayer.entries.map { it.id }.toSet(), guardianDone = true)
        assertTrue(Missions.dungeonOpen(cleared, kantoJob))
        assertTrue(kantoJob.id in Missions.available(cleared).map { it.id })
    }
}
