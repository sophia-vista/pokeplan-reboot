package app.pokeplan.core.dex

import app.pokeplan.core.model.PokemonType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A collecting region. The module regions ([isModule]) are hand-curated lists, one per life module
 * (`tools/species/regions/<name>.txt`, "Module regions" in docs/ecosystem.md); the others are the games' regional dexes,
 * which open one per trainer rank. A module region is open to everyone once its module ships ([released]); until then it
 * is hidden. [PLAN] was the Home Region and keeps its stored name `HOME` (saves, plan.focus_sessions, the reward function).
 */
@Serializable
enum class Region(val label: String, val isModule: Boolean = false, val released: Boolean = true) {
    @SerialName("HOME") PLAN("Plan Region", isModule = true),
    KANTO("Kanto"),
    JOHTO("Johto"),
    HOENN("Hoenn"),
    SINNOH("Sinnoh"),
    UNOVA("Unova"),
    KALOS("Kalos"),
    ALOLA("Alola"),
    GALAR("Galar"),
    PALDEA("Paldea"),
    /** The Care module's region: gentle, soothing Pokémon. Released with the Care screens. */
    CARE("Care Region", isModule = true, released = false),
    /** The household module's region (recipes, closet). Not curated yet. */
    HOUSEHOLD("Home Region", isModule = true, released = false),
    ;

    companion object {
        /** The game regions, in the order trainer rank opens them. */
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
