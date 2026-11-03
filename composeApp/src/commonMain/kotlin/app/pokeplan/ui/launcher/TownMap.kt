package app.pokeplan.ui.launcher

import kotlin.math.roundToInt
import kotlin.random.Random

/** What a square of a dungeon town is: open ground, wall (trees, rocks) or water. */
enum class TownCell { GROUND, WALL, WATER }

/**
 * A town built from a dungeon's tiles: a [cols] by [rows] map of [TownCell]s. A wall or water area is always a plain
 * rectangle at least two squares wide and tall, so its edge tiles come from the 3 x 3 block of the tileset (corners,
 * edges and middle) and need no other rules. Squares beyond the map count as the nearest square inside it, so an area
 * that runs off the screen shows no edge there.
 */
class TownMap(val cols: Int, val rows: Int, private val cells: Array<TownCell>) {
    fun at(x: Int, y: Int): TownCell = cells[y.coerceIn(0, rows - 1) * cols + x.coerceIn(0, cols - 1)]

    /**
     * Where the tile of square ([x], [y]) is in the 6 x 4 atlas of tools/tiles/cut_tiles.py: wall block at columns 0 to 2,
     * water block at 3 to 5 (rows 0 to 2), and on row 3 the ground tile and its two alternates, which a few ground
     * squares use (chosen from [seed], so a town always looks the same).
     */
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

/** Lays a dungeon town out: a wall band along the top, a pond and wall clumps below it, and more clumps at the bottom corners. */
object TownLayout {
    /** Where the town's Pokémon walk, as fractions of the screen's height: clear of every pond and clump. */
    const val WALK_TOP = 0.20f
    const val WALK_BOTTOM = 0.74f

    private class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        /** Whether this comes within one square of [o], which a new area must not (a square of ground stays between areas). */
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
        // The band runs off the screen on three sides, so only its lower edge shows.
        val band = (rows * 0.10f).roundToInt().coerceAtLeast(2)
        put(Rect(-2, -2, cols + 4, band + 2), TownCell.WALL)
        // Between the band and where the Pokémon walk: one pond and a clump or two.
        val upperEnd = (rows * 0.18f).toInt()
        tryPlace(TownCell.WATER, 3 + random.nextInt(3), 2, 0..cols - 5, band + 1..upperEnd - 2)
        repeat(2) { tryPlace(TownCell.WALL, 2 + random.nextInt(2), 2, -1..cols - 3, band + 1..upperEnd - 2) }
        // Below the Pokémon, behind the day's text: clumps at the corners, running off the screen.
        val lowerStart = (rows * 0.80f).toInt()
        repeat(3) {
            val w = 2 + random.nextInt(3)
            val xs = if (random.nextBoolean()) -1..1 else cols - w - 1..cols - 1
            tryPlace(TownCell.WALL, w, 2 + random.nextInt(2), xs, lowerStart..rows - 2)
        }
        return TownMap(cols, rows, cells)
    }
}

/**
 * A dungeon floor for a walk or a team's strip, from the same atlas as the town: one row of wall along the top (a pond in
 * a few places), [floorRows] rows of ground to walk on, then wall below with a few bays of ground cut into it. The floor
 * rows are always clear, so nobody walks through a wall; the bays, ponds and the ground alternates only break the
 * repetition. The same [seed] (from the dungeon and the floor) always gives the same floor. Nothing is drawn here: the
 * tiles are the game's own, picked by [TownMap.atlas].
 */
internal object DungeonFloorLayout {
    fun build(cols: Int, rows: Int, seed: Int, floorRows: Int): TownMap {
        val random = Random(seed)
        val cells = Array(cols * rows) { TownCell.GROUND }
        val below = floorRows + 1
        for (x in 0 until cols) {
            cells[x] = TownCell.WALL
            for (y in below until rows) cells[y * cols + x] = TownCell.WALL
        }
        // Bays: ground cut into the wall below (two to four squares wide, never at the map's edge, a square of wall between them).
        if (below < rows - 1) {
            var x = 2 + random.nextInt(3)
            while (x < cols - 5) {
                val w = 2 + random.nextInt(3)
                if (random.nextInt(100) < 55) for (c in x until minOf(x + w, cols - 2)) cells[below * cols + c] = TownCell.GROUND
                x += w + 2 + random.nextInt(4)
            }
        }
        // Ponds in the top wall (three squares wide), now and then.
        var x = 3 + random.nextInt(4)
        while (x < cols - 6) {
            if (random.nextInt(100) < 35) for (c in x until x + 3) cells[c] = TownCell.WATER
            x += 5 + random.nextInt(6)
        }
        return TownMap(cols, rows, cells)
    }
}
