package app.pokeplan.ui.launcher

import androidx.compose.ui.geometry.Offset
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
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
    /** Walks [legs] connected legs, glancing about between them; a [short] one is a step or two to shift its weight. */
    data class Roam(val legs: Int, val short: Boolean = false) : Beat
}

/** What a tap on a Pokémon does besides its cry (LAUNCHER.md 6.4): all of them are brief, nonverbal and cost nothing. */
internal enum class TapReaction { HOP, HAPPY, TURN, FLUSTERED }

/** How a Pokémon feels about the weather over the town: shown as a status icon (a twinkle or a drop of sweat). */
internal enum class Feeling { ENJOYS, DISLIKES }

internal object TownBehavior {
    /** The town's liveliness when nothing is set (Settings → Town → How lively, 1 to 10): calm, so the town reads as a place and not a crowd. */
    const val ACTIVITY_DEFAULT = 4

    /** A [level] of 1 to 10 as 0 (sleepy) to 1 (restless). */
    fun liveliness(level: Int): Float = (level.coerceIn(1, 10) - 1) / 9f

    /** The word the setting shows for a [level]. */
    fun activityLabel(level: Int): String = when (level.coerceIn(1, 10)) { 1, 2 -> "Sleepy"; 3, 4 -> "Calm"; 5, 6 -> "Easy"; 7, 8 -> "Lively"; else -> "Restless" }

    /** How restless this Pokémon is by nature, the same each time: from 0.7 to 1.3 times the town's liveliness, so some potter about while others rest. */
    fun temper(id: String): Float = 0.7f + spread(id, 0x2468ACE) * 0.6f

    /**
     * Whether a Pokémon is in a restless spell after this beat (it was [restless]): spells of rest and spells of pottering about that last a while, so the town has
     * a rhythm (a few walk while most rest, and it changes) instead of every Pokémon rolling a fresh dice each beat. [lively] is from [liveliness].
     * About 15% of the time restless at the sleepiest, 35% at the default, 70% at the most restless; the Guardian half as much.
     */
    fun spell(random: Random, restless: Boolean, lively: Float, temper: Float = 1f, guardian: Boolean = false): Boolean {
        val start = (0.06f + 0.30f * lively) * temper * (if (guardian) 0.5f else 1f)
        val stop = 0.38f - 0.22f * lively
        return if (restless) random.nextFloat() >= stop else random.nextFloat() < start
    }

    /**
     * The next beat. Resting, a Pokémon stands, looks about, grazes, now and then hops or shifts a step; restless, it walks a few connected legs with short
     * pauses. The Guardian stands and looks far longer and roams short and rarely, so the town's biggest Pokémon is a presence and not a wanderer. [facingLeft]
     * is the way it faces now, so a turn is always a change of direction. The sleepier the town ([lively] near 0), the longer the rests.
     */
    fun next(random: Random, guardian: Boolean, facingLeft: Boolean, restless: Boolean = false, lively: Float = liveliness(ACTIVITY_DEFAULT)): Beat {
        val roll = random.nextInt(100)
        // Rests stretch out in a sleepy town and shorten in a lively one.
        val slow = 1.5f - lively
        fun ms(base: Int, more: Int) = ((base + random.nextInt(more)) * slow).toLong()
        return if (guardian) when {
            roll < 50 -> Beat.Stand(((7_000 + random.nextLong(9_000)) * (1.3f - 0.5f * lively)).toLong())
            roll < 75 -> Beat.Turn(!facingLeft, 3_000 + random.nextLong(4_000))
            roll < 85 -> Beat.Graze
            else -> Beat.Roam(1, short = !restless)
        } else if (restless) when {
            roll < 12 -> Beat.Stand(800 + random.nextLong(1_800))
            roll < 24 -> Beat.Turn(!facingLeft, 600 + random.nextLong(1_100))
            roll < 30 -> Beat.Graze
            roll < 34 -> Beat.Hop
            else -> Beat.Roam(2 + random.nextInt(3))
        } else when {
            roll < 40 -> Beat.Stand(ms(2_500, 5_000))
            roll < 63 -> Beat.Turn(!facingLeft, ms(1_400, 2_200))
            roll < 77 -> Beat.Graze
            roll < 81 -> Beat.Hop
            else -> Beat.Roam(1, short = true)
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
     * The ground is drawn in perspective: a whole step from the back to the front ([Offset.y] 0 to 1) looks about this many screen widths long. Directions and
     * distances are worked out in that "looks like" space, so a walk that turns does not suddenly speed up or stretch when it heads towards the viewer.
     */
    const val DEPTH = 0.4f

    /** One leg of a walk: where it ends and the way it was heading ([heading] in radians, 0 to the right, in the "looks like" space of [DEPTH]). */
    data class Leg(val to: Offset, val heading: Float)

    /**
     * The next leg of a walk from [from], heading about [heading]: a correlated random walk, so it keeps roughly to its way with small turns (a sum of two
     * rolls, so gentle turns are common and about-turns rare), bends back towards its [anchor] the further it has strayed (so each Pokémon has a corner of its
     * own), and leans along the back and the front of the ground near them. Now and then, more often in a lively town, it sets off for anywhere in the town instead.
     * A [short] leg is a step or two; the Guardian never goes far from its anchor. The town has no side walls, so a leg may end a little past either edge.
     */
    fun leg(random: Random, from: Offset, heading: Float, anchor: Offset, screens: Int, guardian: Boolean, short: Boolean = false, lively: Float = liveliness(ACTIVITY_DEFAULT)): Leg {
        if (!guardian && !short && random.nextInt(100) < (2 + 6 * lively).toInt()) {
            val to = Offset(-0.2f + random.nextFloat() * (screens + 0.4f), 0.05f + random.nextFloat() * 0.9f)
            return Leg(to, atan2((to.y - from.y) * DEPTH, to.x - from.x))
        }
        val range = if (guardian) 0.08f else 0.4f
        var h = heading + (random.nextFloat() + random.nextFloat() - 1f) * 0.9f
        val ax = anchor.x - from.x
        val ay = (anchor.y - from.y) * DEPTH
        val strayed = hypot(ax, ay)
        if (strayed > 0.001f) h = blend(h, atan2(ay, ax), ((strayed / range) * (strayed / range)).coerceAtMost(1f) * if (guardian) 0.85f else 0.5f)
        // Near the back or the front of the ground it leans into walking along it, rather than marching up to the edge and bouncing off.
        val edge = ((0.25f - minOf(from.y, 1f - from.y)) / 0.25f).coerceIn(0f, 1f)
        if (edge > 0f) h = blend(h, if (cos(h) >= 0f) 0f else PI.toFloat(), edge * 0.8f)
        val length = (if (guardian) 0.04f + random.nextFloat() * 0.05f else 0.09f + random.nextFloat() * 0.14f) * (if (short) 0.4f else 1f)
        var y = from.y + sin(h) * length / DEPTH
        if (y < 0.03f || y > 0.97f) { h = -h; y = from.y + sin(h) * length / DEPTH }
        var x = from.x + cos(h) * length
        if (guardian) x = x.coerceIn(anchor.x - 0.12f, anchor.x + 0.12f)
        x = x.coerceIn(-0.2f, screens + 0.2f)
        return Leg(Offset(x, y.coerceIn(0.02f, 0.98f)), h)
    }

    /** [from] turned a [share] of the shorter way towards [to] (angles in radians). */
    private fun blend(from: Float, to: Float, share: Float): Float {
        var d = (to - from) % (2f * PI.toFloat())
        if (d > PI) d -= 2f * PI.toFloat() else if (d < -PI) d += 2f * PI.toFloat()
        return from + d * share
    }

    /** Whether [at] is too close to any of [others] to stop there: the Pokémon keep a little room between them, as people do. */
    fun crowded(at: Offset, others: List<Offset>, room: Float = 0.08f): Boolean = others.any { hypot(it.x - at.x, (it.y - at.y) * DEPTH) < room }

    /**
     * The way a stroll from [from] to [to] goes, as [steps] points to pass at even times: a gentle curve (bent to one side by up to a sixth of its length, never
     * a ruler line) that starts and ends a little slower than its middle, so a Pokémon eases into a walk and settles out of it. The last point is [to].
     */
    fun path(random: Random, from: Offset, to: Offset, steps: Int = 8): List<Offset> {
        val mx = to.x - from.x
        val my = (to.y - from.y) * DEPTH
        val bend = (random.nextFloat() * 2f - 1f) * 0.17f
        // The bend is across the way it goes (in the "looks like" space), then back into the ground's fractions.
        val cx = (from.x + to.x) / 2f - my * bend
        val cy = (from.y + to.y) / 2f + mx * bend / DEPTH
        return List(steps) { i ->
            val u = (i + 1f) / steps
            // Partly linear, partly smoothstep: the pace dips at the ends without the feet sliding to a stop.
            val t = u * 0.45f + (u * u * (3f - 2f * u)) * 0.55f
            if (i == steps - 1) to else {
                val a = (1 - t) * (1 - t)
                val b = 2 * (1 - t) * t
                val c = t * t
                Offset(a * from.x + b * cx + c * to.x, (a * from.y + b * cy + c * to.y).coerceIn(0f, 1f))
            }
        }
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

    /** Whether [mon] is asleep at this [darkness] (0 day, 1 night); the sleepy species ([Quirk.ASLEEP]) are asleep at any hour. */
    fun asleep(mon: OwnedPokemon, darkness: Float): Boolean = TownQuirks.of(mon) == Quirk.ASLEEP || (sleepsAtNight(mon) && darkness >= bedtime(mon.id))

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
