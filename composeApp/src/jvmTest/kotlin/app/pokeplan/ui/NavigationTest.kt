package app.pokeplan.ui

import app.pokeplan.app.storage.Clock
import app.pokeplan.app.GameStore
import app.pokeplan.app.storage.MemoryStorage
import app.pokeplan.ui.care.CareSection
import app.pokeplan.ui.screens.Module
import app.pokeplan.ui.screens.PlanSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where each place the app can be sent (a widget, shortcut or notification) lands: the right tab, module and section. */
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
        model().apply { open(Place.LINK, true); assertEquals(Module.LINK, module); assertNull(linkSection) }
        model().apply { open(Place.WORLD, true); assertEquals(Module.WORLD, module); assertNull(worldSection) }
        model().apply { open(Place.PARTY, true); assertTrue(openParty) }
        model().apply { open(Place.GEAR, true); assertTrue(gearOpen) }
    }

    @Test
    fun theNamesWidgetsAndShortcutsSendResolveToPlaces() {
        // The Android side looks a place up by its name; every name it uses must exist.
        listOf("PLANNER", "FOCUS", "CARE", "HEAL", "MINT", "BASE", "LINK", "WORLD", "ME", "HOME", "COLLECTION", "GEAR").forEach { name ->
            assertTrue(Place.entries.any { it.name == name }, "$name is a place")
        }
    }

    @Test
    fun openingAModuleTwiceKeepsItsPlaceAndHomeLeavesTheModulesAlone() {
        val m = model()
        m.open(Place.WORLD, true)
        m.worldSection = app.pokeplan.ui.world.WorldSection.MAP
        m.open(Place.HOME, true)
        assertEquals(Module.WORLD, m.module, "going Home does not close the module that is open")
    }
}
