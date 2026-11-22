package app.pokeplan.ui.launcher

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.StatusKind

internal enum class Quirk {
    ASLEEP,
    ROOTED,
    DISGUISED,
    SLOW,
    FAST,
    ERRATIC,
    TIMID,
}

internal object TownQuirks {
    private val table: Map<String, Quirk> = buildMap {
        listOf("snorlax", "slakoth", "slaking", "abra", "komala", "munna", "musharna", "morelull").forEach { put(it, Quirk.ASLEEP) }
        listOf("metapod", "kakuna", "cascoon", "silcoon", "sudowoodo", "ferroseed", "ferrothorn", "sandygast", "palossand").forEach { put(it, Quirk.ROOTED) }
        listOf("mew", "ditto", "zorua", "zoroark").forEach { put(it, Quirk.DISGUISED) }
        listOf("shuckle", "slowpoke", "slowbro", "slowking", "torkoal", "carbink").forEach { put(it, Quirk.SLOW) }
        listOf(
            "vigoroth", "ninjask", "varoom", "revavroom", "zebstrika", "doduo", "dodrio", "cyclizar", "cinderace", "arcanine", "talonflame",
            "pikachu", "pichu", "raichu", "plusle", "minun", "pachirisu", "emolga", "dedenne", "togedemaru", "morpeko", "pawmi", "pawmo", "pawmot",
        ).forEach { put(it, Quirk.FAST) }
        put("spinda", Quirk.ERRATIC)
        listOf("ralts", "wimpod").forEach { put(it, Quirk.TIMID) }
    }

    fun of(identifier: String?): Quirk? = identifier?.let { table[it] }

    fun of(mon: OwnedPokemon): Quirk? = of(mon.species.identifier)

    fun stays(quirk: Quirk?): Boolean = quirk == Quirk.ROOTED || quirk == Quirk.ASLEEP

    fun pace(quirk: Quirk?): Float = when (quirk) { Quirk.SLOW -> 0.5f; Quirk.FAST -> 1.9f; else -> 1f }

    fun temper(quirk: Quirk?): Float = when (quirk) { Quirk.SLOW -> 0.6f; Quirk.FAST -> 1.4f; else -> 1f }

    fun stumble(quirk: Quirk?, confused: Boolean): Int = if (quirk == Quirk.ERRATIC) 30 else if (confused) 8 else 0

    fun disguiseFor(mon: OwnedPokemon, town: List<OwnedPokemon>): OwnedPokemon? {
        val pool = town.filter { it.speciesId != mon.speciesId && of(it) != Quirk.DISGUISED }.sortedBy { it.id }
        return if (pool.isEmpty()) null else pool[(mon.id.hashCode() and 0x7fffffff) % pool.size]
    }

    fun aura(types: List<PokemonType>): StatusKind = when (types.firstOrNull()) {
        PokemonType.FIRE -> StatusKind.FLAME
        PokemonType.ICE -> StatusKind.ICE
        PokemonType.FIGHTING -> StatusKind.FIST
        PokemonType.STEEL, PokemonType.ROCK -> StatusKind.SHIELD
        PokemonType.ELECTRIC -> StatusKind.ZAP
        PokemonType.POISON -> StatusKind.SKULL
        else -> StatusKind.TWINKLE
    }
}
