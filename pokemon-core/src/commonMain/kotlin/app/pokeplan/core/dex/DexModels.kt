package app.pokeplan.core.dex

import app.pokeplan.core.model.PokemonType

/** A collecting region. [HOME] is the curated starter dex; the others are the games' regional dexes. */
enum class Region(val label: String) {
    HOME("Home Region"),
    KANTO("Kanto"),
    JOHTO("Johto"),
    HOENN("Hoenn"),
    SINNOH("Sinnoh"),
    UNOVA("Unova"),
    KALOS("Kalos"),
    ALOLA("Alola"),
    GALAR("Galar"),
    PALDEA("Paldea"),
}

data class BaseStats(val hp: Int, val attack: Int, val defense: Int, val spAttack: Int, val spDefense: Int, val speed: Int) {
    val total: Int get() = hp + attack + defense + spAttack + spDefense + speed
}

enum class Gender { MALE, FEMALE }

/**
 * Species-level facts. [id] is the National Dex number.
 * [femaleEighths] is the chance of female in eighths (0 to 8), or null when genderless.
 * [cosmeticForms] are purely visual variants (Unown letters, Vivillon patterns, ...).
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
