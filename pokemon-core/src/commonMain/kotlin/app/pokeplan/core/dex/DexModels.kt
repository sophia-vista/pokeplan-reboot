package app.pokeplan.core.dex

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    CARE("Care Region", isModule = true),
    HEAL("Heal Region", isModule = true),
    MINT("Mint Region", isModule = true),
    @SerialName("HOUSEHOLD") BASE("Base Region", isModule = true),
    LINK("Link Region", isModule = true),
    ;

    val open: Region get() = if (released) this else PLAN

    companion object {
        val games: List<Region> = entries.filter { !it.isModule }
    }
}

data class BaseStats(val hp: Int, val attack: Int, val defense: Int, val spAttack: Int, val spDefense: Int, val speed: Int) {
    val total: Int get() = hp + attack + defense + spAttack + spDefense + speed
}

enum class Gender { MALE, FEMALE }

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
    val isLegendaryClass: Boolean get() = isLegendary || identifier in SpecialSpecies.legendaryClass

    val isSpecial: Boolean get() = isLegendaryClass || isMythical
}

enum class FormKind {
    DEFAULT,

    REGIONAL,

    GENDER,

    MEGA,

    GMAX,

    ALTERNATE,
}

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

enum class EvolutionKind {
    LEVEL,

    STONE,

    TRADE,

    FRIENDSHIP,

    LEVEL_TIME,

    GENDER,

    RANDOM,

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

data class HatchOption(val speciesId: Int, val formIdentifier: String)

enum class MoveCategory { PHYSICAL, SPECIAL, STATUS }

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

data class Learnset(
    val levelUp: List<Pair<Int, Int>>,
    val egg: List<Int>,
    val tutor: List<Int>,
    val machine: List<Int>,
) {
    val all: Set<Int> by lazy { levelUp.map { it.first }.toSet() + egg + tutor + machine }

    fun learns(moveId: Int): Boolean = moveId in all

    companion object {
        val EMPTY = Learnset(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
