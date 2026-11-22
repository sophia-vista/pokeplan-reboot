package app.pokeplan.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomTest {
    private fun done(r: Room.Result) = assertIs<Room.Result.Done>(r).placed

    @Test
    fun theCatalogueIsTheFortyTwoSpritesWithUniqueIds() {
        assertEquals(42, Decorations.ALL.size)
        assertEquals(42, Decorations.ALL.map { it.id }.toSet().size)
        assertEquals(7, Decorations.ALL.count { it.flat })
        assertTrue(Decorations.STARTERS.all { Decorations.get(it) != null })
        assertEquals(listOf(DecorationKind.DOLL, DecorationKind.MAT, DecorationKind.PLANT), Decorations.STARTERS.map { Decorations.get(it)!!.kind })
    }

    @Test
    fun theGridIsFortyFourDistinctCellsInsideTheMap() {
        assertEquals(44, RoomGrid.CELLS.size)
        assertEquals(44, RoomGrid.CELLS.toSet().size)
        assertTrue(RoomGrid.CELLS.all { it.x in 0 until RoomGrid.WIDTH && it.y in 0 until RoomGrid.HEIGHT })
        assertTrue(RoomGrid.contains(8, 4))
        assertTrue(!RoomGrid.contains(0, 4) && !RoomGrid.contains(9, 4) && !RoomGrid.contains(5, 0) && !RoomGrid.contains(5, 8))
    }

    @Test
    fun everyTypeHasALookThreeToEach() {
        val types = listOf(
            "Normal", "Fire", "Water", "Electric", "Grass", "Ice", "Fighting", "Poison", "Ground",
            "Flying", "Psychic", "Bug", "Rock", "Ghost", "Dragon", "Dark", "Steel", "Fairy",
        )
        val byLook = types.groupBy { RoomLook.forType(it) }
        assertEquals(RoomLook.entries.toSet(), byLook.keys)
        assertTrue(byLook.values.all { it.size == 3 })
        assertEquals(RoomLook.SHRUB, RoomLook.forType(null))
        assertEquals(RoomLook.SHRUB, RoomLook.forType("unknown"))
        assertEquals(RoomLook.RED_CAVE, RoomLook.forType("fire"))
    }

    @Test
    fun theMeterPaysAtFiveAndKeepsTheRest() {
        assertEquals(Decorations.Meter(1, false), Decorations.advance(0, Decorations.CHORE_POINTS, allOwned = false))
        assertEquals(Decorations.Meter(0, true), Decorations.advance(4, Decorations.CHORE_POINTS, allOwned = false))
        assertEquals(Decorations.Meter(1, true), Decorations.advance(4, Decorations.MAINTENANCE_POINTS, allOwned = false))
        assertEquals(Decorations.Meter(0, false), Decorations.advance(4, 2, allOwned = true), "with everything owned it neither fills nor pays")
    }

    @Test
    fun aPickIsAnUnownedDecorationAndTheSameForTheSameEvent() {
        val owned = Decorations.STARTERS
        val a = assertNotNull(Decorations.pick(owned, "home:chore:c1:2026-11-15"))
        assertTrue(a.id !in owned)
        assertEquals(a, Decorations.pick(owned, "home:chore:c1:2026-11-15"))
        var have = emptyList<String>()
        repeat(42) { n -> have = have + assertNotNull(Decorations.pick(have, "event-$n")).id }
        assertEquals(42, have.toSet().size)
        assertNull(Decorations.pick(have, "one-more"))
    }

    @Test
    fun placeMoveAndRemoveFollowTheRules() {
        val owned = setOf("snorlax_doll", "thunder_mat")
        var placed = done(Room.place(owned, emptyList(), "snorlax_doll", 4, 4))
        assertEquals(listOf(Placement("snorlax_doll", 4, 4)), placed)
        assertIs<Room.Result.Refused>(Room.place(owned, placed, "snorlax_doll", 5, 4), "already in the room")
        assertIs<Room.Result.Refused>(Room.place(owned, placed, "thunder_mat", 4, 4), "the cell is taken")
        assertIs<Room.Result.Refused>(Room.place(owned, placed, "thunder_mat", 0, 0), "not on the floor")
        assertIs<Room.Result.Refused>(Room.place(owned, placed, "big_plant", 5, 4), "not owned")
        assertIs<Room.Result.Refused>(Room.place(owned, placed, "no_such_thing", 5, 4), "not in the catalogue")
        placed = done(Room.place(owned, placed, "thunder_mat", 5, 4))
        assertEquals(emptyList(), Room.inBox(owned, placed))
        placed = done(Room.move(placed, "snorlax_doll", 6, 5))
        assertEquals(Placement("snorlax_doll", 6, 5), placed.first { it.id == "snorlax_doll" })
        assertIs<Room.Result.Refused>(Room.move(placed, "snorlax_doll", 5, 4), "onto the mat's cell")
        assertIs<Room.Result.Refused>(Room.move(placed, "big_plant", 2, 2), "not placed")
        placed = done(Room.remove(placed, "snorlax_doll"))
        assertEquals(listOf("snorlax_doll"), Room.inBox(owned, placed).map { it.id }, "removing puts it back in the box")
        assertIs<Room.Result.Refused>(Room.remove(placed, "snorlax_doll"))
    }

    @Test
    fun aDamagedRoomLosesOnlyWhatCannotStand() {
        val owned = listOf("snorlax_doll", "thunder_mat", "big_plant")
        val kept = Room.tidy(
            owned,
            listOf(
                Placement("snorlax_doll", 4, 4), Placement("snorlax_doll", 5, 4),
                Placement("thunder_mat", 4, 4),
                Placement("big_plant", 0, 0),
                Placement("tire", 3, 3),
            ),
        )
        assertEquals(listOf(Placement("snorlax_doll", 4, 4)), kept)
    }

    @Test
    fun pokemonStandOnFreeCellsAndAMatDoesNotBlock() {
        assertEquals(emptyList(), Room.standingCells(emptyList(), 0))
        val none = Room.standingCells(emptyList(), 6)
        assertEquals(6, none.size)
        assertEquals(6, none.toSet().size)
        assertTrue(none.all { RoomGrid.contains(it.x, it.y) })
        assertEquals(none, Room.standingCells(emptyList(), 6), "the same each time")
        val doll = Placement("snorlax_doll", none[0].x, none[0].y)
        assertTrue(Room.standingCells(listOf(doll), 6).none { it.x == doll.x && it.y == doll.y })
        val mat = Placement("thunder_mat", none[0].x, none[0].y)
        assertEquals(none, Room.standingCells(listOf(mat), 6))
        assertEquals(44, Room.standingCells(emptyList(), 60).size)
    }
}
