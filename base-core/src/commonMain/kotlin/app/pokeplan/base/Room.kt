package app.pokeplan.base

import kotlinx.serialization.Serializable

/**
 * The Secret Base room (docs/pokelife `MODULES/HOME.md` section 9.5, REP-053): decorations, the room's grid and looks, and the rules for placing
 * them. Pure and free of Pokémon and rewards: the room shows Base's party but is never a place a Pokémon is assigned to, and a decoration has no
 * effect. Every number is a *(tune)* starting value.
 */
enum class DecorationKind(val label: String, val flat: Boolean = false) {
    DOLL("Doll"), MAT("Mat", flat = true), DESK("Desk"), PLANT("Plant"), TENT("Tent"), BRICK("Brick"), ORNAMENT("Ornament"), OTHER("Other"),
}

/** One decoration of the catalogue: [id] is its Emerald file name (`assets/hoenn/decorations/<id>.png`, a 24 px sprite) and never changes. */
data class Decoration(val id: String, val name: String, val kind: DecorationKind) {
    /** A mat lies on the floor under whatever stands on it; everything else stands. */
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

    /** Points a paid moment adds to the Decoration meter (HOME.md 9.5.4): a chore done 1, a piece of maintenance done 2. *(tune)* */
    const val CHORE_POINTS = 1
    const val MAINTENANCE_POINTS = 2

    /** Points that pay one decoration (the owner chose 5, the Habitat Egg meter's size). *(tune)* */
    const val METER_SIZE = 5

    /** What a new save, and a save from before the room, starts with, once: a doll, a mat and a plant. */
    val STARTERS: List<String> = listOf("snorlax_doll", "thunder_mat", "big_plant")

    /** The decorations [owned] does not hold yet, in catalogue order. */
    fun missing(owned: Collection<String>): List<Decoration> = ALL.filter { it.id !in owned }

    /**
     * The decoration an upkeep payment finds: one the player does not own yet, chosen from [seed] (the payment's event id) so a replay of the same event
     * names the same one. Null when all of them are owned (then the meter stops filling).
     */
    fun pick(owned: Collection<String>, seed: String): Decoration? {
        val left = missing(owned)
        if (left.isEmpty()) return null
        // FNV-1a over the seed's characters: stable on every platform, unlike a platform hash.
        var h = 0x811C9DC5.toInt()
        for (c in seed) { h = h xor c.code; h *= 0x01000193 }
        return left[(h.toLong() and 0x7FFFFFFFL).toInt() % left.size]
    }

    /** How the meter moves for one paid moment: the new points left over and whether one decoration is found. */
    data class Meter(val points: Int, val found: Boolean)

    /** [meter] plus [add] points; at [METER_SIZE] it pays and keeps the rest. With [allOwned] it neither fills nor pays. */
    fun advance(meter: Int, add: Int, allOwned: Boolean): Meter {
        if (allOwned) return Meter(0, false)
        val total = meter + add
        return Meter(total % METER_SIZE, total >= METER_SIZE)
    }
}

/** A cell of the room's grid: [x] runs 0 to 10 across, [y] 0 to 8 down (the 11 x 9 metatiles of a size-1 Hoenn Secret Base). */
data class Cell(val x: Int, val y: Int)

/**
 * The room's grid: the **44 cells** that can be walked on in all six size-1 layouts (`assets/hoenn/secret-base/layouts/SecretBase_*1`, where the
 * collision bits of a `map.bin` cell are 0), so a change of look never moves a decoration. Rows are the first walkable column and the last, per row.
 */
object RoomGrid {
    const val WIDTH = 11
    const val HEIGHT = 9
    const val CELL_PX = 16 // pixels of one cell (a metatile)

    /** For each row y, the walkable columns x from..to; rows not listed are wall. */
    private val SPANS: Map<Int, IntRange> = mapOf(
        1 to 6..8, 2 to 4..8, 3 to 1..8, 4 to 1..8, 5 to 1..8, 6 to 2..8, 7 to 3..7,
    )

    val CELLS: List<Cell> = SPANS.flatMap { (y, xs) -> xs.map { x -> Cell(x, y) } }

    fun contains(x: Int, y: Int): Boolean = SPANS[y]?.contains(x) == true
}

/** One decoration standing on one cell. [id] is the decoration's id, which is also the record's key: each decoration is owned once, so it is placed at most once. */
@Serializable
data class Placement(val id: String, val x: Int, val y: Int)

/** The room's looks (HOME.md 9.5.3): which Hoenn layout and tileset draws it. The look follows Base's lead and is never stored. */
enum class RoomLook(val label: String, val layout: String, val tileset: String) {
    TREE("Tree", "SecretBase_Tree1", "tree"),
    SHRUB("Shrub", "SecretBase_Shrub1", "shrub"),
    RED_CAVE("Red cave", "SecretBase_RedCave1", "red_cave"),
    BROWN_CAVE("Brown cave", "SecretBase_BrownCave1", "brown_cave"),
    BLUE_CAVE("Blue cave", "SecretBase_BlueCave1", "blue_cave"),
    YELLOW_CAVE("Yellow cave", "SecretBase_YellowCave1", "yellow_cave");

    companion object {
        /** The look for a lead whose primary type is named [type] (any case; the 18 types, three to each look). No lead, or a type this does not know, is a shrub. */
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

/** The rules for placing decorations (HOME.md 9.5.5). Each returns the new placements, or why not; nothing here costs anything or loses a decoration. */
object Room {
    sealed interface Result {
        data class Done(val placed: List<Placement>) : Result
        data class Refused(val reason: String) : Result
    }

    /** What the player owns and has not placed ("in the box"), in catalogue order. */
    fun inBox(owned: Collection<String>, placed: List<Placement>): List<Decoration> {
        val out = placed.map { it.id }.toSet()
        return Decorations.ALL.filter { it.id in owned && it.id !in out }
    }

    private fun occupant(placed: List<Placement>, x: Int, y: Int): Placement? = placed.firstOrNull { it.x == x && it.y == y }

    /** Puts an owned, unplaced decoration on an empty cell of the grid. */
    fun place(owned: Collection<String>, placed: List<Placement>, id: String, x: Int, y: Int): Result {
        if (Decorations.get(id) == null || id !in owned) return Result.Refused("You do not have that.")
        if (placed.any { it.id == id }) return Result.Refused("That is already in the room.")
        if (!RoomGrid.contains(x, y)) return Result.Refused("That is not on the floor.")
        if (occupant(placed, x, y) != null) return Result.Refused("Something is already there.")
        return Result.Done(placed + Placement(id, x, y))
    }

    /** Moves a placed decoration to another empty cell. */
    fun move(placed: List<Placement>, id: String, x: Int, y: Int): Result {
        if (placed.none { it.id == id }) return Result.Refused("That is not in the room.")
        if (!RoomGrid.contains(x, y)) return Result.Refused("That is not on the floor.")
        if (occupant(placed, x, y) != null) return Result.Refused("Something is already there.")
        return Result.Done(placed.map { if (it.id == id) it.copy(x = x, y = y) else it })
    }

    /** Takes a decoration back into the box. It is never lost. */
    fun remove(placed: List<Placement>, id: String): Result =
        if (placed.none { it.id == id }) Result.Refused("That is not in the room.") else Result.Done(placed.filterNot { it.id == id })

    /**
     * Drops what the player no longer owns or cannot be on the floor, and a second use of the same cell or decoration (a damaged or hand-edited
     * save); the earlier record of each wins.
     */
    fun tidy(owned: Collection<String>, placed: List<Placement>): List<Placement> {
        val seenIds = mutableSetOf<String>()
        val seenCells = mutableSetOf<Cell>()
        return placed.filter { p ->
            Decorations.get(p.id) != null && p.id in owned && RoomGrid.contains(p.x, p.y) && seenIds.add(p.id) && seenCells.add(Cell(p.x, p.y))
        }
    }

    /**
     * Where [count] Pokémon stand: the floor cells with no standing decoration (a mat does not block). Each takes its own spot of [SPOTS], spread over the
     * floor, or the free cell nearest to it when a decoration stands there; past six they take the free cells in order. Never stored, so the scene looks
     * the same each time it opens.
     */
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

    /** The spots six Pokémon prefer: back left, back right, the middle, front left and right, spread over the floor. */
    private val SPOTS = listOf(Cell(3, 4), Cell(6, 3), Cell(8, 5), Cell(4, 6), Cell(6, 6), Cell(2, 5))
}
