package app.pokeplan.ui.launcher

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** A square of a [DungeonFloor]: column [x], row [y]. */
data class Cell(val x: Int, val y: Int)

/**
 * The gentle traps of Explorers of Sky a floor can hide (LAUNCHER.md 4.5), by their index in `assets/tiles/floor-traps.png` (the game's
 * own trap numbers). What each does is for show only: see [Town].
 */
enum class FloorTrap(val tile: Int) {
    WARP(6), SPIN(8), SLUMBER(9), GUST(7), SLOW(10), TRIP(22),

    /** Always shown, as in the games. */
    WONDER(17),
}

/** The stairs tile in `floor-traps.png`. */
internal const val STAIRS_TILE = 27

/** How a Pokémon gets about: on the floor only, wading into water too, or flying over walls as well. */
enum class Mover { WALKER, WADER, FLIER }

/**
 * A Mystery Dungeon floor for the launcher's town (LAUNCHER.md 4.5, REP-052): rooms joined by corridors across [screens] pages of
 * [colsPerScreen] squares, walls everywhere else, a pond in some rooms and water in some walls, the stairs, hidden traps and the spots
 * where the day's finds lie. Made from a seed, so the same seed always gives the same floor. Nothing is drawn here: [map] says what
 * each square is, and the tiles are the game's own ([TownMap.atlas], or the full autotile set when [organic]).
 */
class DungeonFloor(
    val map: TownMap,
    val rooms: List<Room>,
    val stairs: Cell,
    val traps: Map<Cell, FloorTrap>,
    val finds: List<Cell>,
    val organic: Boolean,
    private val soft: (Cell) -> Boolean,
) {
    data class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
        operator fun contains(c: Cell) = c.x in x until x + w && c.y in y until y + h
        val cells: List<Cell> get() = (y until y + h).flatMap { r -> (x until x + w).map { Cell(it, r) } }
    }

    val cols: Int get() = map.cols
    val rows: Int get() = map.rows

    fun inside(c: Cell) = c.x in 0 until cols && c.y in 0 until rows

    /** The floor and nothing else. */
    fun isFloor(c: Cell) = inside(c) && map.at(c.x, c.y) == TownCell.GROUND

    fun canStand(c: Cell, mover: Mover): Boolean {
        if (!inside(c)) return false
        return when (map.at(c.x, c.y)) {
            TownCell.GROUND -> true
            TownCell.WATER -> mover != Mover.WALKER
            TownCell.WALL -> mover == Mover.FLIER && c.y > 0 && c.y < rows - 1 && c.x > 0 && c.x < cols - 1
        }
    }

    /** Whether a widget covers [c]: nobody stops there, and a path goes round it when it can. */
    fun isSoft(c: Cell) = soft(c)

    /** The room [c] is in, if any. */
    fun roomOf(c: Cell): Room? = rooms.firstOrNull { c in it }

    /** Every square [mover] could stop on: not under a widget, not the stairs. */
    fun restingCells(mover: Mover): List<Cell> = (0 until rows).flatMap { y -> (0 until cols).map { Cell(it, y) } }
        .filter { canStand(it, mover) && !isSoft(it) && it != stairs && (mover != Mover.FLIER || map.at(it.x, it.y) != TownCell.WALL || isFloorNear(it)) }

    private fun isFloorNear(c: Cell) = (-1..1).any { dy -> (-1..1).any { dx -> isFloor(Cell(c.x + dx, c.y + dy)) } }

    /**
     * The shortest way from [from] to [to] for [mover], in eight directions without cutting a wall's corner, a square under a widget costing
     * [SOFT_COST] times as much (so a path goes round one when it can); null if there is none. Both ends are included.
     */
    fun path(from: Cell, to: Cell, mover: Mover): List<Cell>? {
        if (!canStand(to, mover) || !inside(from)) return null
        if (from == to) return listOf(from)
        val n = cols * rows
        val cost = FloatArray(n) { Float.MAX_VALUE }
        val came = IntArray(n) { -1 }
        val open = ArrayList<Pair<Float, Int>>()
        fun id(c: Cell) = c.y * cols + c.x
        fun h(c: Cell): Float { val dx = abs(c.x - to.x); val dy = abs(c.y - to.y); return max(dx, dy) + 0.414f * min(dx, dy) }
        cost[id(from)] = 0f
        open += h(from) to id(from)
        while (open.isNotEmpty()) {
            var best = 0
            for (i in open.indices) if (open[i].first < open[best].first) best = i
            val (_, cur) = open.removeAt(best)
            if (cur == id(to)) break
            val c = Cell(cur % cols, cur / cols)
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val next = Cell(c.x + dx, c.y + dy)
                // The start may be off the floor (warped, or a wall a flier rests on): leaving it is always allowed.
                if (!canStand(next, mover)) continue
                if (dx != 0 && dy != 0 && (!canStand(Cell(c.x + dx, c.y), mover) || !canStand(Cell(c.x, c.y + dy), mover))) continue
                val step = (if (dx != 0 && dy != 0) 1.414f else 1f) * (if (isSoft(next)) SOFT_COST else 1f) *
                    (if (mover == Mover.FLIER && map.at(next.x, next.y) == TownCell.WALL) 1.5f else 1f)
                val nc = cost[cur] + step
                if (nc < cost[id(next)]) {
                    cost[id(next)] = nc
                    came[id(next)] = cur
                    open += (nc + h(next)) to id(next)
                }
            }
        }
        if (came[id(to)] == -1) return null
        val way = ArrayList<Cell>()
        var at = id(to)
        while (at != -1) { way += Cell(at % cols, at / cols); at = came[at] }
        return way.asReversed()
    }

    /** [way] with only its turns kept (and both ends): one stroll per straight run. */
    fun turns(way: List<Cell>): List<Cell> {
        if (way.size <= 2) return way
        val out = arrayListOf(way.first())
        for (i in 1 until way.size - 1) {
            val a = way[i - 1]; val b = way[i]; val c = way[i + 1]
            if (b.x - a.x != c.x - b.x || b.y - a.y != c.y - b.y) out += b
        }
        out += way.last()
        return out
    }

    /** Every floor square [from] can reach on foot. */
    fun reachable(from: Cell): Set<Cell> {
        val seen = HashSet<Cell>()
        val queue = ArrayDeque<Cell>()
        if (isFloor(from)) { seen += from; queue += from }
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            for ((dx, dy) in STEPS) {
                val n = Cell(c.x + dx, c.y + dy)
                if (isFloor(n) && seen.add(n)) queue += n
            }
        }
        return seen
    }

    companion object {
        /** How much more a square under a widget costs to walk through. */
        const val SOFT_COST = 8f

        /** How many traps a floor hides (and a Wonder Tile half the time). *(tune)* */
        const val TRAPS = 4

        private val STEPS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        /**
         * Lays a floor out: [screens] pages side by side, each [colsPerScreen] squares wide, [rows] tall; [home] is the page the stairs and
         * the finds favour. [soft] says which squares a widget covers. [organic] when the tileset has its full autotile set: then water may
         * take any shape and walls may be thin; otherwise every wall is at least two squares thick and water is a rectangle.
         */
        fun build(
            screens: Int, colsPerScreen: Int, rows: Int, seed: Long, home: Int = 0, organic: Boolean = true,
            finds: Int = 3, soft: (Cell) -> Boolean = { false },
        ): DungeonFloor {
            val random = Random(seed)
            val cols = screens * colsPerScreen
            val cells = Array(cols * rows) { TownCell.WALL }
            fun set(c: Cell, t: TownCell) { if (c.x in 1 until cols - 1 && c.y in 1 until rows - 1) cells[c.y * cols + c.x] = t }
            fun at(c: Cell) = if (c.x in 0 until cols && c.y in 0 until rows) cells[c.y * cols + c.x] else TownCell.WALL

            // Rooms: two columns a page, two rooms in each (three on a tall screen), big, as on the games' floors; now and then one as wide as the
            // page (owner, 14 November 2026: bigger rooms). A room may sit anywhere down its column, and of a few tries the one that shows the most
            // floor no widget covers wins, so the rooms fill the open bands between the widgets. Rooms keep two squares of wall between them.
            val columns = screens * 2
            val cw = cols / columns.toFloat()
            val perColumn = if (rows >= 36) 3 else 2
            val made = ArrayList<Room>()
            fun clear(r: Room) = made.none { o -> r.x - 2 < o.x + o.w && o.x - 2 < r.x + r.w && r.y - 2 < o.y + o.h && o.y - 2 < r.y + r.h }
            for (column in 0 until columns) {
                val wide = column % 2 == 0 && random.nextInt(100) < 30
                if (column % 2 == 1 && made.any { it.w > cw * 1.5f && it.x < (column * cw).toInt() && it.x + it.w > (column * cw).toInt() + 1 } && random.nextBoolean()) continue
                val x0 = (column * cw).toInt() + 1
                val x1 = (((column + if (wide) 2 else 1) * cw).toInt() - 1).coerceAtMost(cols - 2)
                repeat(if (wide) perColumn - 1 else perColumn) {
                    var best: Room? = null
                    var bestScore = Float.NEGATIVE_INFINITY
                    repeat(30) {
                        val aw = x1 - x0
                        val w = random.nextInt(max(3, (aw * 0.7f).toInt()), max(4, aw + 1))
                        val h = random.nextInt(4, max(5, min(rows / perColumn - 3, 10) + 1))
                        val x = random.nextInt(x0, max(x0 + 1, x1 - w + 1))
                        val y = random.nextInt(2, max(3, rows - 2 - h))
                        val r = Room(x, y, min(w, cols - 1 - x), min(h, rows - 1 - y))
                        if (r.w < 3 || r.h < 3 || !clear(r)) return@repeat
                        val covered = r.cells.count(soft)
                        val score = (r.cells.size - covered) - 0.4f * covered + random.nextFloat() * 4f
                        if (score > bestScore) { best = r; bestScore = score }
                    }
                    best?.let { r -> made += r; r.cells.forEach { set(it, TownCell.GROUND) } }
                }
            }
            if (made.isEmpty()) Room(2, 2, min(6, cols - 4), min(5, rows - 4)).let { r -> made += r; r.cells.forEach { set(it, TownCell.GROUND) } }

            // Corridors: the shortest links that join every room (so all of the floor is one piece), and a few more for loops.
            fun corridor(a: Cell, b: Cell) {
                val bend = if (random.nextBoolean()) Cell(b.x, a.y) else Cell(a.x, b.y)
                for (leg in listOf(a to bend, bend to b)) {
                    val (p, q) = leg
                    var x = p.x; var y = p.y
                    set(Cell(x, y), TownCell.GROUND)
                    while (x != q.x || y != q.y) {
                        x += (q.x - x).coerceIn(-1, 1); y += (q.y - y).coerceIn(-1, 1)
                        if (at(Cell(x, y)) != TownCell.GROUND) set(Cell(x, y), TownCell.GROUND)
                    }
                }
            }
            fun door(r: Room) = Cell(random.nextInt(r.x, r.x + r.w), random.nextInt(r.y, r.y + r.h))
            fun gap(a: Room, b: Room) = abs((a.x + a.w / 2) - (b.x + b.w / 2)) + abs((a.y + a.h / 2) - (b.y + b.h / 2))
            val joined = arrayListOf(made.first())
            val left = made.drop(1).toMutableList()
            while (left.isNotEmpty()) {
                var pair: Pair<Room, Room>? = null
                for (a in joined) for (b in left) if (pair == null || gap(a, b) < gap(pair.first, pair.second)) pair = a to b
                val (a, b) = pair!!
                corridor(door(a), door(b))
                joined += b; left -= b
            }
            repeat(max(1, made.size / 4)) {
                val a = made.random(random)
                made.filter { it != a }.minByOrNull { gap(a, it) + random.nextInt(8) }?.let { corridor(door(a), door(it)) }
            }

            // With only the 3 x 3 block a wall must be two squares thick: a wall with floor on both sides becomes floor.
            if (!organic) {
                var changed = true
                while (changed) {
                    changed = false
                    for (y in 1 until rows - 1) for (x in 1 until cols - 1) {
                        val c = Cell(x, y)
                        if (at(c) != TownCell.WALL) continue
                        val thinX = at(Cell(x - 1, y)) == TownCell.GROUND && at(Cell(x + 1, y)) == TownCell.GROUND
                        val thinY = at(Cell(x, y - 1)) == TownCell.GROUND && at(Cell(x, y + 1)) == TownCell.GROUND
                        if (thinX || thinY) { set(c, TownCell.GROUND); changed = true }
                    }
                }
            }

            // Water: a pond in some big rooms, kept off the room's edge so the room stays one piece; with the full tile set, lakes in the walls too.
            for (r in made) {
                if (r.w < 6 || r.h < 5 || random.nextInt(100) >= 35) continue
                val pw = random.nextInt(2, r.w - 3)
                val ph = random.nextInt(2, r.h - 2)
                val px = random.nextInt(r.x + 1, r.x + r.w - pw)
                val py = random.nextInt(r.y + 1, r.y + r.h - ph)
                for (y in py until py + ph) for (x in px until px + pw) {
                    // A round pond with the full set: the corners stay floor.
                    val corner = organic && (x == px || x == px + pw - 1) && (y == py || y == py + ph - 1) && pw > 2 && ph > 2
                    if (!corner) set(Cell(x, y), TownCell.WATER)
                }
            }
            if (organic) repeat(screens * 2) {
                var c = Cell(random.nextInt(1, cols - 1), random.nextInt(1, rows - 1))
                if (at(c) != TownCell.WALL) return@repeat
                repeat(random.nextInt(6, 18)) {
                    if (at(c) == TownCell.WALL) set(c, TownCell.WATER)
                    val (dx, dy) = STEPS[random.nextInt(4)]
                    val n = Cell(c.x + dx, c.y + dy)
                    if (at(n) != TownCell.GROUND) c = n
                }
            }

            val map = TownMap(cols, rows, cells)
            // Stairs, traps and finds lie in rooms, on floor no widget covers; the stairs and finds on Home if it has room.
            fun pick(from: List<Cell>, taken: Set<Cell>): Cell? = from.filter { at(it) == TownCell.GROUND && !soft(it) && it !in taken }.randomOrNull(random)
            val inRooms = made.flatMap { it.cells }
            val onHome = inRooms.filter { it.x / colsPerScreen == home }
            val taken = HashSet<Cell>()
            val stairs = pick(onHome, taken) ?: pick(inRooms, taken) ?: Cell(made.first().x, made.first().y)
            taken += stairs
            (-1..1).forEach { dy -> (-1..1).forEach { dx -> taken += Cell(stairs.x + dx, stairs.y + dy) } }
            val spots = ArrayList<Cell>()
            repeat(finds) { (pick(onHome, taken) ?: pick(inRooms, taken))?.let { spots += it; taken += it } }
            val traps = LinkedHashMap<Cell, FloorTrap>()
            val kinds = FloorTrap.entries.filter { it != FloorTrap.WONDER }
            repeat(TRAPS) { pick(inRooms, taken)?.let { traps[it] = kinds[random.nextInt(kinds.size)]; taken += it } }
            if (random.nextBoolean()) pick(inRooms, taken)?.let { traps[it] = FloorTrap.WONDER }
            return DungeonFloor(map, made, stairs, traps, spots, organic, soft)
        }
    }
}

/**
 * The full autotile set's column for a square of [map] ([TownCell.WALL] or [TownCell.WATER]): the 47 neighbourhoods of
 * `tools/tiles/extract_sky_tiles.py` (`blob_masks`), bits N 1, NE 2, E 4, SE 8, S 16, SW 32, W 64, NW 128 set where the neighbour is the
 * same, a diagonal kept only when both squares beside it are. Squares beyond the map count as the same, so walls run off the edge.
 */
internal object Autotile {
    val masks: IntArray = (0 until 256).map(::reduce).distinct().sorted().toIntArray()
    private val column = IntArray(256) { masks.indexOf(reduce(it)) }

    fun reduce(m: Int): Int {
        var r = m
        for ((d, a, b) in listOf(Triple(2, 1, 4), Triple(8, 4, 16), Triple(32, 16, 64), Triple(128, 64, 1))) if (r and a == 0 || r and b == 0) r = r and d.inv()
        return r
    }

    fun mask(map: TownMap, x: Int, y: Int): Int {
        val me = map.at(x, y)
        fun same(dx: Int, dy: Int): Boolean {
            val nx = x + dx; val ny = y + dy
            if (nx !in 0 until map.cols || ny !in 0 until map.rows) return true
            return map.at(nx, ny) == me
        }
        return (if (same(0, -1)) 1 else 0) or (if (same(1, -1)) 2 else 0) or (if (same(1, 0)) 4 else 0) or (if (same(1, 1)) 8 else 0) or
            (if (same(0, 1)) 16 else 0) or (if (same(-1, 1)) 32 else 0) or (if (same(-1, 0)) 64 else 0) or (if (same(-1, -1)) 128 else 0)
    }

    fun column(map: TownMap, x: Int, y: Int): Int = column[mask(map, x, y)]
}
