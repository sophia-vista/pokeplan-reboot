package app.pokeplan.ui.gear

import app.pokeplan.core.pokemon.GearTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolCatalogTest {
    @Test
    fun everyToolIsInTheCatalogueOnce() {
        assertEquals(GearTool.entries.toSet(), ToolCatalog.all.map { it.tool }.toSet())
        assertEquals(GearTool.entries.size, ToolCatalog.all.size)
        ToolCategory.entries.forEach { c -> assertTrue(ToolCatalog.all.any { it.category == c }, "${c.label} has a tool") }
        assertTrue(ToolCatalog.all.all { it.description.isNotBlank() && it.keywords.isNotEmpty() })
    }

    @Test
    fun searchFindsToolsBySynonymAndSlip() {
        fun first(q: String) = ToolCatalog.search(q).firstOrNull()?.tool
        assertEquals(GearTool.UNITS, first("lbs")); assertEquals(GearTool.CURRENCY, first("yen")); assertEquals(GearTool.FLASHLIGHT, first("torch"))
        assertEquals(GearTool.TIMER, first("countdown")); assertEquals(GearTool.STOPWATCH, first("laps")); assertEquals(GearTool.PICKER, first("dinner"))
        assertEquals(GearTool.COIN, first("d20")); assertEquals(GearTool.CALCULATOR, first("percent")); assertEquals(GearTool.CALCULATOR, first("Calculator"))
        assertEquals(GearTool.FLASHLIGHT, first("flashligt"), "one wrong letter in a longer word")
        assertTrue(ToolCatalog.search("convert").map { it.tool }.containsAll(listOf(GearTool.UNITS, GearTool.CURRENCY)))
        assertTrue(ToolCatalog.search("qqqqq").isEmpty())
        assertEquals(GearTool.entries.size, ToolCatalog.search("").size)
    }

    @Test
    fun favouritesAndRecentsComeFirstWhenNothingIsTyped() {
        val none = ToolCatalog.search("").map { it.tool }
        assertEquals(ToolCatalog.all.map { it.tool }, none, "the catalogue's own order")
        val ranked = ToolCatalog.search("", favorites = listOf("COIN"), recents = listOf("TIMER", "UNITS")).map { it.tool }
        assertEquals(listOf(GearTool.COIN, GearTool.TIMER, GearTool.UNITS), ranked.take(3))
        val random = ToolCatalog.search("random", favorites = listOf("COIN")).map { it.tool }
        assertEquals(GearTool.COIN, random.first())
        assertEquals(GearTool.UNITS, ToolCatalog.search("lbs", favorites = listOf("COIN")).first().tool)
    }

    @Test
    fun everyToolSitsOnExactlyOneShelf() {
        val groups = ToolCatalog.groups()
        assertEquals(ToolCategory.entries.map { it }, groups.map { it.category }, "shelves keep the catalogue's order")
        val tools = groups.flatMap { it.tools }
        assertEquals(ToolCatalog.all.size, tools.size, "no tool is on two shelves and none is lost")
        assertEquals(GearTool.entries.toSet(), tools.map { it.tool }.toSet(), "every tool stays reachable")
        assertEquals(groups.map { it.category }, groups.flatMap { it.tools }.map { it.category }.distinct(), "each shelf appears once, in catalogue order")
        val time = ToolCatalog.groups(ToolCategory.TIME)
        assertEquals(listOf(ToolCategory.TIME), time.map { it.category })
        assertEquals(setOf(GearTool.TIMER, GearTool.STOPWATCH, GearTool.WORLD_CLOCK), time.single().tools.map { it.tool }.toSet())
    }

    @Test
    fun favouritesAndRecentsAreTheirOwnStrips() {
        assertEquals(listOf(GearTool.COIN, GearTool.TIMER), ToolCatalog.favorites(listOf("COIN", "TIMER", "GONE")).map { it.tool })
        assertEquals(emptyList<ToolInfo>(), ToolCatalog.favorites(emptyList()))
        val recents = ToolCatalog.recents(listOf("TIMER", "UNITS", "QR", "COIN", "SOUND"), favorites = listOf("QR"), limit = 3)
        assertEquals(listOf(GearTool.TIMER, GearTool.UNITS, GearTool.COIN), recents.map { it.tool }, "newest first, favourites left out, at most three")
        assertEquals(emptyList<ToolInfo>(), ToolCatalog.recents(listOf("GONE")))
    }
}
