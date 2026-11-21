package app.pokeplan.base

import kotlinx.serialization.Serializable

enum class DecorationKind(val label: String, val flat: Boolean = false) {
    DOLL("Doll"), MAT("Mat", flat = true), DESK("Desk"), PLANT("Plant"), TENT("Tent"), BRICK("Brick"), ORNAMENT("Ornament"), OTHER("Other"),
}

data class Decoration(val id: String, val name: String, val kind: DecorationKind) {
    val flat: Boolean get() = kind.flat
}

object Decorations {
    val ALL: List<Decoration> = listOf(
        Decoration("blastoise_doll", "Blastoise Doll", DecorationKind.DOLL),
        Decoration("charizard_doll", "Charizard Doll", DecorationKind.DOLL),
        Decoration("lapras_doll", "Lapras Doll", DecorationKind.DOLL),
        Decoration("regice_doll", "Regice Doll", DecorationKind.DOLL),
        Decoration("regirock_doll", "Regirock Doll", DecorationKind.DOLL),
        Decoration("registeel_doll", "Registeel Doll", DecorationKind.DOLL),
        Decoration("rhydon_doll", "Rhydon Doll", DecorationKind.DOLL),
        Decoration("snorlax_doll", "Snorlax Doll", DecorationKind.DOLL),
        Decoration("venusaur_doll", "Venusaur Doll", DecorationKind.DOLL),
        Decoration("wailmer_doll", "Wailmer Doll", DecorationKind.DOLL),
        Decoration("attract_mat", "Attract Mat", DecorationKind.MAT),
        Decoration("fire_blast_mat", "Fire Blast Mat", DecorationKind.MAT),
        Decoration("fissure_mat", "Fissure Mat", DecorationKind.MAT),
        Decoration("powder_snow_mat", "Powder Snow Mat", DecorationKind.MAT),
        Decoration("spikes_mat", "Spikes Mat", DecorationKind.MAT),
        Decoration("surf_mat", "Surf Mat", DecorationKind.MAT),
        Decoration("thunder_mat", "Thunder Mat", DecorationKind.MAT),
        Decoration("brick_desk", "Brick Desk", DecorationKind.DESK),
        Decoration("camp_desk", "Camp Desk", DecorationKind.DESK),
        Decoration("comfort_desk", "Comfort Desk", DecorationKind.DESK),
        Decoration("hard_desk", "Hard Desk", DecorationKind.DESK),
        Decoration("heavy_desk", "Heavy Desk", DecorationKind.DESK),
        Decoration("pretty_desk", "Pretty Desk", DecorationKind.DESK),
        Decoration("ragged_desk", "Ragged Desk", DecorationKind.DESK),
        Decoration("big_plant", "Big Plant", DecorationKind.PLANT),
        Decoration("colorful_plant", "Colorful Plant", DecorationKind.PLANT),
        Decoration("gorgeous_plant", "Gorgeous Plant", DecorationKind.PLANT),
        Decoration("red_plant", "Red Plant", DecorationKind.PLANT),
        Decoration("tropical_plant", "Tropical Plant", DecorationKind.PLANT),
        Decoration("pretty_flowers", "Pretty Flowers", DecorationKind.PLANT),
        Decoration("blue_tent", "Blue Tent", DecorationKind.TENT),
        Decoration("red_tent", "Red Tent", DecorationKind.TENT),
        Decoration("blue_brick", "Blue Brick", DecorationKind.BRICK),
        Decoration("red_brick", "Red Brick", DecorationKind.BRICK),
        Decoration("yellow_brick", "Yellow Brick", DecorationKind.BRICK),
        Decoration("glass_ornament", "Glass Ornament", DecorationKind.ORNAMENT),
        Decoration("sand_ornament", "Sand Ornament", DecorationKind.ORNAMENT),
        Decoration("breakable_door", "Breakable Door", DecorationKind.OTHER),
        Decoration("slide", "Slide", DecorationKind.OTHER),
        Decoration("solid_board", "Solid Board", DecorationKind.OTHER),
        Decoration("stand", "Stand", DecorationKind.OTHER),
        Decoration("tire", "Tire", DecorationKind.OTHER),
    )

    private val byId = ALL.associateBy { it.id }

    fun get(id: String): Decoration? = byId[id]

    const val CHORE_POINTS = 1
    const val MAINTENANCE_POINTS = 2

    const val METER_SIZE = 5

    val STARTERS: List<String> = listOf("snorlax_doll", "thunder_mat", "big_plant")

    fun missing(owned: Collection<String>): List<Decoration> = ALL.filter { it.id !in owned }

    fun pick(owned: Collection<String>, seed: String): Decoration? {
        val left = missing(owned)
        if (left.isEmpty()) return null
        var h = 0x811C9DC5.toInt()
        for (c in seed) { h = h xor c.code; h *= 0x01000193 }
        return left[(h.toLong() and 0x7FFFFFFFL).toInt() % left.size]
    }

    data class Meter(val points: Int, val found: Boolean)

    fun advance(meter: Int, add: Int, allOwned: Boolean): Meter {
        if (allOwned) return Meter(0, false)
        val total = meter + add
        return Meter(total % METER_SIZE, total >= METER_SIZE)
    }
}

data class Cell(val x: Int, val y: Int)

object RoomGrid {
    const val WIDTH = 11
    const val HEIGHT = 9
    const val CELL_PX = 16

    private val SPANS: Map<Int, IntRange> = mapOf(
        1 to 6..8, 2 to 4..8, 3 to 1..8, 4 to 1..8, 5 to 1..8, 6 to 2..8, 7 to 3..7,
    )

    val CELLS: List<Cell> = SPANS.flatMap { (y, xs) -> xs.map { x -> Cell(x, y) } }

    fun contains(x: Int, y: Int): Boolean = SPANS[y]?.contains(x) == true
}

@Serializable
data class Placement(val id: String, val x: Int, val y: Int)

enum class RoomLook(val label: String, val layout: String, val tileset: String) {
    TREE("Tree", "SecretBase_Tree1", "tree"),
    SHRUB("Shrub", "SecretBase_Shrub1", "shrub"),
    RED_CAVE("Red cave", "SecretBase_RedCave1", "red_cave"),
    BROWN_CAVE("Brown cave", "SecretBase_BrownCave1", "brown_cave"),
    BLUE_CAVE("Blue cave", "SecretBase_BlueCave1", "blue_cave"),
    YELLOW_CAVE("Yellow cave", "SecretBase_YellowCave1", "yellow_cave");

    companion object {
        fun forType(type: String?): RoomLook = when (type?.lowercase()) {
            "grass", "bug", "flying" -> TREE
            "normal", "fairy", "psychic" -> SHRUB
            "fire", "fighting", "dragon" -> RED_CAVE
            "rock", "ground", "steel" -> BROWN_CAVE
            "water", "ice", "ghost" -> BLUE_CAVE
            "electric", "dark", "poison" -> YELLOW_CAVE
            else -> SHRUB
        }
    }
}

object Room {
    sealed interface Result {
        data class Done(val placed: List<Placement>) : Result
        data class Refused(val reason: String) : Result
    }

    fun inBox(owned: Collection<String>, placed: List<Placement>): List<Decoration> {
        val out = placed.map { it.id }.toSet()
        return Decorations.ALL.filter { it.id in owned && it.id !in out }
    }

    private fun occupant(placed: List<Placement>, x: Int, y: Int): Placement? = placed.firstOrNull { it.x == x && it.y == y }

    fun place(owned: Collection<String>, placed: List<Placement>, id: String, x: Int, y: Int): Result {
        if (Decorations.get(id) == null || id !in owned) return Result.Refused("You do not have that.")
        if (placed.any { it.id == id }) return Result.Refused("That is already in the room.")
        if (!RoomGrid.contains(x, y)) return Result.Refused("That is not on the floor.")
        if (occupant(placed, x, y) != null) return Result.Refused("Something is already there.")
        return Result.Done(placed + Placement(id, x, y))
    }

    fun move(placed: List<Placement>, id: String, x: Int, y: Int): Result {
        if (placed.none { it.id == id }) return Result.Refused("That is not in the room.")
        if (!RoomGrid.contains(x, y)) return Result.Refused("That is not on the floor.")
        if (occupant(placed, x, y) != null) return Result.Refused("Something is already there.")
        return Result.Done(placed.map { if (it.id == id) it.copy(x = x, y = y) else it })
    }

    fun remove(placed: List<Placement>, id: String): Result =
        if (placed.none { it.id == id }) Result.Refused("That is not in the room.") else Result.Done(placed.filterNot { it.id == id })

    fun tidy(owned: Collection<String>, placed: List<Placement>): List<Placement> {
        val seenIds = mutableSetOf<String>()
        val seenCells = mutableSetOf<Cell>()
        return placed.filter { p ->
            Decorations.get(p.id) != null && p.id in owned && RoomGrid.contains(p.x, p.y) && seenIds.add(p.id) && seenCells.add(Cell(p.x, p.y))
        }
    }

    fun standingCells(placed: List<Placement>, count: Int): List<Cell> {
        if (count <= 0) return emptyList()
        val blocked = placed.filter { p -> Decorations.get(p.id)?.flat == false }.map { Cell(it.x, it.y) }.toSet()
        val free = RoomGrid.CELLS.filter { it !in blocked }
        val out = mutableListOf<Cell>()
        for (i in 0 until minOf(count, free.size)) {
            val spot = SPOTS.getOrNull(i)
            val left = free.filter { it !in out }
            out += if (spot == null) left.first() else left.minBy { kotlin.math.abs(it.x - spot.x) + kotlin.math.abs(it.y - spot.y) }
        }
        return out
    }

    private val SPOTS = listOf(Cell(3, 4), Cell(6, 3), Cell(8, 5), Cell(4, 6), Cell(6, 6), Cell(2, 5))
}
