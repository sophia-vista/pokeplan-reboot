package app.pokeplan.core.dex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A collecting region. The module regions ([isModule]) are hand-curated lists, one per life module
 * (`tools/species/regions/<name>.txt`, "Module regions" in docs/ecosystem.md). A module region is open to everyone once its
 * module ships ([released]); until then it is hidden. [PLAN] was the Home Region and keeps its stored name `HOME` (saves,
 * plan.focus_sessions, the reward function).
 *
 * The games' regional dexes (Kanto to Paldea) are retired (0.44.0, owner): only the Plan and Care Regions are collected. They
 * stay here, never released, only so saves, sessions and events that name one still load; [open] sends them to [PLAN].
 */
@Serializable
enum class Region(val label: String, val isModule: Boolean = false, val released: Boolean = true) {
    @SerialName("HOME") PLAN("Plan Region", isModule = true),
    KANTO("Kanto", released = false),
    JOHTO("Johto", released = false),
    HOENN("Hoenn", released = false),
    SINNOH("Sinnoh", released = false),
    UNOVA("Unova", released = false),
    KALOS("Kalos", released = false),
    ALOLA("Alola", released = false),
    GALAR("Galar", released = false),
    PALDEA("Paldea", released = false),
    /** The Care module's region: gentle, soothing Pokémon. Released with the Care screens (0.34.0). */
    CARE("Care Region", isModule = true),
    /** The Heal module's region (Field Research): sporty, outdoorsy and energetic Pokémon. Released with the Heal screens. */
    HEAL("Heal Region", isModule = true),
    /** The finance module's region (Ledger Research): steady Steel, Rock and Ground keepers, fossils and gems. Released with the Mint screens. */
    MINT("Mint Region", isModule = true),
    /** The household module's region (kitchen, pantry, closet, garden): hearth, garden and pet Pokémon. Released with the Home screens. */
    HOUSEHOLD("Home Region", isModule = true),
    /** The connection module's region (Postcard Trail): postal birds, loyal dogs, pairs and signallers. Not released until the module ships. */
    LINK("Link Region", isModule = true, released = false),
    ;

    /** Where an egg from this region really comes from: itself while released, else the Plan Region. */
    val open: Region get() = if (released) this else PLAN

    companion object {
        /** The games' regions, retired: none is released. */
        val games: List<Region> = entries.filter { !it.isModule }
    }
}

data class BaseStats(val hp: Int, val attack: Int, val defense: Int, val spAttack: Int, val spDefense: Int, val speed: Int) {
    val total: Int get() = hp + attack + defense + spAttack + spDefense + speed
}

enum class Gender { MALE, FEMALE }

/**
 * Species-level facts. [id] is the National Dex number.
 * [femaleEighths] is the chance of female in eighths (0 to 8), or null when genderless.
 * [cosmeticForms] are purely visual variants (Unown letters, Vivillon patterns, ...).
 * [genus] is the Pokédex category ("Seed Pokémon"); [heightDm] and [weightHg] are the default form's height in decimetres
 * and weight in hectograms, as the games store them.
 */
data class SpeciesInfo(
    val id: Int,
    val identifier: String,
    val name: String,
    val generation: Int,
    val evolvesFrom: Int?,
    val femaleEighths: Int?,
    val isBaby: Boolean,
    val isLegendary: Boolean,
    val isMythical: Boolean,
    val cosmeticForms: List<String>,
    val genus: String,
    val heightDm: Int,
    val weightHg: Int,
) {
    /** Legendary-class species that never hatch from eggs: legendaries, Ultra Beasts and Paradox Pokémon. */
    val isLegendaryClass: Boolean get() = isLegendary || identifier in SpecialSpecies.legendaryClass

    /** Species kept out of every egg pool: legendary-class and mythical. They come from quests and milestones. */
    val isSpecial: Boolean get() = isLegendaryClass || isMythical
}

enum class FormKind {
    /** The base form of a species. */
    DEFAULT,

    /** Alolan, Galarian, Hisuian and Paldean variants. */
    REGIONAL,

    /** A separate form for the other gender (Meowstic, Indeedee, ...). */
    GENDER,

    MEGA,

    GMAX,

    /** Other forms (Rotom appliances, Deoxys formes, Arceus types, ...), changed by items or events, never hatched. */
    ALTERNATE,
}

/** One row per Pokémon form: types, base stats and abilities live here. */
data class FormInfo(
    val id: Int,
    val identifier: String,
    val speciesId: Int,
    val kind: FormKind,
    val name: String,
    val types: List<PokemonType>,
    val stats: BaseStats,
    val abilities: List<String>,
    val hiddenAbility: String?,
)

/** How an evolution is triggered in the app. See "Evolution" in docs/design.md. */
enum class EvolutionKind {
    /** Super Candy once the level is reached. */
    LEVEL,

    /** An evolution item. */
    STONE,

    /** A Link Cord (holding [Evolution.heldItem] where the game needs one). */
    TRADE,

    /** Friendship at [Evolution.friendship] or more, then a Super Candy, at [Evolution.timeOfDay] where it matters. */
    FRIENDSHIP,

    /** A Super Candy used at the right time of day, from [Evolution.level] where set. */
    LEVEL_TIME,

    /** Depends on the Pokémon's gender. */
    GENDER,

    /** Decided when the Pokémon hatches (Wurmple, Tandemaus, ...). */
    RANDOM,

    /** Anything else: an Evolution Trial, a short quest flavoured after [Evolution.conditions]. */
    TRIAL,
}

enum class TimeOfDay { DAY, NIGHT, DUSK }

data class Evolution(
    val from: Int,
    val to: Int,
    val kind: EvolutionKind,
    val level: Int?,
    val item: String?,
    val heldItem: String?,
    val timeOfDay: TimeOfDay?,
    val gender: Gender?,
    val friendship: Int?,
    val requiredForm: String?,
    val evolvedForm: String?,
    val chancePercent: Int?,
    val conditions: List<String>,
)

/** A species (and the form to show) that can hatch in a region. */
data class HatchOption(val speciesId: Int, val formIdentifier: String)

/** Whether a move deals physical or special damage, or none. */
enum class MoveCategory { PHYSICAL, SPECIAL, STATUS }

/** A move. [power] and [accuracy] are null for moves that have none (status moves, never-miss moves). */
data class MoveInfo(
    val id: Int,
    val identifier: String,
    val name: String,
    val type: PokemonType,
    val category: MoveCategory,
    val power: Int?,
    val pp: Int?,
    val accuracy: Int?,
    val priority: Int,
    val generation: Int,
)

/** Where a Pokémon form can learn a move from (the newest game it appears in). */
enum class LearnMethod { LEVEL_UP, EGG, TUTOR, MACHINE }

/** The moves one form learns, from the newest game it appears in. [levelUp] pairs a move id with its level. */
data class Learnset(
    val levelUp: List<Pair<Int, Int>>,
    val egg: List<Int>,
    val tutor: List<Int>,
    val machine: List<Int>,
) {
    /** Every move id this form can learn by any method. */
    val all: Set<Int> by lazy { levelUp.map { it.first }.toSet() + egg + tutor + machine }

    fun learns(moveId: Int): Boolean = moveId in all

    fun methodsFor(moveId: Int): List<LearnMethod> = buildList {
        if (levelUp.any { it.first == moveId }) add(LearnMethod.LEVEL_UP)
        if (moveId in egg) add(LearnMethod.EGG)
        if (moveId in tutor) add(LearnMethod.TUTOR)
        if (moveId in machine) add(LearnMethod.MACHINE)
    }

    companion object {
        val EMPTY = Learnset(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
