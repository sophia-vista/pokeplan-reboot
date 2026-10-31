package app.pokeplan.ui.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetGridTest {
    private fun w(kind: WidgetKind, col: Int, row: Int, wd: Int, h: Int, page: WidgetPage = WidgetPage.HOME) = GridWidget(page, kind, "", col, row, wd, h)
    private fun noOverlap(all: List<GridWidget>) = all.indices.all { i -> all.indices.none { j -> i != j && all[i].overlaps(all[j]) } }

    @Test
    fun newWidgetsTakeTheFirstFreeSpotAndNeverOverlap() {
        var g = emptyList<GridWidget>()
        g = WidgetGrid.add(g, w(WidgetKind.NAV, 0, 0, 2, 2))
        g = WidgetGrid.add(g, w(WidgetKind.OFTEN, 3, 3, 2, 2))
        g = WidgetGrid.add(g, w(WidgetKind.ACTIONS, 0, 0, 4, 2))
        assertEquals(0 to 0, g[0].col to g[0].row)
        assertEquals(2 to 0, g[1].col to g[1].row, "beside the first, not where it was dropped")
        assertEquals(0 to 2, g[2].col to g[2].row, "a full-width widget goes below")
        assertTrue(noOverlap(g))
    }

    @Test
    fun noWidgetIsAddedTwice() {
        val g = WidgetGrid.add(emptyList(), w(WidgetKind.NAV, 0, 0, 4, 2))
        assertEquals(g, WidgetGrid.add(g, w(WidgetKind.NAV, 1, 5, 2, 1)))
    }

    @Test
    fun movingOntoAnotherPushesItDown() {
        val a = w(WidgetKind.NAV, 0, 0, 4, 2)
        val b = w(WidgetKind.OFTEN, 0, 2, 4, 2)
        val c = w(WidgetKind.ACTIONS, 0, 4, 4, 2)
        val moved = WidgetGrid.move(listOf(a, b, c), c, 0, 0)
        assertTrue(noOverlap(moved))
        assertEquals(0, moved.first { it.kind == WidgetKind.ACTIONS }.row)
        assertEquals(2, moved.first { it.kind == WidgetKind.NAV }.row)
        assertEquals(4, moved.first { it.kind == WidgetKind.OFTEN }.row)
    }

    @Test
    fun aWidgetCannotLeaveTheGrid() {
        val a = w(WidgetKind.NAV, 0, 0, 3, 2)
        val moved = WidgetGrid.move(listOf(a), a, 9, -4).single()
        assertEquals(1, moved.col, "kept on the grid: 4 columns, 3 wide")
        assertEquals(0, moved.row)
    }

    @Test
    fun resizingStaysBetweenTheLimitsAndBumpsTheRest() {
        val cal = w(WidgetKind.CALENDAR, 0, 0, 4, 6)
        val agenda = w(WidgetKind.AGENDA, 0, 6, 4, 6)
        val big = WidgetGrid.resize(listOf(cal, agenda), cal, 9, 9)
        val c = big.first { it.kind == WidgetKind.CALENDAR }
        assertEquals(4 to 8, c.w to c.h, "the calendar is at most 4 by 8")
        assertEquals(8, big.first { it.kind == WidgetKind.AGENDA }.row)
        val small = WidgetGrid.resize(listOf(cal), cal, 1, 1).single()
        assertEquals(4 to 4, small.w to small.h, "and at least 4 by 4")
        assertTrue(noOverlap(big))
    }

    @Test
    fun resizingNearTheRightEdgeShiftsLeftToStayOnTheGrid() {
        val a = w(WidgetKind.OFTEN, 3, 0, 2, 2)
        val g = WidgetGrid.resize(listOf(a), a, 3, 2).single()
        assertEquals(1, g.col)
        assertEquals(3, g.w)
    }

    @Test
    fun pagesDoNotPushEachOther() {
        val home = w(WidgetKind.NAV, 0, 0, 4, 2)
        val today = w(WidgetKind.AGENDA, 0, 0, 4, 3, WidgetPage.TODAY)
        val moved = WidgetGrid.move(listOf(home, today), home, 0, 0)
        assertEquals(0, moved.first { it.page == WidgetPage.TODAY }.row)
    }

    @Test
    fun theOldStackBecomesAGridInTheSameOrderWithBottomWidgetsAtTheFoot() {
        val slots = defaultWidgets()
        val grid = WidgetGrid.fromSlots(slots, homeRows = 20)
        assertTrue(noOverlap(grid))
        assertEquals(slots.size, grid.size)
        val home = WidgetGrid.onPage(grid, WidgetPage.HOME)
        val order = home.sortedBy { it.row }.map { it.kind }
        assertEquals(listOf(WidgetKind.CLOCK, WidgetKind.BUDDY, WidgetKind.NAV, WidgetKind.OFTEN, WidgetKind.PINNED, WidgetKind.ACTIONS), order)
        assertEquals(20, WidgetGrid.rows(grid, WidgetPage.HOME), "the bottom widgets end at the foot")
        assertTrue(home.all { it.w == WidgetGrid.COLUMNS })
        assertEquals(listOf(WidgetKind.CALENDAR, WidgetKind.AGENDA), WidgetGrid.onPage(grid, WidgetPage.TODAY).sortedBy { it.row }.map { it.kind })
    }

    @Test
    fun smallerOldSizesGiveShorterWidgets() {
        val large = WidgetGrid.fromSlots(listOf(WidgetSlot(WidgetArea.TODAY, WidgetKind.AGENDA, size = WidgetSize.LARGE))).single()
        val small = WidgetGrid.fromSlots(listOf(WidgetSlot(WidgetArea.TODAY, WidgetKind.AGENDA, size = WidgetSize.SMALL))).single()
        assertTrue(small.h < large.h)
    }

    @Test
    fun theGridIsSavedAndReadBack() {
        val prefs = LauncherPrefs().withGrid(listOf(w(WidgetKind.NAV, 1, 2, 3, 2), GridWidget(WidgetPage.TODAY, WidgetKind.FOLDER, "Games", 0, 0, 4, 2)))
        val back = LauncherPrefs.decode(prefs.encode())
        assertEquals(prefs.grid, back.grid)
    }

    @Test
    fun anEmptyGridIsSavedAndASavedStackStillShowsAsAGrid() {
        assertEquals(emptyList(), LauncherPrefs.decode(LauncherPrefs().withGrid(emptyList()).encode()).grid)
        val old = LauncherPrefs.decode("G HOME_TOP\tCLOCK\t\tLARGE\nG TODAY\tAGENDA\t\tSMALL\n")
        assertEquals(null, old.grid)
        assertEquals(2, old.gridWidgets.size)
    }

    @Test
    fun damagedGridLinesAreSkipped() {
        val back = LauncherPrefs.decode("X HOME\tNAV\t\t1\t2\t3\nX NOPE\tNAV\t\t0\t0\t4\t2\nX HOME\tNAV\t\t0\t0\t4\t2\n")
        assertEquals(1, back.grid?.size)
        assertFalse(back.grid.isNullOrEmpty())
    }
}

class WidgetGridSlotTest {
    @Test
    fun aGridWidgetsHeightPicksTheOldSize() {
        val l = WidgetKind.AGENDA.limits()
        fun at(h: Int) = GridWidget(WidgetPage.TODAY, WidgetKind.AGENDA, "", 0, 0, 4, h).asSlot().size
        assertEquals(WidgetSize.SMALL, at(l.minH))
        assertEquals(WidgetSize.MEDIUM, at(l.minH + 1))
        assertEquals(WidgetSize.LARGE, at(l.defH))
        assertEquals(WidgetSize.LARGE, at(l.maxH))
    }

    @Test
    fun theOldLayoutLaidOutAsAGridSurvivesTheRoundTrip() {
        val grid = WidgetGrid.fromSlots(defaultWidgets())
        grid.forEach { w -> assertEquals(w.kind, w.asSlot().kind) }
        assertEquals(grid, LauncherPrefs.decode(LauncherPrefs().withGrid(grid).encode()).grid)
    }
}

class WidgetGridDragTest {
    private val nav = GridWidget(WidgetPage.HOME, WidgetKind.NAV, "", 1, 2, 2, 3)

    @Test
    fun aDropSnapsToTheNearestCell() {
        assertEquals(2 to 3, WidgetGrid.droppedAt(nav, 60f, 50f, 100f, 40f))
        assertEquals(1 to 2, WidgetGrid.droppedAt(nav, 40f, 15f, 100f, 40f))
        assertEquals(0 to 0, WidgetGrid.droppedAt(nav, -900f, -900f, 100f, 40f), "never off the top left")
        assertEquals(2 to 2, WidgetGrid.droppedAt(nav, 900f, 0f, 100f, 40f), "nor past the right edge: 4 columns, 2 wide")
    }

    @Test
    fun aResizeSnapsAndStaysWithinTheLimitsAndTheGrid() {
        assertEquals(3 to 4, WidgetGrid.resizedTo(nav, 100f, 40f, 100f, 40f))
        assertEquals(3 to 3, WidgetGrid.resizedTo(nav, 900f, 0f, 100f, 40f), "at most the columns left of it")
        assertEquals(2 to 2, WidgetGrid.resizedTo(nav, -900f, -900f, 100f, 40f), "at least the kind's minimum")
    }

    @Test
    fun addingAndRemovingKeepTheGridAndTheLayoutTogether() {
        val base = LauncherPrefs().withGrid(WidgetGrid.fromSlots(defaultWidgets()))
        val added = base.addWidget(WidgetSlot(WidgetArea.TODAY, WidgetKind.FORECAST))
        assertTrue(added.grid!!.any { it.kind == WidgetKind.FORECAST && it.page == WidgetPage.TODAY })
        val gone = added.removeWidget(WidgetSlot(WidgetArea.TODAY, WidgetKind.FORECAST))
        assertTrue(gone.grid!!.none { it.kind == WidgetKind.FORECAST })
        val dropped = base.removeGridWidget(base.grid!!.first { it.kind == WidgetKind.NAV })
        assertTrue(dropped.grid!!.none { it.kind == WidgetKind.NAV })
        assertTrue(dropped.widgets.none { it.kind == WidgetKind.NAV })
    }
}
