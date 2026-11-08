package app.pokeplan.ui

import app.pokeplan.world.story.Expression

/**
 * A cry with a little character (and a volume: the games give a cry a level by condition and distance as well as a feeling, see [Condition] and [Distance]) (what the games do: the first two generations built every cry from a shared base sound with a pitch and a length of
 * its own, and later ones still bend a cry for a moment: a low, slow one when a Pokémon is weak, a higher one when it is excited). Here a voice is a pitch
 * and a speed on the recorded cry, both close to 1 so it is still the real cry: **who** it is (a stable detune from the Pokémon's id, so two of the same
 * species are not identical) and **how it feels** (the expression of the line it is said on, or the moment: pleased, startled, angry...).
 *
 * Pitch and speed are two numbers because the player can change them apart (Android's `PlaybackParams`); a player that cannot plays the cry as recorded.
 */
object CryVoice {
    /**
     * Pitch and speed, 1 each for the cry as recorded, and a [gain] relative to a cry at its default level (1: default, [Condition.LOUD] 1.2). The player has no
     * headroom above full volume, so a gain is shown against [LOUD]: see [AppModel.cry].
     */
    data class Voice(val pitch: Float = 1f, val speed: Float = 1f, val gain: Float = 1f) {
        operator fun times(o: Voice) = Voice((pitch * o.pitch).coerceIn(MIN, MAX), (speed * o.speed).coerceIn(MIN, MAX), (gain * o.gain).coerceIn(MIN_GAIN, LOUD))
    }

    /** How loud a cry is said, as the games' sound design gives it (a report of Pokémon's cry design lists these levels): default 100%, loud 120%, low 80%, injured 95% and seriously injured 85%. */
    enum class Condition(val gain: Float) { DEFAULT(1f), LOUD(1.2f), LOW(0.8f), INJURED(0.95f), SERIOUSLY_INJURED(0.85f) }

    /** How far away the speaker is: Legends: Arceus needed a near, a medium and a far version of each cry. Here the far ones are only quieter. *(tune)* */
    enum class Distance(val gain: Float) { NEAR(1f), MEDIUM(0.7f), FAR(0.45f) }

    /** The loudest a gain can be, and the quietest. */
    const val LOUD = 1.2f
    const val MIN_GAIN = 0.2f

    val NEUTRAL = Voice()

    /** How far an individual can sit from the recorded cry, each way. *(tune)* */
    const val INDIVIDUAL = 0.04f

    /** The most any voice moves a cry, so it never stops sounding like itself. */
    const val MIN = 0.85f
    const val MAX = 1.15f

    /** The detune of the Pokémon (or story character) with [id]: the same every time, between -[INDIVIDUAL] and +[INDIVIDUAL] on the pitch and half of that on the speed. */
    fun individual(id: String): Voice {
        var h = 17
        for (c in id) h = h * 31 + c.code
        val a = ((h and 0xFFFF) % 2001 - 1000) / 1000f   // -1 .. 1
        val b = (((h ushr 16) and 0xFFFF) % 2001 - 1000) / 1000f
        return Voice(1f + a * INDIVIDUAL, 1f + b * INDIVIDUAL / 2)
    }

    /** What a feeling does to a cry: pitch and speed, and how loud it is said (excited and angry loud, low spirits low). *(tune)* */
    fun mood(expression: Expression?): Voice = when (expression) {
        Expression.JOYOUS -> Voice(1.07f, 1.06f, Condition.LOUD.gain)
        Expression.HAPPY -> Voice(1.04f, 1.02f)
        Expression.INSPIRED -> Voice(1.05f, 1.0f)
        Expression.DETERMINED -> Voice(0.98f, 1.0f)
        Expression.SURPRISED -> Voice(1.09f, 1.1f, Condition.LOUD.gain)
        Expression.WORRIED -> Voice(0.97f, 0.93f, Condition.LOW.gain)
        Expression.SAD, Expression.TEARY -> Voice(0.92f, 0.88f, Condition.LOW.gain)
        Expression.ANGRY -> Voice(0.93f, 1.07f, Condition.LOUD.gain)
        Expression.NORMAL, null -> NEUTRAL
    }

    /** The voice of [id] feeling [expression], [distance] away, in [condition]. */
    fun of(id: String, expression: Expression? = null, distance: Distance = Distance.NEAR, condition: Condition = Condition.DEFAULT): Voice =
        individual(id) * mood(expression) * Voice(gain = distance.gain * condition.gain)
}
