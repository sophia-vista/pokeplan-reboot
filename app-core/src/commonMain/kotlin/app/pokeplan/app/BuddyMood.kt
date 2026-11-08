package app.pokeplan.app

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.BondStyle
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random

/**
 * The real scenes Pokémon stand in (assets/scenes), dimmed at night. A region's lead stands in its region's scene (Plan's
 * town, Care's meadow: docs/ecosystem.md section 11, "Three visual layers"); a Pokédex entry shows a scene from its type.
 */
object BuddyScene {
    /** Plan's region scene: a town street, where the Plan buddy stands in the app and the widgets. */
    const val PLAN = "city"

    /** Care's region scene: the meadow, where Care's lead and its visitors stand. */
    const val CARE = "meadow"

    /** Heal's region scene: an open route, for movement. */
    const val HEAL = "route"

    /** Mint's region scene: a quiet cave of rock and ore, for the steady keepers. */
    const val MINT = "dampcave"

    /** Home's region scene: the town, for the household's upkeep. */
    const val HOME = "city"

    /** Link's region scene: the route, a postcard trail. */
    const val LINK = "route"

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

    /** Between 8 pm and 6 am: the time-of-day reactions use this. */
    fun isNight(hour: Int): Boolean = hour >= 20 || hour < 6

    /** The darkest the scene gets, as the strength of its night tint (0 to 1). */
    const val MAX_DARKNESS = 0.6f

    /**
     * How dark the scene is at [hour]:[minute]: none in the day, a slow dusk from 6 pm to 9 pm, full night until 5 am and
     * a dawn until 7 am, so the scene never switches abruptly.
     */
    fun darkness(hour: Int, minute: Int): Float {
        val t = hour + minute / 60f
        return when {
            t >= 21f || t < 5f -> MAX_DARKNESS
            t >= 18f -> MAX_DARKNESS * (t - 18f) / 3f
            t < 7f -> MAX_DARKNESS * (7f - t) / 2f
            else -> 0f
        }
    }
}

/**
 * What just happened that is worth a small reaction: the buddy answers with one emoji above its head, like a game emote,
 * and never with sentences. There is no permanent speech bubble, no needs and no mood meter.
 */
enum class BuddyEvent(val emoji: String) {
    SHINY("🌟"),
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
    fun pet(buddy: OwnedPokemon, hour: Int, random: Random = Random, avoid: String? = null): String {
        var emoji = pick(buddy, hour, random)
        // Never the same one twice in a row.
        repeat(5) { if (emoji == avoid) emoji = pick(buddy, hour, random) }
        return emoji
    }

    private fun pick(buddy: OwnedPokemon, hour: Int, random: Random): String {
        val roll = random.nextInt(100)
        val pool = when {
            roll < 50 -> byBond.getValue(buddy.bondStyle)
            roll < 85 -> buddy.form.types.flatMap { byType[it].orEmpty() }.ifEmpty { byBond.getValue(buddy.bondStyle) }
            else -> timeOfDay(hour).ifEmpty { byBond.getValue(buddy.bondStyle) }
        }
        return pool.random(random)
    }

    /** The emoji for a berry: delighted for a Pokémon that loves gifts, otherwise a happy bite. */
    fun feed(buddy: OwnedPokemon, random: Random = Random, avoid: String? = null): String {
        val pool = if (buddy.bondStyle == BondStyle.GIFTS) listOf("😍", "🥰", "💝") else listOf("😋", "🍓", "😊")
        return (pool - setOfNotNull(avoid)).random(random)
    }
}

/** A [BuddyEvent] and when its reward was paid, so a screen can celebrate it once and not again every time it is opened. */
data class BuddyEventNow(val event: BuddyEvent, val atMillis: Long)
