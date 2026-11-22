package app.pokeplan.data

data class Spread(val hp: Int, val attack: Int, val defense: Int, val spAttack: Int, val spDefense: Int, val speed: Int) {
    val total: Int get() = hp + attack + defense + spAttack + spDefense + speed

    companion object { val ZERO = Spread(0, 0, 0, 0, 0, 0) }
}

data class OwnedPokemonRecord(
    val id: String,
    val speciesId: Int,
    val formIdentifier: String,
    val nickname: String,
    val nature: String,
    val ability: String,
    val gender: String?,
    val ivs: Spread,
    val evs: Spread,
    val level: Int,
    val candyProgress: Int,
    val totalCandies: Int,
    val friendship: Int,
    val isShiny: Boolean,
    val heldItem: String?,
    val teraType: String?,
    val cosmeticForm: String?,
    val personality: Int,
    val caughtAtMillis: Long,
    val isFavorite: Boolean = false,
    val originHow: String? = null,
    val originRegion: String? = null,
    val extraJson: String? = null,
    val position: Int = 0,
    val originJson: String? = null,
)

sealed interface PokemonPlace {
    data object Buddy : PokemonPlace
    data class Party(val moduleId: String) : PokemonPlace
    data object Guardian : PokemonPlace
}

object ModuleIds {
    const val PLAN = "PLAN"
    const val CARE = "CARE"
    const val HEAL = "HEAL"
    const val MINT = "MINT"
    const val BASE = "HOME"
    const val LINK = "LINK"
    val WITH_PARTY = setOf(PLAN, CARE, HEAL, MINT, BASE, LINK)
    const val PARTY_CAPACITY = 6
}

enum class DexKind(val key: String) {
    CAUGHT_SPECIES("caught_species"),
    SHINY_SPECIES("shiny_species"),
    CAUGHT_FORM("caught_form"),
    SHINY_FORM("shiny_form"),
    SEEN_SPECIES("seen_species"),
}

data class PlayerProgress(val eggMeter: Long, val trainerPoints: Long)

object Assets {
    const val POKE = "poke"
    fun item(id: String) = "item:$id"
}
