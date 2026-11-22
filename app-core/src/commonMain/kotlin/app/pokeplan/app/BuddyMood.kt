package app.pokeplan.app

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.BondStyle
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random

object BuddyScene {
    const val PLAN = "city"

    const val CARE = "meadow"

    const val HEAL = "route"

    const val MINT = "dampcave"

    const val BASE = "forest"

    const val LINK = "route"

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

    fun isNight(hour: Int): Boolean = hour >= 20 || hour < 6

    const val MAX_DARKNESS = 0.6f

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

enum class ReactionIcon(val key: String) {
    TWINKLE("twinkle"), ALERT("alert"), QUESTION("question"), SLEEP("sleep"),
    FLAME("flame"), ZAP("zap"), ICE("ice"), FIST("fist"), SHIELD("shield"), DROP("drop"),
    HEART("heart"), NOTE("note"), SUN("sun"), MOON("moon"), STAR("star"), APPLE("apple"),
    ;

    companion object {
        fun of(key: String?): ReactionIcon? = entries.firstOrNull { it.key == key }
    }
}

enum class BuddyEvent(val icon: ReactionIcon) {
    SHINY(ReactionIcon.STAR),
    HATCHED(ReactionIcon.ALERT),
    DONE(ReactionIcon.TWINKLE),
}

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

    fun petPool(buddy: OwnedPokemon, hour: Int): List<ReactionIcon> =
        (byBond.getValue(buddy.bondStyle) + buddy.form.types.flatMap { byType[it].orEmpty() } + timeOfDay(hour)).distinct()

    private fun timeOfDay(hour: Int) = if (BuddyScene.isNight(hour)) night else if (hour in 6..9) morning else emptyList()

    fun pet(buddy: OwnedPokemon, hour: Int, random: Random = Random, avoid: ReactionIcon? = null): ReactionIcon {
        var icon = pick(buddy, hour, random)
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

    fun feed(buddy: OwnedPokemon, random: Random = Random, avoid: ReactionIcon? = null): ReactionIcon {
        val pool = if (buddy.bondStyle == BondStyle.GIFTS) listOf(ReactionIcon.HEART, ReactionIcon.APPLE, ReactionIcon.STAR) else listOf(ReactionIcon.APPLE, ReactionIcon.STAR, ReactionIcon.TWINKLE)
        return (pool - setOfNotNull(avoid)).random(random)
    }
}

data class BuddyEventNow(val event: BuddyEvent, val atMillis: Long)
