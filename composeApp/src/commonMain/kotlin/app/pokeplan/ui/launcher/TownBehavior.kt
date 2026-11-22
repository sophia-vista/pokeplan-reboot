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

internal sealed interface Beat {
    data class Stand(val millis: Long) : Beat
    data class Turn(val left: Boolean, val millis: Long) : Beat
    data object Graze : Beat
    data object Hop : Beat
    data class Roam(val legs: Int, val short: Boolean = false) : Beat
}

internal enum class TapReaction { HOP, HAPPY, TURN, FLUSTERED }

internal enum class Feeling { ENJOYS, DISLIKES }

internal object TownBehavior {
    const val ACTIVITY_DEFAULT = 4

    fun liveliness(level: Int): Float = (level.coerceIn(1, 10) - 1) / 9f

    fun activityLabel(level: Int): String = when (level.coerceIn(1, 10)) { 1, 2 -> "Sleepy"; 3, 4 -> "Calm"; 5, 6 -> "Easy"; 7, 8 -> "Lively"; else -> "Restless" }

    fun temper(id: String): Float = 0.7f + spread(id, 0x2468ACE) * 0.6f

    fun spell(random: Random, restless: Boolean, lively: Float, temper: Float = 1f, guardian: Boolean = false): Boolean {
        val start = (0.06f + 0.30f * lively) * temper * (if (guardian) 0.5f else 1f)
        val stop = 0.38f - 0.22f * lively
        return if (restless) random.nextFloat() >= stop else random.nextFloat() < start
    }

    fun next(random: Random, guardian: Boolean, facingLeft: Boolean, restless: Boolean = false, lively: Float = liveliness(ACTIVITY_DEFAULT)): Beat {
        val roll = random.nextInt(100)
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

    private fun spread(id: String, salt: Int): Float = (((id.hashCode() xor salt) * -1640531535) ushr 24) / 255f

    fun pace(id: String): Float = 0.8f + spread(id, 0x1234567) * 0.45f

    fun anchor(id: String, screens: Int, home: Int, buddy: Boolean): Offset {
        val random = Random(id.hashCode())
        return if (buddy) Offset(home + 0.35f + random.nextFloat() * 0.3f, 0.55f + random.nextFloat() * 0.3f)
        else Offset(random.nextFloat() * screens, random.nextFloat())
    }

    const val DEPTH = 0.4f

    data class Leg(val to: Offset, val heading: Float)

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

    private fun blend(from: Float, to: Float, share: Float): Float {
        var d = (to - from) % (2f * PI.toFloat())
        if (d > PI) d -= 2f * PI.toFloat() else if (d < -PI) d += 2f * PI.toFloat()
        return from + d * share
    }

    fun crowded(at: Offset, others: List<Offset>, room: Float = 0.08f): Boolean = others.any { hypot(it.x - at.x, (it.y - at.y) * DEPTH) < room }

    fun path(random: Random, from: Offset, to: Offset, steps: Int = 8): List<Offset> {
        val mx = to.x - from.x
        val my = (to.y - from.y) * DEPTH
        val bend = (random.nextFloat() * 2f - 1f) * 0.17f
        val cx = (from.x + to.x) / 2f - my * bend
        val cy = (from.y + to.y) / 2f + mx * bend / DEPTH
        return List(steps) { i ->
            val u = (i + 1f) / steps
            val t = u * 0.45f + (u * u * (3f - 2f * u)) * 0.55f
            if (i == steps - 1) to else {
                val a = (1 - t) * (1 - t)
                val b = 2 * (1 - t) * t
                val c = t * t
                Offset(a * from.x + b * cx + c * to.x, (a * from.y + b * cy + c * to.y).coerceIn(0f, 1f))
            }
        }
    }

    fun entrance(random: Random, home: Int): Offset = Offset(if (random.nextBoolean()) home - 0.18f else home + 1.18f, 0.2f + random.nextFloat() * 0.6f)

    fun exit(from: Offset, home: Int): Offset = Offset(if (from.x < home + 0.5f) home - 0.25f else home + 1.25f, from.y)

    fun tapReaction(random: Random, recentTaps: Int): TapReaction {
        if (recentTaps >= 3) return TapReaction.FLUSTERED
        val roll = random.nextInt(100)
        return if (roll < 45) TapReaction.HOP else if (roll < 75) TapReaction.HAPPY else TapReaction.TURN
    }

    fun bedtime(id: String): Float = 0.4f + spread(id, 0x7654321) * 0.25f

    fun asleep(mon: OwnedPokemon, darkness: Float): Boolean = TownQuirks.of(mon) == Quirk.ASLEEP || (sleepsAtNight(mon) && darkness >= bedtime(mon.id))

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

    fun rotation(epochDay: Long): Int = (epochDay % 1_000_000L).toInt()
}
