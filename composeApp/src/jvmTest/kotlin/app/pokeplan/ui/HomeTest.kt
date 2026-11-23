package app.pokeplan.ui

import app.pokeplan.ui.screens.HomeLive
import app.pokeplan.ui.screens.HomeLiveAction
import app.pokeplan.ui.screens.HomeOpportunities
import app.pokeplan.ui.screens.Module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The module cards' live lines: which card carries each, in what order, and nothing when nothing is live. */
class HomeTest {
    private val several = HomeLive(
        reportFloors = 3,
        floorDue = true,
        floorDueWhere = "Tiny Woods",
        jobsReady = 2,
        finds = 1,
        outbreaks = 1,
        dreamers = 2,
        safariVisits = 1,
        companionCandies = 3,
        companionTitle = "Book club",
    )

    @Test
    fun nothingIsLiveMeansNothingIsShown() {
        assertEquals(emptyList(), HomeOpportunities.select(HomeLive()))
        assertEquals(emptyMap(), HomeOpportunities.byModule(HomeLive()))
    }

    @Test
    fun liveThingsAreOneLineEachAndTheMostUrgentComeFirst() {
        val lines = HomeOpportunities.select(several)
        assertEquals(listOf(5, 5, 4, 3, 3, 2, 2, 1), lines.map { it.urgency }, "most urgent first")
        assertEquals(HomeLiveAction.WORLD_REPORT, lines.first().kind)
        assertTrue(lines.none { it.text.isBlank() || it.action.isBlank() })
        assertTrue(lines.map { it.id }.toSet().size == lines.size, "one line per thing")
        val only = HomeOpportunities.select(HomeLive(companionCandies = 2, companionTitle = "Book club"))
        assertEquals("Book club: 2 candies waiting", only.single().text)
        assertEquals("1 job ready to report", HomeOpportunities.select(HomeLive(jobsReady = 1)).single().text)
    }

    @Test
    fun everyLineSitsOnItsOwnModulesCard() {
        val byModule = HomeOpportunities.byModule(several)
        assertEquals(setOf(Module.WORLD, Module.MINT, Module.CARE, Module.LINK, Module.PLAN), byModule.keys)
        assertEquals(setOf("world-report", "world-jobs", "world-floor", "world-finds"), byModule.getValue(Module.WORLD).map { it.id }.toSet())
        assertEquals(listOf("mint-outbreak"), byModule.getValue(Module.MINT).map { it.id })
        assertEquals(listOf("care-dream"), byModule.getValue(Module.CARE).map { it.id })
        assertEquals(listOf("link-safari"), byModule.getValue(Module.LINK).map { it.id })
        assertEquals(listOf("plan-candies"), byModule.getValue(Module.PLAN).map { it.id })
        assertTrue(byModule.values.flatten().all { it.module == it.kind.module })
    }

    @Test
    fun aLiveLineFloatsItsCardByUrgency() {
        val sev = HomeOpportunities.select(several).associate { it.id to HomeOpportunities.severityOf(it) }
        assertEquals(3, sev.getValue("world-jobs"))
        assertEquals(3, sev.getValue("mint-outbreak"))
        assertEquals(2, sev.getValue("world-finds"))
        assertEquals(1, sev.getValue("care-dream"))
        assertEquals(1, sev.getValue("plan-candies"))
    }
}
