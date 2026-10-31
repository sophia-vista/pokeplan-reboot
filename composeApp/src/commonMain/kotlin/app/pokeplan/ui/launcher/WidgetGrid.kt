package app.pokeplan.ui.launcher

/** The two pages widgets can stand on: the home page (over the town) and Today. */
enum class WidgetPage { HOME, TODAY }

/** How small, how large and how big at first a widget of a kind may be, in grid cells (4 columns wide; a row is as tall as a column is wide). */
class WidgetLimits(val minW: Int, val minH: Int, val maxW: Int, val maxH: Int, val defW: Int, val defH: Int)

fun WidgetKind.limits(): WidgetLimits = when (this) {
    WidgetKind.CLOCK -> WidgetLimits(2, 1, 4, 2, 4, 1)
    WidgetKind.STATUS, WidgetKind.BUDDY -> WidgetLimits(2, 2, 4, 3, 4, 2)
    WidgetKind.NAV -> WidgetLimits(2, 1, 4, 2, 4, 2)
    WidgetKind.PINNED, WidgetKind.OFTEN, WidgetKind.FOLDER -> WidgetLimits(2, 1, 4, 4, 4, 2)
    WidgetKind.ACTIONS -> WidgetLimits(2, 1, 4, 2, 4, 2)
    WidgetKind.CALENDAR -> WidgetLimits(4, 2, 4, 4, 4, 3)
    WidgetKind.AGENDA -> WidgetLimits(2, 2, 4, 6, 4, 3)
    WidgetKind.FORECAST -> WidgetLimits(2, 1, 4, 3, 4, 2)
    WidgetKind.ANDROID -> WidgetLimits(1, 1, 4, 8, 4, 2)
}

/**
 * One widget on a page's grid: its [kind] (and a folder's name or an Android widget's id as [arg]), its top-left cell
 * ([col], [row]) and its size in cells ([w] wide, [h] tall). Rows grow downwards without end.
 */
data class GridWidget(val page: WidgetPage, val kind: WidgetKind, val arg: String = "", val col: Int = 0, val row: Int = 0, val w: Int = 4, val h: Int = 2) {
    /** The identity of a widget, whatever its place or size. */
    fun sameAs(o: GridWidget) = page == o.page && kind == o.kind && arg == o.arg
    val right: Int get() = col + w
    val bottom: Int get() = row + h
    fun overlaps(o: GridWidget) = page == o.page && col < o.right && o.col < right && row < o.bottom && o.row < bottom
}

/**
 * The layout rules of the launcher's widget grid (4 columns, like Smart Launcher's): where a new widget goes, moving one
 * (what it bumps moves down out of the way), resizing between its limits, and turning the old stacked layout into a grid.
 * Pure, so it is tested without a screen; the screens only draw it and report where a finger dropped a widget.
 */
object WidgetGrid {
    const val COLUMNS = 4

    fun onPage(all: List<GridWidget>, page: WidgetPage) = all.filter { it.page == page }

    private fun clamped(w: GridWidget): GridWidget {
        val l = w.kind.limits()
        val width = w.w.coerceIn(l.minW, minOf(l.maxW, COLUMNS))
        val height = w.h.coerceIn(l.minH, l.maxH)
        return w.copy(w = width, h = height, col = w.col.coerceIn(0, COLUMNS - width), row = maxOf(0, w.row))
    }

    /**
     * Makes room: keeping [fixed] where it is, any other widget it overlaps goes down below it, and what that bumps goes down
     * in turn. Widgets are settled top to bottom, so the order they had is kept.
     */
    private fun settle(all: List<GridWidget>, fixed: GridWidget): List<GridWidget> {
        val others = all.filterNot { it.sameAs(fixed) }.sortedWith(compareBy({ it.page }, { it.row }, { it.col }))
        val placed = mutableListOf(fixed)
        for (w in others) {
            var at = w
            if (w.page == fixed.page) {
                while (true) {
                    val hit = placed.filter { it.overlaps(at) }.maxByOrNull { it.bottom } ?: break
                    at = at.copy(row = hit.bottom)
                }
            }
            placed += at
        }
        return placed.sortedWith(compareBy({ it.page }, { it.row }, { it.col }))
    }

    /** Adds [widget] (its own size, within limits) in the first free spot of its page, scanning from the top left; none is added twice. */
    fun add(all: List<GridWidget>, widget: GridWidget): List<GridWidget> {
        if (all.any { it.sameAs(widget) }) return all
        val sized = clamped(widget)
        var row = 0
        while (true) {
            for (col in 0..COLUMNS - sized.w) {
                val at = sized.copy(col = col, row = row)
                if (all.none { it.overlaps(at) }) return all + at
            }
            row++
        }
    }

    fun remove(all: List<GridWidget>, widget: GridWidget) = all.filterNot { it.sameAs(widget) }

    /** Drops [widget] with its top-left cell at ([col], [row]); anything in the way goes down. */
    fun move(all: List<GridWidget>, widget: GridWidget, col: Int, row: Int): List<GridWidget> {
        val current = all.firstOrNull { it.sameAs(widget) } ?: return all
        return settle(all, clamped(current.copy(col = col, row = row)))
    }

    /** Gives [widget] the size [w] by [h] cells, within its kind's limits; it stays at its top-left cell (shifting left only to stay on the grid) and anything in the way goes down. */
    fun resize(all: List<GridWidget>, widget: GridWidget, w: Int, h: Int): List<GridWidget> {
        val current = all.firstOrNull { it.sameAs(widget) } ?: return all
        return settle(all, clamped(current.copy(w = w, h = h)))
    }

    /** The rows a page needs: below its lowest widget. */
    fun rows(all: List<GridWidget>, page: WidgetPage) = onPage(all, page).maxOfOrNull { it.bottom } ?: 0

    /**
     * The grid for a launcher that has only the old stacked layout: each page's widgets keep their order, one under the
     * other and full width; a widget's old size (Small, Medium, Large) becomes a height between its limits. On the home page
     * the bottom widgets end at row [homeRows], as they sat at the foot of the screen, with the town between.
     */
    fun fromSlots(slots: List<WidgetSlot>, homeRows: Int = 12): List<GridWidget> {
        fun heightOf(s: WidgetSlot): Int {
            val l = s.kind.limits()
            return when (s.size) {
                WidgetSize.LARGE -> l.defH
                WidgetSize.MEDIUM -> (l.defH - 1).coerceAtLeast(l.minH)
                WidgetSize.SMALL -> l.minH
            }
        }
        fun GridWidget.of(s: WidgetSlot, row: Int) = clamped(copy(row = row, w = COLUMNS, h = heightOf(s)))
        fun stack(list: List<WidgetSlot>, page: WidgetPage, from: Int): List<GridWidget> {
            var row = from
            return list.map { s -> GridWidget(page, s.kind, s.arg).of(s, row).also { row = it.bottom } }
        }
        val top = stack(slots.filter { it.area == WidgetArea.HOME_TOP }, WidgetPage.HOME, 0)
        val bottomSlots = slots.filter { it.area == WidgetArea.HOME_BOTTOM }
        val bottomHeight = bottomSlots.sumOf { heightOf(it) }
        val bottom = stack(bottomSlots, WidgetPage.HOME, maxOf(top.maxOfOrNull { it.bottom } ?: 0, homeRows - bottomHeight))
        return top + bottom + stack(slots.filter { it.area == WidgetArea.TODAY }, WidgetPage.TODAY, 0)
    }
}
