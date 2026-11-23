package app.pokeplan.ui

import app.pokeplan.app.storage.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.ui.care.CareSection
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.PlanSection
import app.pokeplan.mint.TxType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationTest {
    private val clock = object : Clock { override fun nowMillis() = 1_700_000_000_000L }
    private fun model() = AppModel(GameStore(MemoryStorage(), clock).also { it.start("Sophia", 25) })

    @Test
    fun everyPlaceHasATabAndLandsWhereItSays() {
        Place.entries.forEach { place -> assertTrue(Tab.entries.contains(place.tab), "$place has a tab") }
        model().apply { open(Place.PLAN, true); assertEquals(Module.PLAN, module); assertNull(planSection) }
        model().apply { open(Place.PLANNER, true); assertEquals(Module.PLAN, module); assertNull(planSection) }
        model().apply { open(Place.FOCUS, true); assertEquals(Module.PLAN, module); assertEquals(PlanSection.FOCUS, planSection) }
        model().apply { open(Place.FOCUS, false); assertEquals(Module.PLAN, module); assertNull(planSection) }
        model().apply { open(Place.CARE, true); assertEquals(Module.CARE, module); assertNull(careSection) }
        model().apply { open(Place.HEAL, true); assertEquals(Module.HEAL, module); assertNull(healSection) }
        model().apply { open(Place.MINT, true); assertEquals(Module.MINT, module); assertNull(mintSection) }
        model().apply { open(Place.BASE, true); assertEquals(Module.BASE, module); assertNull(baseSection) }
        model().apply { linkPersonId = "x"; open(Place.LINK, true); assertEquals(Module.LINK, module); assertNull(linkSection); assertNull(linkPersonId) }
        model().apply { open(Place.WORLD, true); assertEquals(Module.WORLD, module); assertNull(worldSection) }
        model().apply { open(Place.PARTY, true); assertTrue(openParty) }
        model().apply { open(Place.GEAR, true); assertTrue(gearOpen) }
    }

    @Test
    fun everyPlanPlaceIsInTheSectionsKeyAndProgressAndTrendsAreInMe() {
        assertEquals(listOf(PlanSection.TASKS, PlanSection.CALENDAR, PlanSection.FOCUS, PlanSection.GOALS, PlanSection.SETTINGS), PlanSection.entries)
    }

    @Test
    fun theNamesWidgetsAndShortcutsSendResolveToPlaces() {
        listOf("PLANNER", "FOCUS", "CARE", "HEAL", "MINT", "BASE", "LINK", "WORLD", "ME", "HOME", "COLLECTION", "GEAR").forEach { name ->
            assertTrue(Place.entries.any { it.name == name }, "$name is a place")
        }
    }

    @Test
    fun arrivingSomewhereClosesThePokegearAndOnlyGearOpensIt() {
        val m = model()
        m.open(Place.GEAR, true)
        assertTrue(m.gearOpen, "Gear opens the Pokégear")
        m.open(Place.CARE, true)
        assertFalse(m.gearOpen, "a destination from a widget or notification replaces the Pokégear instead of opening behind it")
        m.open(Place.GEAR, true)
        m.open(Place.HOME, true)
        assertFalse(m.gearOpen, "Home closes it too")
        m.open(Place.GEAR, true)
        m.open(Place.GEAR, true)
        assertTrue(m.gearOpen, "Gear still opens it")
    }

    @Test
    fun arrivingAtMintStartsAPlainExpenseWhateverALeftoverQuickActionAskedFor() {
        val m = model()
        m.mintStartType = TxType.TRANSFER
        m.mintStartTo = "acc-1"
        m.open(Place.MINT, true)
        assertNull(m.mintStartType, "a kind left by an earlier quick action does not become the next entry's")
        assertNull(m.mintStartTo)
    }

    @Test
    fun openingAModuleTwiceKeepsItsPlaceAndHomeLeavesTheModulesAlone() {
        val m = model()
        m.open(Place.WORLD, true)
        m.worldSection = app.pokeplan.ui.world.WorldSection.EXPLORE
        m.open(Place.HOME, true)
        assertEquals(Module.WORLD, m.module, "going Home does not close the module that is open")
    }
}
