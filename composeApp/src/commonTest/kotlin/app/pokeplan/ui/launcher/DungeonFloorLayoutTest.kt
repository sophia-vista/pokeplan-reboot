package app.pokeplan.ui.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DungeonFloorLayoutTest {
    @Test
    fun theFloorRowsAreAlwaysClearAndTheSameSeedGivesTheSameFloor() {
        for (seed in 0 until 200) {
            val map = DungeonFloorLayout.build(40, 9, seed, floorRows = 4)
            for (x in 0 until 40) {
                assertTrue(map.at(x, 0) != TownCell.GROUND, "top row is wall or water")
                for (y in 1..4) assertEquals(TownCell.GROUND, map.at(x, y), "seed $seed ($x,$y) must be walkable")
            }
            val again = DungeonFloorLayout.build(40, 9, seed, floorRows = 4)
            for (y in 0 until 9) for (x in 0 until 40) assertEquals(map.at(x, y), again.at(x, y))
        }
    }

    @Test
    fun floorsVaryAndUseTheWholeAtlas() {
        val used = mutableSetOf<Pair<Int, Int>>()
        val layouts = (0 until 50).map { DungeonFloorLayout.build(40, 9, it, 4) }
        for (map in layouts) for (y in 0 until 9) for (x in 0 until 40) used += map.atlas(x, y, 7)
        assertTrue(used.size >= 10, "only ${used.size} different tiles used")
        assertTrue(layouts.map { l -> (0 until 40).map { l.at(it, 5) } }.distinct().size > 10, "bays should differ between floors")
    }
}
