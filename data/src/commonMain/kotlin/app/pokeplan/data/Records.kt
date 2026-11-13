package app.pokeplan.data

/** The six stats of an IV or EV spread. */
data class Spread(val hp: Int, val attack: Int, val defense: Int, val spAttack: Int, val spDefense: Int, val speed: Int) {
    val total: Int get() = hp + attack + defense + spAttack + spDefense + speed

    companion object { val ZERO = Spread(0, 0, 0, 0, 0, 0) }
}

/** One owned Pokémon as stored. The ids and names are the legacy ones, unchanged. */
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
    /** Raw JSON of any legacy field this version has no column for. */
    val extraJson: String? = null,
    /** Its place in the collection's list (the order the Pokémon were owned); 0 asks [PokemonRepository.add] to put it last. */
    val position: Int = 0,
    /** The origin exactly as the save keeps it; [originHow] and [originRegion] are its lookup columns. */
    val originJson: String? = null,
)

/** Where a Pokémon is, besides the PC (a Pokémon with none is in the PC). WORLD teams and GEAR helpers are kept by their modules. */
sealed interface PokemonPlace {
    data object Buddy : PokemonPlace
    data class Party(val moduleId: String) : PokemonPlace
    data object Guardian : PokemonPlace
}

/** The modules that keep a party (POKEMON_ECOSYSTEM.md section 2.2). */
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

/** Which Dex facts [PokemonRepository.addDex] keeps. */
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
