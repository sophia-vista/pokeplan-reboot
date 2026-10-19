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
 * What just happened that is worth a small reaction: the buddy answers with one emoji above its head, like a game emote,
 * and never with sentences. There is no permanent speech bubble, no needs and no mood meter.
 */
enum class BuddyEvent(val emoji: String) {
    HATCHED("🎉"),
    DONE("✨"),
}

/** The emoji for the buddy's other reactions. */
object BuddyReaction {
    const val PET = "❤️"
    const val FEED = "😋"
}

/** A [BuddyEvent] and when its reward was paid, so a screen can celebrate it once and not again every time it is opened. */
data class BuddyEventNow(val event: BuddyEvent, val atMillis: Long)
