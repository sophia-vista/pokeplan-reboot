package app.pokeplan.ui.launcher

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.OwnedPokemon
import app.pokeplan.ui.StatusKind

/**
 * How a species behaves in the town beyond the common wandering (owner, 20 November 2026, LAUNCHER.md 6.2): the ones the games make sleepy, rooted,
 * sneaky, slow, quick, flighty or shy. All cosmetic; a quirk changes only what the Pokémon does on the launcher.
 */
internal enum class Quirk {
    /** Lies asleep all day, whatever the hour. */
    ASLEEP,
    /** Never walks: it stands (or turns) where it is, like a plant or a rock. */
    ROOTED,
    /** Looks like another Pokémon of the town until it is touched. */
    DISGUISED,
    /** Walks at about half the usual pace and rests more. */
    SLOW,
    /** Walks at nearly twice the usual pace and potters about more. */
    FAST,
    /** Zigzags: it keeps stumbling a square to the side. */
    ERRATIC,
    /** Keeps its distance: it goes away from any Pokémon that comes close. */
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
            // The Pikachu and its clones.
            "pikachu", "pichu", "raichu", "plusle", "minun", "pachirisu", "emolga", "dedenne", "togedemaru", "morpeko", "pawmi", "pawmo", "pawmot",
        ).forEach { put(it, Quirk.FAST) }
        put("spinda", Quirk.ERRATIC)
        listOf("ralts", "wimpod").forEach { put(it, Quirk.TIMID) }
    }

    /** The quirk of the species with this PokeAPI [identifier], if it has one. */
    fun of(identifier: String?): Quirk? = identifier?.let { table[it] }

    fun of(mon: OwnedPokemon): Quirk? = of(mon.species.identifier)

    /** Whether [quirk] keeps a Pokémon in one place, so it is set down in a room and not in a corridor others need. */
    fun stays(quirk: Quirk?): Boolean = quirk == Quirk.ROOTED || quirk == Quirk.ASLEEP

    /** How fast it walks, against the usual pace of 1. */
    fun pace(quirk: Quirk?): Float = when (quirk) { Quirk.SLOW -> 0.5f; Quirk.FAST -> 1.9f; else -> 1f }

    /** How restless it is, against the usual temper of 1 (see [TownBehavior.temper]). */
    fun temper(quirk: Quirk?): Float = when (quirk) { Quirk.SLOW -> 0.6f; Quirk.FAST -> 1.4f; else -> 1f }

    /** The chance in a hundred that a step of an erratic walk (or a confused one) stumbles to the side. */
    fun stumble(quirk: Quirk?, confused: Boolean): Int = if (quirk == Quirk.ERRATIC) 30 else if (confused) 8 else 0

    /**
     * Which of the others [mon] looks like while it is disguised: another species that is in the town, the same one each time (Ditto copies, Zoroark
     * has its illusion). Null when nobody else is there to copy.
     */
    fun disguiseFor(mon: OwnedPokemon, town: List<OwnedPokemon>): OwnedPokemon? {
        val pool = town.filter { it.speciesId != mon.speciesId && of(it) != Quirk.DISGUISED }.sortedBy { it.id }
        return if (pool.isEmpty()) null else pool[(mon.id.hashCode() and 0x7fffffff) % pool.size]
    }

    /**
     * The icon a Guardian shows now and then over its head: the game's stat icon of its first type (a flame, a block of ice, a fist, a shield, a
     * flash, a skull) and a sparkle for the rest, so each carries its power about the town like a banner.
     */
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
