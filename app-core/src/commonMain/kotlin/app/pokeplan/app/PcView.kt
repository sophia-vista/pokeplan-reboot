package app.pokeplan.app

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.OwnedPokemon

/** How the PC boxes are ordered. */
enum class PcSort(val label: String) {
    NEWEST("Newest"), LEVEL("Level"), DEX("Dex no."), NAME("Name"), FRIENDSHIP("Friendship"), IVS("IVs"),
}

/**
 * What the PC boxes show: a [sort], and any of a [type], shiny only, starred only and a [search] text (nickname or species).
 * Pure, so the rules are tested without a screen; [apply] never changes which Pokémon are owned.
 */
data class PcView(
    val sort: PcSort = PcSort.NEWEST,
    val type: PokemonType? = null,
    val shinyOnly: Boolean = false,
    val starredOnly: Boolean = false,
    val search: String = "",
    /** Which box (page of [BOX_SIZE]) is open, counting from 0; [boxCount] and [page] keep it in range. */
    val box: Int = 0,
) {
    /** True when the view hides some of the Pokémon (the screen shows a "clear" button then). */
    val filtering: Boolean get() = type != null || shinyOnly || starredOnly || search.isNotBlank()

    /** How many boxes [total] Pokémon fill (at least one, so an empty result still has a box to show). */
    fun boxCount(total: Int): Int = ((total + BOX_SIZE - 1) / BOX_SIZE).coerceAtLeast(1)

    /** The open box's Pokémon from the already sorted and filtered [shown]. */
    fun page(shown: List<OwnedPokemon>): List<OwnedPokemon> {
        val at = box.coerceIn(0, boxCount(shown.size) - 1)
        return shown.drop(at * BOX_SIZE).take(BOX_SIZE)
    }

    fun apply(pokemon: List<OwnedPokemon>, isStarred: (String) -> Boolean): List<OwnedPokemon> {
        val text = search.trim().lowercase()
        val kept = pokemon.filter { p ->
            (type == null || type in p.form.types) &&
                (!shinyOnly || p.isShiny) &&
                (!starredOnly || isStarred(p.id)) &&
                (text.isEmpty() || p.nickname.lowercase().contains(text) || p.species.name.lowercase().contains(text))
        }
        return when (sort) {
            PcSort.NEWEST -> kept.sortedByDescending { it.caughtAtMillis }
            PcSort.LEVEL -> kept.sortedWith(compareByDescending<OwnedPokemon> { it.level }.thenByDescending { it.caughtAtMillis })
            PcSort.DEX -> kept.sortedWith(compareBy<OwnedPokemon> { it.speciesId }.thenBy { it.formIdentifier }.thenBy { it.caughtAtMillis })
            PcSort.NAME -> kept.sortedWith(compareBy<OwnedPokemon> { it.nickname.lowercase() }.thenBy { it.caughtAtMillis })
            PcSort.FRIENDSHIP -> kept.sortedWith(compareByDescending<OwnedPokemon> { it.friendship }.thenByDescending { it.caughtAtMillis })
            PcSort.IVS -> kept.sortedWith(compareByDescending<OwnedPokemon> { it.ivs.total }.thenByDescending { it.caughtAtMillis })
        }
    }

    companion object {
        /** Pokémon per box, as in the games. */
        const val BOX_SIZE = 30
    }
}
