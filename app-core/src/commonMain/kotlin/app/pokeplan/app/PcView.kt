package app.pokeplan.app

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.OwnedPokemon

enum class PcSort(val label: String) {
    NEWEST("Newest"), LEVEL("Level"), DEX("Dex no."), NAME("Name"), FRIENDSHIP("Friendship"), IVS("IVs"),
}

enum class PcPlace(val label: String) {
    PC("PC"), BUDDY("Buddy"), LEAD("Leads"), PARTY("Parties"), GUARDIAN("Guardians"), WORLD("World teams"), GEAR("Pokégear");

    companion object {
        fun of(place: Place, isLead: Boolean): PcPlace = when (place) {
            Place.Pc -> PC
            Place.Buddy -> BUDDY
            is Place.Party -> if (isLead) LEAD else PARTY
            is Place.Guardian -> GUARDIAN
            is Place.WorldTeam -> WORLD
            is Place.GearHelper -> GEAR
        }

        fun all(d: AppData): Map<String, PcPlace> {
            val p = d.player
            val leads = listOfNotNull(p.planLead, p.careLead, p.healLead, p.mintLead, p.baseLead, p.linkLead).map { it.id }.toSet()
            return p.pokemon.associate { it.id to of(Places.of(d, it.id), it.id in leads) }
        }
    }
}

data class PcView(
    val sort: PcSort = PcSort.NEWEST,
    val type: PokemonType? = null,
    val shinyOnly: Boolean = false,
    val starredOnly: Boolean = false,
    val search: String = "",
    val box: Int = 0,
    val everyone: Boolean = false,
    val where: PcPlace? = null,
) {
    val filtering: Boolean get() = type != null || shinyOnly || starredOnly || search.isNotBlank() || (everyone && where != null)

    fun cleared(): PcView = copy(type = null, shinyOnly = false, starredOnly = false, search = "", where = null, box = 0)

    fun boxCount(total: Int): Int = ((total + BOX_SIZE - 1) / BOX_SIZE).coerceAtLeast(1)

    fun page(shown: List<OwnedPokemon>): List<OwnedPokemon> {
        val at = box.coerceIn(0, boxCount(shown.size) - 1)
        return shown.drop(at * BOX_SIZE).take(BOX_SIZE)
    }

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
        const val BOX_SIZE = 30
    }
}
