package app.pokeplan.ui.launcher

import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.roundToInt
import kotlin.random.Random

const val CORNER_TL = 1
const val CORNER_TR = 2
const val CORNER_BL = 4
const val CORNER_BR = 8

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInnerCorners(image: androidx.compose.ui.graphics.ImageBitmap, corners: Int, x: Int, y: Int, tile: Int, scale: Int) {
    if (corners == 0 || tile != 24) return
    val t = tile
    val rim = 9
    fun block(sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) = drawImage(
        image, androidx.compose.ui.unit.IntOffset(sx, sy), androidx.compose.ui.unit.IntSize(w, h),
        androidx.compose.ui.unit.IntOffset(x + dx * scale, y + dy * scale), androidx.compose.ui.unit.IntSize(w * scale, h * scale),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
    )
    if (corners and CORNER_TL != 0) { block(1, t, 10, rim, 1, 0); block(1, t, 1, 3, 0, 0); block(t + 23, 3, 1, 6, 0, 3) }
    if (corners and CORNER_TR != 0) { block(2 * t + 13, t, 10, rim, 13, 0); block(2 * t + 22, t, 1, 3, 23, 0); block(t, 3, 1, 6, 23, 3) }
    if (corners and CORNER_BL != 0) { block(1, t + 15, 10, rim, 1, 15); block(1, t + 21, 1, 3, 0, 21); block(t + 23, 2 * t + 15, 1, 6, 0, 15) }
    if (corners and CORNER_BR != 0) { block(2 * t + 13, t + 15, 10, rim, 13, 15); block(2 * t + 22, t + 21, 1, 3, 23, 21); block(t, 2 * t + 15, 1, 6, 23, 15) }
}

enum class TownCell { GROUND, WALL, WATER }

class TownMap(val cols: Int, val rows: Int, private val cells: Array<TownCell>) {
    fun at(x: Int, y: Int): TownCell = cells[y.coerceIn(0, rows - 1) * cols + x.coerceIn(0, cols - 1)]

    fun innerCorners(x: Int, y: Int): Int {
        val me = at(x, y)
        if (me != TownCell.WALL) return 0
        fun open(dx: Int, dy: Int) = at(x + dx, y) == me && at(x, y + dy) == me && at(x + dx, y + dy) != me
        return (if (open(-1, -1)) CORNER_TL else 0) or (if (open(1, -1)) CORNER_TR else 0) or (if (open(-1, 1)) CORNER_BL else 0) or (if (open(1, 1)) CORNER_BR else 0)
    }

    fun atlas(x: Int, y: Int, seed: Int): Pair<Int, Int> {
        val me = at(x, y)
        if (me == TownCell.GROUND) {
            val roll = ((x * 73856093) xor (y * 19349663) xor (seed * 83492791)).let { (it ushr 3) % 100 }
            return when {
                roll < 4 -> 1 to 3
                roll < 8 -> 2 to 3
                else -> 0 to 3
            }
        }
        val left = at(x - 1, y) != me
        val right = at(x + 1, y) != me
        val up = at(x, y - 1) != me
        val down = at(x, y + 1) != me
        val column = when { left && !right -> 0; right && !left -> 2; else -> 1 }
        val row = when { up && !down -> 0; down && !up -> 2; else -> 1 }
        return (if (me == TownCell.WATER) 3 + column else column) to row
    }
}

object TownLayout {
    const val WALK_TOP = 0.20f
    const val WALK_BOTTOM = 0.74f

    private class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun near(o: Rect) = x - 1 < o.x + o.w && o.x - 1 < x + w && y - 1 < o.y + o.h && o.y - 1 < y + h
    }

    fun build(cols: Int, rows: Int, seed: Int): TownMap {
        val random = Random(seed)
        val cells = Array(cols * rows) { TownCell.GROUND }
        val placed = ArrayList<Rect>()
        fun put(r: Rect, cell: TownCell) {
            placed += r
            for (y in r.y until r.y + r.h) for (x in r.x until r.x + r.w) if (x in 0 until cols && y in 0 until rows) cells[y * cols + x] = cell
        }
        fun tryPlace(cell: TownCell, w: Int, h: Int, xs: IntRange, ys: IntRange, tries: Int = 30) {
            if (ys.isEmpty() || xs.isEmpty()) return
            repeat(tries) {
                val r = Rect(random.nextInt(xs.first, xs.last + 1), random.nextInt(ys.first, ys.last + 1), w, h)
                if (placed.none { it.near(r) }) return put(r, cell)
            }
        }
        val band = (rows * 0.10f).roundToInt().coerceAtLeast(2)
        put(Rect(-2, -2, cols + 4, band + 2), TownCell.WALL)
        val upperEnd = (rows * 0.18f).toInt()
        tryPlace(TownCell.WATER, 3 + random.nextInt(3), 2, 0..cols - 5, band + 1..upperEnd - 2)
        repeat(2) { tryPlace(TownCell.WALL, 2 + random.nextInt(2), 2, -1..cols - 3, band + 1..upperEnd - 2) }
        val lowerStart = (rows * 0.80f).toInt()
        repeat(3) {
            val w = 2 + random.nextInt(3)
            val xs = if (random.nextBoolean()) -1..1 else cols - w - 1..cols - 1
            tryPlace(TownCell.WALL, w, 2 + random.nextInt(2), xs, lowerStart..rows - 2)
        }
        return TownMap(cols, rows, cells)
    }
}

internal object DungeonFloorLayout {
    fun build(cols: Int, rows: Int, seed: Int, floorRows: Int): TownMap {
        val random = Random(seed)
        val cells = Array(cols * rows) { TownCell.GROUND }
        val below = floorRows + 1
        for (x in 0 until cols) {
            cells[x] = TownCell.WALL
            for (y in below until rows) cells[y * cols + x] = TownCell.WALL
        }
        if (below < rows - 1) {
            var x = 2 + random.nextInt(3)
            while (x < cols - 5) {
                val w = 2 + random.nextInt(3)
                if (random.nextInt(100) < 55) for (c in x until minOf(x + w, cols - 2)) cells[below * cols + c] = TownCell.GROUND
                x += w + 2 + random.nextInt(4)
            }
        }
        return TownMap(cols, rows, cells)
    }
}

internal fun alternatesFit(image: androidx.compose.ui.graphics.ImageBitmap): BooleanArray {
    val map = image.toPixelMap()
    if (map.height < 96 || map.width < 72) return booleanArrayOf(true, false, false)
    fun difference(column: Int): Float {
        var sum = 0f
        for (y in 0 until 24) for (x in 0 until 24) {
            val a = map[x, 72 + y]; val b = map[column * 24 + x, 72 + y]
            sum += kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)
        }
        return sum / (24 * 24 * 3) * 255f
    }
    return booleanArrayOf(true, difference(1) < 20f, difference(2) < 20f)
}
