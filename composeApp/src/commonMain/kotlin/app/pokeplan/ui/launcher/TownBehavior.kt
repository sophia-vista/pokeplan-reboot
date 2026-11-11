package app.pokeplan.ui.launcher

import androidx.compose.ui.geometry.Offset
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random

/**
 * What a Pokémon does next in the town (LAUNCHER.md 6.2): a small, bounded set of beats picked from a weighted roll. Everything here is
 * cosmetic and uses only the animations the sprite sheets have (idle, eat, hop, the walks, sleep) and the status icons; the rules are pure, so they are tested.
 */
internal sealed interface Beat {
    /** Stands still (the idle animation) for [millis]. */
    data class Stand(val millis: Long) : Beat
    /** Turns to face [left] or right and stands a moment. */
    data class Turn(val left: Boolean, val millis: Long) : Beat
    /** Eats from the ground (the eat animation, three times). */
    data object Graze : Beat
    /** One hop on the spot. */
    data object Hop : Beat
    /** Walks [legs] short legs, glancing about between them. */
    data class Roam(val legs: Int) : Beat
}

/** What a tap on a Pokémon does besides its cry (LAUNCHER.md 6.4): all of them are brief, nonverbal and cost nothing. */
internal enum class TapReaction { HOP, HAPPY, TURN, FLUSTERED }

/** How a Pokémon feels about the weather over the town: shown as a status icon (a twinkle or a drop of sweat). */
internal enum class Feeling { ENJOYS, DISLIKES }

internal object TownBehavior {
    /**
     * The next beat. A Pokémon stands, looks about, grazes, hops or roams; the Guardian stands and looks far longer and roams short and rarely, so the town's
     * biggest Pokémon is a presence and not a wanderer. [facingLeft] is the way it faces now, so a turn is always a change of direction.
     */
    fun next(random: Random, guardian: Boolean, facingLeft: Boolean): Beat {
        val roll = random.nextInt(100)
        return if (guardian) when {
            roll < 50 -> Beat.Stand(7_000 + random.nextLong(9_000))
            roll < 75 -> Beat.Turn(!facingLeft, 3_000 + random.nextLong(4_000))
            roll < 85 -> Beat.Graze
            else -> Beat.Roam(1)
        } else when {
            roll < 20 -> Beat.Stand(1_200 + random.nextLong(3_500))
            roll < 38 -> Beat.Turn(!facingLeft, 900 + random.nextLong(1_800))
            roll < 48 -> Beat.Graze
            roll < 54 -> Beat.Hop
            else -> Beat.Roam(1 + random.nextInt(3))
        }
    }

    /** [id] spread evenly over 0 to 1 (a multiplicative hash, so ids that differ in one character still land far apart); [salt] makes each use its own spread. */
    private fun spread(id: String, salt: Int): Float = (((id.hashCode() xor salt) * -1640531535) ushr 24) / 255f

    /** A steady pace for each Pokémon, always the same one: from 0.8 to 1.25 times the usual stroll. */
    fun pace(id: String): Float = 0.8f + spread(id, 0x1234567) * 0.45f

    /** The place a Pokémon likes to be, as the town's fractions (x in screens, y from the back of the ground to the front): the same each time, near the middle of Home for the buddy. */
    fun anchor(id: String, screens: Int, home: Int, buddy: Boolean): Offset {
        val random = Random(id.hashCode())
        return if (buddy) Offset(home + 0.35f + random.nextFloat() * 0.3f, 0.55f + random.nextFloat() * 0.3f)
        else Offset(random.nextFloat() * screens, random.nextFloat())
    }

    /**
     * Where a roam goes: mostly near its [anchor] (so the town has quiet corners and a recognisable buddy), now and then anywhere, and for the Guardian only a short way.
     * The town has no side walls, so a target may lie a little past either edge.
     */
    fun target(random: Random, anchor: Offset, screens: Int, guardian: Boolean): Offset {
        val reach = if (guardian) 0.12f else 0.3f
        return if (guardian || random.nextInt(100) < 72) {
            Offset((anchor.x + (random.nextFloat() * 2f - 1f) * reach).coerceIn(-0.2f, screens + 0.2f), (anchor.y + (random.nextFloat() * 2f - 1f) * reach * 1.4f).coerceIn(0f, 1f))
        } else Offset(-0.2f + random.nextFloat() * (screens + 0.4f), random.nextFloat())
    }

    /** Where a Guardian walks in from: just off the left or the right edge of Home. */
    fun entrance(random: Random, home: Int): Offset = Offset(if (random.nextBoolean()) home - 0.18f else home + 1.18f, 0.2f + random.nextFloat() * 0.6f)

    /** Where a leaving Guardian walks to: off the nearer edge of Home. */
    fun exit(from: Offset, home: Int): Offset = Offset(if (from.x < home + 0.5f) home - 0.25f else home + 1.25f, from.y)

    /** How a tap goes: a hop, a happy one, a turn about, and, when it is tapped over and over ([recentTaps] in the last two seconds), flustered. */
    fun tapReaction(random: Random, recentTaps: Int): TapReaction {
        if (recentTaps >= 3) return TapReaction.FLUSTERED
        val roll = random.nextInt(100)
        return if (roll < 45) TapReaction.HOP else if (roll < 75) TapReaction.HAPPY else TapReaction.TURN
    }

    /**
     * When this one lies down for the night: the darkness at which it sleeps, between 0.4 and 0.65, so they do not all drop off in the same minute, and the same
     * threshold wakes it in the morning. A third of the Pokémon sleep at night, always the same ones ([sleepsAtNight]).
     */
    fun bedtime(id: String): Float = 0.4f + spread(id, 0x7654321) * 0.25f

    /** Whether [mon] is asleep at this [darkness] (0 day, 1 night). */
    fun asleep(mon: OwnedPokemon, darkness: Float): Boolean = sleepsAtNight(mon) && darkness >= bedtime(mon.id)

    /**
     * How a Pokémon of these [types] feels about [weather]: those at home in it enjoy it (water and grass in rain, ice in snow, ground, rock and steel in sand,
     * flying and dragons under cloud shadows), those that suffer in it do not. Null: it does not mind.
     */
    fun feeling(types: List<PokemonType>, weather: Weather): Feeling? {
        val (likes, dislikes) = when (weather) {
            Weather.RAIN -> setOf(PokemonType.WATER, PokemonType.GRASS) to setOf(PokemonType.FIRE, PokemonType.GROUND)
            Weather.SNOW, Weather.BLIZZARD -> setOf(PokemonType.ICE) to setOf(PokemonType.FIRE, PokemonType.GRASS, PokemonType.BUG)
            Weather.SANDSTORM -> setOf(PokemonType.GROUND, PokemonType.ROCK, PokemonType.STEEL) to setOf(PokemonType.WATER, PokemonType.GRASS, PokemonType.ICE)
            Weather.CLOUDS -> setOf(PokemonType.FLYING, PokemonType.DRAGON) to emptySet()
            else -> return null
        }
        return if (types.any { it in likes }) Feeling.ENJOYS else if (types.any { it in dislikes }) Feeling.DISLIKES else null
    }

    /** How many whole days the Guardian of the day has been counted from: the Guardians take turns, one a day. */
    fun rotation(epochDay: Long): Int = (epochDay % 1_000_000L).toInt()
}
