package app.pokeplan.app

import app.pokeplan.core.model.PokemonType
import app.pokeplan.core.pokemon.BondStyle
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random

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

/**
 * The buddy's reactions to a pet or a berry: one emoji, chosen from how this particular Pokémon is. Its bond style (from
 * its nature) decides most of them, its type adds flavour, and the hour of the day sometimes adds a sleepy or a bright
 * one. Never words, never anything that reads as the buddy being unhappy.
 */
object BuddyReaction {
    private val byBond = mapOf(
        BondStyle.COMFORT to listOf("🥰", "😌", "💞", "🤗", "☺️"),
        BondStyle.PRESENCE to listOf("👀", "✨", "😊", "🙂", "💫"),
        BondStyle.GIFTS to listOf("🎁", "😍", "🍓", "😋", "💝"),
        BondStyle.BALANCED to listOf("❤️", "😊", "🎵", "😄", "🌟"),
    )

    private val byType = mapOf(
        PokemonType.NORMAL to listOf("🎵", "🙂"), PokemonType.FIRE to listOf("🔥", "😤"), PokemonType.WATER to listOf("💧", "🌊"),
        PokemonType.GRASS to listOf("🌱", "🍃"), PokemonType.ELECTRIC to listOf("⚡", "😆"), PokemonType.ICE to listOf("❄️", "⛄"),
        PokemonType.FIGHTING to listOf("💪", "🥋"), PokemonType.POISON to listOf("🧪", "😏"), PokemonType.GROUND to listOf("🌵", "🏜️"),
        PokemonType.FLYING to listOf("🪶", "🌤️"), PokemonType.PSYCHIC to listOf("🔮", "🌀"), PokemonType.BUG to listOf("🐛", "🍃"),
        PokemonType.ROCK to listOf("🪨", "💎"), PokemonType.GHOST to listOf("👻", "🌙"), PokemonType.DRAGON to listOf("🐲", "💥"),
        PokemonType.DARK to listOf("🌙", "😎"), PokemonType.STEEL to listOf("🛡️", "💪"), PokemonType.FAIRY to listOf("🎀", "🌸"),
    )

    private val night = listOf("😴", "💤", "🌙")
    private val morning = listOf("☀️", "🌅", "😊")

    /** Every emoji a pet can show for [buddy]; for tests and for a future reaction picker. */
    fun petPool(buddy: OwnedPokemon, hour: Int): List<String> =
        (byBond.getValue(buddy.bondStyle) + buddy.form.types.flatMap { byType[it].orEmpty() } + timeOfDay(hour)).distinct()

    private fun timeOfDay(hour: Int) = if (BuddyScene.isNight(hour)) night else if (hour in 6..9) morning else emptyList()

    /** The emoji for a pet right now: about half from its bond style, a third from its type, the rest from the time of day. */
    fun pet(buddy: OwnedPokemon, hour: Int, random: Random = Random): String {
        val roll = random.nextInt(100)
        val timePool = timeOfDay(hour)
        val pool = when {
            roll < 50 -> byBond.getValue(buddy.bondStyle)
            roll < 85 -> buddy.form.types.flatMap { byType[it].orEmpty() }.ifEmpty { byBond.getValue(buddy.bondStyle) }
            else -> timePool.ifEmpty { byBond.getValue(buddy.bondStyle) }
        }
        return pool.random(random)
    }

    /** The emoji for a berry: delighted for a Pokémon that loves gifts, otherwise a happy bite. */
    fun feed(buddy: OwnedPokemon, random: Random = Random): String =
        (if (buddy.bondStyle == BondStyle.GIFTS) listOf("😍", "🥰", "💝") else listOf("😋", "🍓", "😊")).random(random)
}

/** A [BuddyEvent] and when its reward was paid, so a screen can celebrate it once and not again every time it is opened. */
data class BuddyEventNow(val event: BuddyEvent, val atMillis: Long)
