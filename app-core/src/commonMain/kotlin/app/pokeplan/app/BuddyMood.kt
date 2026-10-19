package app.pokeplan.app

import app.pokeplan.core.model.PokemonType

/** Where the buddy lives on Home and in the widget: a real scene picked from its type (assets/scenes), dimmed at night. */
object BuddyScene {
    /** The scene file name (without .png) for a Pokémon whose types are [types]. */
    fun of(types: List<PokemonType>): String = when (types.firstOrNull()) {
        PokemonType.FIRE -> "mountain"
        PokemonType.WATER -> "beach"
        PokemonType.GRASS, PokemonType.BUG -> "forest"
        PokemonType.ELECTRIC -> "thunderplains"
        PokemonType.ICE -> "icecave"
        PokemonType.GROUND, PokemonType.ROCK -> "desert"
        PokemonType.POISON, PokemonType.GHOST, PokemonType.DARK, PokemonType.PSYCHIC -> "dampcave"
        PokemonType.DRAGON -> "volcanocave"
        PokemonType.FIGHTING, PokemonType.STEEL -> "route"
        else -> "meadow"
    }

    /** The scene darkens between 8 pm and 6 am. */
    fun isNight(hour: Int): Boolean = hour >= 20 || hour < 6
}

/**
 * What just happened that is worth a short factual message and one small celebration. The buddy never chats otherwise:
 * no permanent speech bubble, no needs, no mood meter.
 */
enum class BuddyEvent(val message: String) {
    HATCHED("A new Pokémon hatched!"),
    DONE("Nice work!"),
}

/** A [BuddyEvent] and when its reward was paid, so a screen can celebrate it once and not again every time it is opened. */
data class BuddyEventNow(val event: BuddyEvent, val atMillis: Long)
