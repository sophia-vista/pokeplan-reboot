package app.pokeplan.world

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase2Test {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun teamsCanBeNamedAndColouredAndAnEmptyNameGoesBack() {
        val d = WorldData().renameTeam(1, "  Sea   crew  ")!!.recolourTeam(1, 3)!!
        assertEquals("Sea crew", d.team(1)!!.title)
        assertEquals(3, d.team(1)!!.accent)
        assertEquals("Team 1", d.renameTeam(1, "   ")!!.team(1)!!.title)
        assertEquals(WorldTeam.NAME_MAX, d.renameTeam(1, "x".repeat(40))!!.team(1)!!.name.length)
        assertNull(d.renameTeam(5, "Nope"), "a slot the rank has not opened")
        assertNull(d.recolourTeam(1, WorldTeam.ACCENTS))
        val out = WorldData().assign(1, listOf("a"))!!.start(1, "tiny-woods", 0L)!!.renameTeam(1, "Scouts")!!
        assertEquals(listOf("a"), out.team(1)!!.members)
        assertNotNull(out.team(1)!!.expedition)
    }

    @Test
    fun presetsKeepTheNewestSixAndComeBackWithWhoIsStillThere() {
        var d = WorldData()
        for (i in 1..8) d = d.savePreset("P$i", listOf("a"))!!
        assertEquals(WorldData.MAX_PRESETS, d.presets.size)
        assertEquals("P3", d.presets.first().name)
        assertEquals(1, d.savePreset("P8", listOf("b"))!!.presets.count { it.name == "P8" }, "the same name replaces")
        assertNull(WorldData().savePreset("", listOf("a")))
        assertNull(WorldData().savePreset("Empty", emptyList()))
        val saved = WorldData().savePreset("Duo", listOf("a", "b"))!!
        assertEquals(listOf("a"), saved.applyPreset(1, "Duo", setOf("a", "c"))!!.team(1)!!.members)
        assertNull(saved.applyPreset(1, "Duo", setOf("c")))
        val busy = saved.assign(2, listOf("b"))!!
        assertEquals(listOf("a"), busy.applyPreset(1, "Duo", setOf("a", "b"))!!.team(1)!!.members)
        assertTrue(saved.deletePreset("Duo").presets.isEmpty())
    }

    @Test
    fun oldSavesLoadWithTheNewFieldsAtTheirDefaults() {
        val old = """{"teams":[{"id":1,"members":["a"]}],"journal":[{"at":5,"text":"Team 1 cleared Tiny Woods."}],"xp":10}"""
        val d = json.decodeFromString(WorldData.serializer(), old)
        assertEquals(1, d.worldVersion)
        assertEquals("Team 1", d.team(1)!!.title)
        assertEquals(JournalLine.KIND_NOTE, d.journal.first().kind)
        assertTrue(d.presets.isEmpty())
        val back = json.decodeFromString(WorldData.serializer(), json.encodeToString(WorldData.serializer(), d.renameTeam(1, "Scouts")!!.savePreset("S", listOf("a"))!!))
        assertEquals("Scouts", back.team(1)!!.name)
        assertEquals(1, back.presets.size)
    }
}
