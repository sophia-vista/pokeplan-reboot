package app.pokeplan.ui.launcher

import app.pokeplan.ui.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostedWidgetSpecTest {
    private val column = 82f

    @Test
    fun startsAtTheRoomItAskedForRoundedUpSoNothingIsClipped() {
        val l = HostedWidgetSpec(250, 110).limits(column)
        assertEquals(4, l.defW)
        assertEquals(4, l.defH)
        assertTrue(l.defW * column - 8 >= 250)
        assertTrue(l.defH * WidgetGrid.ROW_DP - 8 >= 110)
    }

    @Test
    fun aWidgetThatCannotBeResizedStaysAtItsStartingSize() {
        val l = HostedWidgetSpec(110, 110, resizeHorizontal = false, resizeVertical = false).limits(column)
        assertEquals(l.defW, l.minW)
        assertEquals(l.defW, l.maxW)
        assertEquals(l.defH, l.minH)
        assertEquals(l.defH, l.maxH)
    }

    @Test
    fun resizingStopsAtTheProvidersMinimumAndMaximum() {
        val l = HostedWidgetSpec(200, 250, minResizeWidthDp = 80, minResizeHeightDp = 70, maxResizeWidthDp = 300, maxResizeHeightDp = 400).limits(column)
        assertEquals(2, l.minW)
        assertEquals(3, l.minH)
        assertEquals(3, l.maxW)
        assertEquals(11, l.maxH)
        assertTrue(l.defW <= l.maxW && l.defW >= l.minW)
        assertTrue(l.defH <= l.maxH && l.defH >= l.minH)
    }

    @Test
    fun noMaximumMeansTheWholeGridButNeverBelowTheStart() {
        val l = HostedWidgetSpec(40, 40).limits(column)
        assertEquals(WidgetGrid.COLUMNS, l.maxW)
        assertEquals(16, l.maxH)
        assertTrue(l.minH >= 2)
    }

    @Test
    fun aProviderMaximumBelowItsStartDoesNotInvertTheLimits() {
        val l = HostedWidgetSpec(250, 250, maxResizeWidthDp = 100, maxResizeHeightDp = 100).limits(column)
        assertTrue(l.maxW >= l.defW && l.maxH >= l.defH)
    }

    @Test
    fun theGridResizesAnotherAppsWidgetOnlyWithinItsProvidersLimits() {
        val limits = HostedWidgetSpec(110, 110, minResizeWidthDp = 110, minResizeHeightDp = 110, maxResizeWidthDp = 200, maxResizeHeightDp = 200).limits(column)
        val w = GridWidget(WidgetPage.HOME, WidgetKind.ANDROID, "7", 0, 0, limits.defW, limits.defH)
        val bigger = WidgetGrid.resize(listOf(w), w, 4, 16, limits).single()
        assertEquals(limits.maxW, bigger.w)
        assertEquals(limits.maxH, bigger.h)
        val smaller = WidgetGrid.resize(listOf(w), w, 1, 1, limits).single()
        assertEquals(limits.minW, smaller.w)
        assertEquals(limits.minH, smaller.h)
        assertEquals(limits.maxW to limits.maxH, WidgetGrid.resizedTo(w, 10_000f, 10_000f, 82f, 36f, limits))
    }
}

class HostedWidgetIdsTest {
    @Test
    fun everyIdTheLayoutsNameIsKeptWhateverThePage() {
        val prefs = LauncherPrefs()
            .addGridWidget(WidgetPage.HOME, WidgetKind.ANDROID, "12")
            .addGridWidget(WidgetPage.TODAY, WidgetKind.ANDROID, "15")
            .addPage(left = false).let { it.addGridWidget(it.pageOrder.last(), WidgetKind.ANDROID, "18") }
        assertEquals(setOf(12, 15, 18), prefs.hostedWidgetIds())
        assertEquals(emptySet(), LauncherPrefs().hostedWidgetIds())
    }

    @Test
    fun aNewHostedWidgetStartsAtTheSizeItsProviderAsked() {
        val limits = HostedWidgetSpec(250, 110).limits(82f)
        val prefs = LauncherPrefs().let { it.addPage(left = false) }.let { it.addGridWidget(it.pageOrder.last(), WidgetKind.ANDROID, "9", limits) }
        val w = prefs.gridWidgets.single { it.kind == WidgetKind.ANDROID }
        assertEquals(limits.defW to limits.defH, w.w to w.h)
    }
}

class LauncherSearchTest {
    @Test
    fun findsModulesByNameAndKeywordNamesFirst() {
        assertEquals(listOf(Place.MINT), LauncherSearch.find("mint").map { it.place })
        assertEquals(Place.HEAL, LauncherSearch.find("sleep").single().place)
        assertEquals(Place.BASE, LauncherSearch.find("chores").single().place)
        assertTrue(LauncherSearch.find("POKÉMON").any { it.place == Place.COLLECTION })
        val found = LauncherSearch.find("pl").map { it.label }
        assertEquals("Plan", found.first())
    }

    @Test
    fun aBlankQueryOrNonsenseFindsNothing() {
        assertEquals(emptyList(), LauncherSearch.find(""))
        assertEquals(emptyList(), LauncherSearch.find("   "))
        assertEquals(emptyList(), LauncherSearch.find("zzzzqq"))
    }

    @Test
    fun everyDestinationIsReachableByItsOwnName() {
        LauncherSearch.destinations.forEach { d -> assertTrue(LauncherSearch.find(d.label).any { it.place == d.place }, d.label) }
    }
}
