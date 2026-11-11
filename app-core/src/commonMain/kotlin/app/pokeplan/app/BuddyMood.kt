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
 * What a reaction shows, above the buddy's head, as the Mystery Dungeon games show a feeling: a status icon where the games
 * have one (the "!", the "?", a sparkle, a flame, a bolt, a block of ice, a fist, a shield, a drop, the Zz) and a real item
 * icon or Pixelarticons glyph where they have none (a heart, a note, the sun, the moon, a star, an apple). The UI draws each
 * from its asset; [key] is the stable name a widget keeps between runs.
 */
enum class ReactionIcon(val key: String) {
    TWINKLE("twinkle"), ALERT("alert"), QUESTION("question"), SLEEP("sleep"),
    FLAME("flame"), ZAP("zap"), ICE("ice"), FIST("fist"), SHIELD("shield"), DROP("drop"),
    HEART("heart"), NOTE("note"), SUN("sun"), MOON("moon"), STAR("star"), APPLE("apple"),
    ;

    companion object {
        fun of(key: String?): ReactionIcon? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What just happened that is worth a small reaction: the buddy answers with one icon above its head, like a game emote,
 * and never with sentences. There is no permanent speech bubble, no needs and no mood meter.
 */
enum class BuddyEvent(val icon: ReactionIcon) {
    SHINY(ReactionIcon.STAR),
    HATCHED(ReactionIcon.ALERT),
    DONE(ReactionIcon.TWINKLE),
}

/**
 * The buddy's reactions to a pet or a berry: one icon, chosen from how this particular Pokémon is. Its bond style (from
 * its nature) decides most of them, its type adds flavour (a flame for Fire, a bolt for Electric), and the hour of the day
 * sometimes adds a sleepy or a bright one. Never words, never anything that reads as the buddy being unhappy.
 */
object BuddyReaction {
    private val byBond = mapOf(
        BondStyle.COMFORT to listOf(ReactionIcon.HEART, ReactionIcon.TWINKLE, ReactionIcon.SUN, ReactionIcon.NOTE, ReactionIcon.STAR),
        BondStyle.PRESENCE to listOf(ReactionIcon.QUESTION, ReactionIcon.TWINKLE, ReactionIcon.STAR, ReactionIcon.SUN, ReactionIcon.NOTE),
        BondStyle.GIFTS to listOf(ReactionIcon.APPLE, ReactionIcon.HEART, ReactionIcon.STAR, ReactionIcon.TWINKLE, ReactionIcon.ALERT),
        BondStyle.BALANCED to listOf(ReactionIcon.HEART, ReactionIcon.NOTE, ReactionIcon.SUN, ReactionIcon.STAR, ReactionIcon.TWINKLE),
    )

    private val byType = mapOf(
        PokemonType.NORMAL to listOf(ReactionIcon.NOTE, ReactionIcon.STAR), PokemonType.FIRE to listOf(ReactionIcon.FLAME, ReactionIcon.SUN),
        PokemonType.WATER to listOf(ReactionIcon.DROP, ReactionIcon.NOTE), PokemonType.GRASS to listOf(ReactionIcon.SUN, ReactionIcon.APPLE),
        PokemonType.ELECTRIC to listOf(ReactionIcon.ZAP, ReactionIcon.TWINKLE), PokemonType.ICE to listOf(ReactionIcon.ICE, ReactionIcon.TWINKLE),
        PokemonType.FIGHTING to listOf(ReactionIcon.FIST, ReactionIcon.STAR), PokemonType.POISON to listOf(ReactionIcon.DROP, ReactionIcon.QUESTION),
        PokemonType.GROUND to listOf(ReactionIcon.SUN, ReactionIcon.SHIELD), PokemonType.FLYING to listOf(ReactionIcon.NOTE, ReactionIcon.SUN),
        PokemonType.PSYCHIC to listOf(ReactionIcon.QUESTION, ReactionIcon.TWINKLE), PokemonType.BUG to listOf(ReactionIcon.QUESTION, ReactionIcon.SUN),
        PokemonType.ROCK to listOf(ReactionIcon.SHIELD, ReactionIcon.STAR), PokemonType.GHOST to listOf(ReactionIcon.MOON, ReactionIcon.QUESTION),
        PokemonType.DRAGON to listOf(ReactionIcon.FLAME, ReactionIcon.STAR), PokemonType.DARK to listOf(ReactionIcon.MOON, ReactionIcon.STAR),
        PokemonType.STEEL to listOf(ReactionIcon.SHIELD, ReactionIcon.FIST), PokemonType.FAIRY to listOf(ReactionIcon.HEART, ReactionIcon.TWINKLE),
    )

    private val night = listOf(ReactionIcon.SLEEP, ReactionIcon.MOON)
    private val morning = listOf(ReactionIcon.SUN, ReactionIcon.TWINKLE)

    /** Every icon a pet can show for [buddy]; for tests and for a future reaction picker. */
    fun petPool(buddy: OwnedPokemon, hour: Int): List<ReactionIcon> =
        (byBond.getValue(buddy.bondStyle) + buddy.form.types.flatMap { byType[it].orEmpty() } + timeOfDay(hour)).distinct()

    private fun timeOfDay(hour: Int) = if (BuddyScene.isNight(hour)) night else if (hour in 6..9) morning else emptyList()

    /** The icon for a pet right now: about half from its bond style, a third from its type, the rest from the time of day. */
    fun pet(buddy: OwnedPokemon, hour: Int, random: Random = Random, avoid: ReactionIcon? = null): ReactionIcon {
        var icon = pick(buddy, hour, random)
        // Never the same one twice in a row.
        repeat(5) { if (icon == avoid) icon = pick(buddy, hour, random) }
        return icon
    }

    private fun pick(buddy: OwnedPokemon, hour: Int, random: Random): ReactionIcon {
        val roll = random.nextInt(100)
        val pool = when {
            roll < 50 -> byBond.getValue(buddy.bondStyle)
            roll < 85 -> buddy.form.types.flatMap { byType[it].orEmpty() }.ifEmpty { byBond.getValue(buddy.bondStyle) }
            else -> timeOfDay(hour).ifEmpty { byBond.getValue(buddy.bondStyle) }
        }
        return pool.random(random)
    }

    /** The icon for a berry: delighted for a Pokémon that loves gifts, otherwise a happy bite. */
    fun feed(buddy: OwnedPokemon, random: Random = Random, avoid: ReactionIcon? = null): ReactionIcon {
        val pool = if (buddy.bondStyle == BondStyle.GIFTS) listOf(ReactionIcon.HEART, ReactionIcon.APPLE, ReactionIcon.STAR) else listOf(ReactionIcon.APPLE, ReactionIcon.STAR, ReactionIcon.TWINKLE)
        return (pool - setOfNotNull(avoid)).random(random)
    }
}

/** A [BuddyEvent] and when its reward was paid, so a screen can celebrate it once and not again every time it is opened. */
data class BuddyEventNow(val event: BuddyEvent, val atMillis: Long)
