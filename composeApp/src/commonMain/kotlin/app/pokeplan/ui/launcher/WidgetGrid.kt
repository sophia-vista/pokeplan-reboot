package app.pokeplan.ui.launcher

import kotlin.math.roundToInt

enum class WidgetPage(val label: String) {
    HOME("Home"), TODAY("Today"), PAGE3("Page 3"), PAGE4("Page 4"), PAGE5("Page 5");

    companion object { const val MAX = 5; const val MIN = 2 }
}

class WidgetLimits(val minW: Int, val minH: Int, val maxW: Int, val maxH: Int, val defW: Int, val defH: Int)

fun WidgetKind.limits(): WidgetLimits = when (this) {
    WidgetKind.CLOCK -> WidgetLimits(2, 2, 4, 3, 4, 2)
    WidgetKind.STATUS, WidgetKind.BUDDY -> WidgetLimits(2, 3, 4, 6, 4, 3)
    WidgetKind.NAV -> WidgetLimits(2, 2, 4, 4, 4, 3)
    WidgetKind.PINNED, WidgetKind.OFTEN, WidgetKind.FOLDER -> WidgetLimits(2, 2, 4, 8, 4, 3)
    WidgetKind.ACTIONS -> WidgetLimits(2, 2, 4, 4, 4, 3)
    WidgetKind.CALENDAR -> WidgetLimits(4, 4, 4, 10, 4, 8)
    WidgetKind.AGENDA -> WidgetLimits(2, 4, 4, 12, 4, 6)
    WidgetKind.FORECAST -> WidgetLimits(2, 2, 4, 6, 4, 3)
    WidgetKind.ANDROID -> WidgetLimits(1, 2, 4, 16, 4, 4)
}

data class GridWidget(val page: WidgetPage, val kind: WidgetKind, val arg: String = "", val col: Int = 0, val row: Int = 0, val w: Int = 4, val h: Int = 2) {
    fun sameAs(o: GridWidget) = page == o.page && kind == o.kind && arg == o.arg
    val right: Int get() = col + w
    val bottom: Int get() = row + h
    fun overlaps(o: GridWidget) = page == o.page && col < o.right && o.col < right && row < o.bottom && o.row < bottom
}

object WidgetGrid {
    const val COLUMNS = 4

    const val NOMINAL_COLUMN_DP = 82f

    const val CELL_GAP_DP = 4

    const val ROW_DP = 36

    fun onPage(all: List<GridWidget>, page: WidgetPage) = all.filter { it.page == page }

    private fun clamped(w: GridWidget, l: WidgetLimits = w.kind.limits()): GridWidget {
        val width = w.w.coerceIn(l.minW, minOf(l.maxW, COLUMNS))
        val height = w.h.coerceIn(l.minH, l.maxH)
        return w.copy(w = width, h = height, col = w.col.coerceIn(0, COLUMNS - width), row = maxOf(0, w.row))
    }

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

    fun move(all: List<GridWidget>, widget: GridWidget, col: Int, row: Int): List<GridWidget> {
        val current = all.firstOrNull { it.sameAs(widget) } ?: return all
        return settle(all, clamped(current.copy(col = col, row = row)))
    }

    fun resize(all: List<GridWidget>, widget: GridWidget, w: Int, h: Int, limits: WidgetLimits = widget.kind.limits()): List<GridWidget> {
        val current = all.firstOrNull { it.sameAs(widget) } ?: return all
        return settle(all, clamped(current.copy(w = w, h = h), limits))
    }

    fun toPage(all: List<GridWidget>, widget: GridWidget, page: WidgetPage): List<GridWidget> {
        val current = all.firstOrNull { it.sameAs(widget) } ?: return all
        if (current.page == page) return all
        return settle(all.filterNot { it.sameAs(current) }, clamped(current.copy(page = page)))
    }

    fun droppedAt(w: GridWidget, dxPx: Float, dyPx: Float, colPx: Float, rowPx: Float): Pair<Int, Int> =
        (w.col + (dxPx / colPx).roundToInt()).coerceIn(0, COLUMNS - w.w) to maxOf(0, w.row + (dyPx / rowPx).roundToInt())

    fun resizedTo(w: GridWidget, dxPx: Float, dyPx: Float, colPx: Float, rowPx: Float, l: WidgetLimits = w.kind.limits()): Pair<Int, Int> {
        val width = (w.w + (dxPx / colPx).roundToInt()).coerceIn(l.minW, minOf(l.maxW, COLUMNS - w.col))
        return width to (w.h + (dyPx / rowPx).roundToInt()).coerceIn(l.minH, l.maxH)
    }

    fun rows(all: List<GridWidget>, page: WidgetPage) = onPage(all, page).maxOfOrNull { it.bottom } ?: 0

    fun fromSlots(slots: List<WidgetSlot>, homeRows: Int = 16): List<GridWidget> {
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

fun GridWidget.asSlot(): WidgetSlot {
    val l = kind.limits()
    val size = when { h >= l.defH -> WidgetSize.LARGE; h <= l.minH -> WidgetSize.SMALL; else -> WidgetSize.MEDIUM }
    return WidgetSlot(if (page == WidgetPage.TODAY) WidgetArea.TODAY else WidgetArea.HOME_TOP, kind, arg, size)
}
