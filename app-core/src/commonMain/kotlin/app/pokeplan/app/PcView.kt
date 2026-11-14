package app.pokeplan.app

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.OwnedPokemon

/** How the PC boxes are ordered. */
enum class PcSort(val label: String) {
    NEWEST("Newest"), LEVEL("Level"), DEX("Dex no."), NAME("Name"), FRIENDSHIP("Friendship"), IVS("IVs"),
}

/** Where a Pokémon is, in the PC's "Where" filter when it shows every Pokémon ([PcView.everyone]). */
enum class PcPlace(val label: String) {
    PC("PC"), BUDDY("Buddy"), LEAD("Leads"), PARTY("Parties"), GUARDIAN("Guardians"), WORLD("World teams"), GEAR("Pokégear");

    companion object {
        /** The filter group of [place]; a party's lead is [LEAD] (as [isLead] says), every other party member [PARTY]. */
        fun of(place: Place, isLead: Boolean): PcPlace = when (place) {
            Place.Pc -> PC
            Place.Buddy -> BUDDY
            is Place.Party -> if (isLead) LEAD else PARTY
            is Place.Guardian -> GUARDIAN
            is Place.WorldTeam -> WORLD
            is Place.GearHelper -> GEAR
        }

        /** Where every owned Pokémon in [d] is, as a filter group, by id (worked out once for a whole screen). */
        fun all(d: AppData): Map<String, PcPlace> {
            val p = d.player
            val leads = listOfNotNull(p.planLead, p.careLead, p.healLead, p.mintLead, p.baseLead, p.linkLead).map { it.id }.toSet()
            return p.pokemon.associate { it.id to of(Places.of(d, it.id), it.id in leads) }
        }
    }
}

/**
 * What the PC boxes show: only the Pokémon resting in the PC (in no other place), or with [everyone] every Pokémon owned, and then
 * a [where] filter by place. Then a [sort], and any of a [type], shiny only, starred only and a [search] text (nickname or species).
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
    /** Every Pokémon owned (the buddy, leads, parties, Guardians, World teams, Pokégear helpers) instead of only the PC's. */
    val everyone: Boolean = false,
    /** With [everyone], only the Pokémon in this place. */
    val where: PcPlace? = null,
) {
    /** True when the view hides some of the Pokémon (the screen shows a "clear" button then). */
    val filtering: Boolean get() = type != null || shinyOnly || starredOnly || search.isNotBlank() || (everyone && where != null)

    /** This view with every filter cleared; the sort and whether it shows [everyone] stay. */
    fun cleared(): PcView = copy(type = null, shinyOnly = false, starredOnly = false, search = "", where = null, box = 0)

    /** How many boxes [total] Pokémon fill (at least one, so an empty result still has a box to show). */
    fun boxCount(total: Int): Int = ((total + BOX_SIZE - 1) / BOX_SIZE).coerceAtLeast(1)

    /** The open box's Pokémon from the already sorted and filtered [shown]. */
    fun page(shown: List<OwnedPokemon>): List<OwnedPokemon> {
        val at = box.coerceIn(0, boxCount(shown.size) - 1)
        return shown.drop(at * BOX_SIZE).take(BOX_SIZE)
    }

    /**
     * [pokemon] filtered and sorted. [placeOf] says where each one is; without [everyone] only the PC's are kept, with it those in
     * [where] (or all of them).
     */
    fun apply(pokemon: List<OwnedPokemon>, placeOf: (String) -> PcPlace = { PcPlace.PC }, isStarred: (String) -> Boolean): List<OwnedPokemon> {
        val text = search.trim().lowercase()
        val kept = pokemon.filter { p ->
            val at = placeOf(p.id)
            (if (everyone) where == null || at == where else at == PcPlace.PC) &&
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
