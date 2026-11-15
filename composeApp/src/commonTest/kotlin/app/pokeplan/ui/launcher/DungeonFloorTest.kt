package app.pokeplan.ui.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DungeonFloorTest {
    private fun floor(seed: Long, organic: Boolean = true, screens: Int = 3, soft: (Cell) -> Boolean = { false }) =
        DungeonFloor.build(screens, 15, 28, seed, home = 1, organic = organic, soft = soft)

    private fun floorCells(f: DungeonFloor) = (0 until f.rows).flatMap { y -> (0 until f.cols).map { Cell(it, y) } }.filter(f::isFloor)

    @Test
    fun everyFloorSquareIsReachedFromTheStairs() {
        for (seed in 0L until 60L) for (organic in listOf(true, false)) {
            val f = floor(seed, organic)
            assertTrue(f.isFloor(f.stairs), "stairs on the floor (seed $seed)")
            assertEquals(floorCells(f).toSet(), f.reachable(f.stairs), "one piece (seed $seed, organic $organic)")
        }
    }

    @Test
    fun theEdgesAreWallAndTheSameSeedGivesTheSameFloor() {
        val f = floor(7)
        for (x in 0 until f.cols) { assertEquals(TownCell.WALL, f.map.at(x, 0)); assertEquals(TownCell.WALL, f.map.at(x, f.rows - 1)) }
        for (y in 0 until f.rows) { assertEquals(TownCell.WALL, f.map.at(0, y)); assertEquals(TownCell.WALL, f.map.at(f.cols - 1, y)) }
        val g = floor(7)
        assertEquals(floorCells(f), floorCells(g))
        assertEquals(f.stairs, g.stairs)
        assertEquals(f.traps, g.traps)
        assertTrue(floorCells(floor(8)) != floorCells(f), "another seed, another floor")
    }

    @Test
    fun withOnlyTheThreeByThreeBlockNoWallIsOneSquareThick() {
        for (seed in 0L until 40L) {
            val f = floor(seed, organic = false)
            for (y in 1 until f.rows - 1) for (x in 1 until f.cols - 1) {
                if (f.map.at(x, y) != TownCell.WALL) continue
                val thinX = f.map.at(x - 1, y) == TownCell.GROUND && f.map.at(x + 1, y) == TownCell.GROUND
                val thinY = f.map.at(x, y - 1) == TownCell.GROUND && f.map.at(x, y + 1) == TownCell.GROUND
                assertTrue(!thinX && !thinY, "thin wall at $x,$y (seed $seed)")
            }
        }
    }

    @Test
    fun findsTrapsAndStairsLieOnOpenFloorAwayFromWidgets() {
        // A widget row across the bottom of every page and one at the top.
        val soft = { c: Cell -> c.y >= 19 || c.y in 2..4 }
        for (seed in 0L until 40L) {
            val f = floor(seed, soft = soft)
            assertEquals(3, f.finds.size)
            (f.finds + f.traps.keys + f.stairs).forEach { assertTrue(f.isFloor(it) && !soft(it), "$it in seed $seed") }
            assertEquals((f.finds + f.traps.keys + f.stairs).size, (f.finds + f.traps.keys + f.stairs).toSet().size, "one thing a square")
        }
    }

    @Test
    fun aPathStaysOnTheFloorAndGoesRoundWidgetsWhenItCan() {
        val f = floor(3)
        val cells = floorCells(f)
        val a = cells.first()
        val b = cells.last()
        val way = assertNotNull(f.path(a, b, Mover.WALKER))
        assertEquals(a, way.first()); assertEquals(b, way.last())
        assertTrue(way.all(f::isFloor))
        way.zipWithNext().forEach { (p, q) -> assertTrue(kotlin.math.abs(p.x - q.x) <= 1 && kotlin.math.abs(p.y - q.y) <= 1) }
        assertEquals(way.first(), f.turns(way).first()); assertEquals(way.last(), f.turns(way).last())
        // A wader may end in water; a walker may not.
        val water = (0 until f.rows).flatMap { y -> (0 until f.cols).map { Cell(it, y) } }.firstOrNull { f.map.at(it.x, it.y) == TownCell.WATER }
        if (water != null) assertEquals(null, f.path(a, water, Mover.WALKER))
    }

    @Test
    fun onlyGhostsPassWallsAndOnlySwimmersAndFliersGoInWaterAndNobodyRestsOnATrap() {
        val f = floor(11)
        val all = (0 until f.rows).flatMap { y -> (0 until f.cols).map { Cell(it, y) } }
        val wall = all.first { it.x in 1 until f.cols - 1 && it.y in 1 until f.rows - 1 && f.map.at(it.x, it.y) == TownCell.WALL }
        assertTrue(!f.canStand(wall, Mover.WALKER) && !f.canStand(wall, Mover.WADER) && f.canStand(wall, Mover.GHOST))
        all.firstOrNull { f.map.at(it.x, it.y) == TownCell.WATER }?.let { water ->
            assertTrue(!f.canStand(water, Mover.WALKER) && f.canStand(water, Mover.WADER) && !f.canStand(water, Mover.GHOST))
        }
        for (m in listOf(Mover.WALKER, Mover.WADER, Mover.GHOST)) assertTrue(f.restingCells(m).none { it in f.traps || it == f.stairs })
    }

    @Test
    fun theAutotileColumnsMatchTheExtractor() {
        assertEquals(47, Autotile.masks.size)
        assertEquals(0, Autotile.masks.first())
        assertEquals(255, Autotile.masks.last())
        // A lone square of water in a wall: no neighbour is water.
        val map = TownMap(3, 3, Array(9) { if (it == 4) TownCell.WATER else TownCell.WALL })
        assertEquals(0, Autotile.column(map, 1, 1))
        // A wall in the middle of walls, the map's edge counting as wall: every neighbour the same.
        val walls = TownMap(3, 3, Array(9) { TownCell.WALL })
        assertEquals(46, Autotile.column(walls, 1, 1))
    }

    @Test
    fun noPokemonCanStartOrBeSentWhereItCouldNeverWalkOut() {
        for (seed in 0L until 40L) {
            val f = floor(seed)
            for (m in listOf(Mover.WALKER, Mover.WADER, Mover.GHOST)) {
                val cells = f.restingCells(m)
                assertTrue(cells.isNotEmpty(), "somewhere to rest (seed $seed, $m)")
                // Every one of them has a way to the stairs, and back.
                for (c in cells.filterIndexed { i, _ -> i % 7 == 0 }) {
                    assertNotNull(f.path(c, f.stairs, m), "a way out of $c (seed $seed, $m)")
                    assertNotNull(f.path(f.stairs, c, m), "a way to $c (seed $seed, $m)")
                }
            }
        }
    }

    @Test
    fun aSpotIsChosenAwayFromOthersAndBigOnesGetMoreRoom() {
        val f = floor(5)
        val geo = FloorGeometry.of(f, 1, f.cols * 24f, f.rows * 24f)
        val life = TownLife()
        life.sizes["small"] = 23 to 27
        life.sizes["other"] = 23 to 27
        life.sizes["big"] = 80 to 70
        // Two ordinary Pokémon overlap on the same row within a square or two, but not a row apart; a big one needs about three squares across.
        assertTrue(life.overlap("small", Cell(5, 5), "other", Cell(6, 5)))
        assertTrue(!life.overlap("small", Cell(5, 5), "other", Cell(7, 5)))
        assertTrue(!life.overlap("small", Cell(5, 5), "other", Cell(5, 6)))
        assertTrue(life.overlap("big", Cell(5, 5), "small", Cell(7, 5)))
        assertTrue(life.overlap("big", Cell(5, 5), "small", Cell(5, 4)), "a big one hides the one right behind it")
        assertTrue(!life.overlap("big", Cell(5, 5), "small", Cell(5, 7)), "but not the one in front")
        assertTrue(!life.overlap("big", Cell(5, 5), "small", Cell(9, 5)))
        // Starting squares keep apart, a big one included.
        val random = kotlin.random.Random(3)
        val claimed = LinkedHashMap<String, Cell>()
        val ids = listOf("big") + (1..10).map { "p$it" }
        ids.forEach { life.sizes.putIfAbsent(it, 23 to 27) }
        for (id in ids) claimed[id] = geo.restingCell(random, Mover.WALKER, crowded = life.crowded(geo, id, claimed.toMap()))
        for (a in ids) for (b in ids) if (a < b) assertTrue(!life.overlap(a, claimed.getValue(a), b, claimed.getValue(b)), "$a and $b overlap")
    }

    @Test
    fun floorsHideTheStatusTrapsAndTheyWearOff() {
        val kinds = (0L until 80L).flatMap { floor(it).traps.values }.toSet()
        assertTrue(FloorTrap.POISON in kinds && FloorTrap.SPIN in kinds, "poison and confusion traps turn up: $kinds")
        Condition.entries.forEach { assertTrue(it.seconds in 10..120 && it.pace in 0.3f..1f, "$it wears off in a while") }
    }

    @Test
    fun aPokemonIsAWallToThePathsOfOthersAndAGustBlowsInALineToTheWall() {
        val f = floor(9)
        val m = Mover.WALKER
        val cells = f.restingCells(m)
        val from = cells.first()
        val to = cells.last()
        val way = assertNotNull(f.path(from, to, m))
        if (way.size > 3) {
            val middle = way[way.size / 2]
            val around = f.path(from, to, m, blocked = setOf(middle))
            // Either a way round, or none (a corridor with someone in it); never through the one standing there.
            assertTrue(around == null || middle !in around, "walked through $middle")
        }
        // A Gust Trap blows in a straight line until a wall, at most GUST_RANGE squares, and lands where it can rest.
        for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1)) {
            val landing = f.blownTo(from, dx, dy, m)
            val steps = maxOf(kotlin.math.abs(landing.x - from.x), kotlin.math.abs(landing.y - from.y))
            assertTrue(steps <= DungeonFloor.GUST_RANGE)
            assertEquals(from.x + dx * steps, landing.x); assertEquals(from.y + dy * steps, landing.y)
            assertTrue(landing == from || f.canRest(landing, m))
            // Another Pokémon in the way stops it short; a wall stops it where the floor ends.
            if (steps > 0) assertEquals(from, f.blownTo(from, dx, dy, m, blocked = setOf(Cell(from.x + dx, from.y + dy))))
            if (steps < DungeonFloor.GUST_RANGE && steps > 0) {
                var c = landing
                var ended = false
                repeat(DungeonFloor.GUST_RANGE) { if (!ended) { c = Cell(c.x + dx, c.y + dy); if (!f.canStand(c, m)) ended = true } }
                assertTrue(ended || f.blownTo(landing, dx, dy, m) == landing || steps >= DungeonFloor.GUST_RANGE - 1, "stopped short of a wall in ($dx, $dy)")
            }
        }
    }
}
