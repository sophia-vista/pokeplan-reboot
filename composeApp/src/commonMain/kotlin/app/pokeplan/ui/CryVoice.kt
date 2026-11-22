package app.pokeplan.ui

import app.pokeplan.world.story.Expression

object CryVoice {
    data class Voice(val pitch: Float = 1f, val speed: Float = 1f, val gain: Float = 1f) {
        operator fun times(o: Voice) = Voice((pitch * o.pitch).coerceIn(MIN, MAX), (speed * o.speed).coerceIn(MIN, MAX), (gain * o.gain).coerceIn(MIN_GAIN, LOUD))
    }

    enum class Condition(val gain: Float) { DEFAULT(1f), LOUD(1.2f), LOW(0.8f), INJURED(0.95f), SERIOUSLY_INJURED(0.85f) }

    enum class Distance(val gain: Float) { NEAR(1f), MEDIUM(0.7f), FAR(0.45f) }

    const val LOUD = 1.2f
    const val MIN_GAIN = 0.2f

    val NEUTRAL = Voice()

    const val INDIVIDUAL = 0.04f

    const val MIN = 0.85f
    const val MAX = 1.15f

    fun individual(id: String): Voice {
        var h = 17
        for (c in id) h = h * 31 + c.code
        val a = ((h and 0xFFFF) % 2001 - 1000) / 1000f
        val b = (((h ushr 16) and 0xFFFF) % 2001 - 1000) / 1000f
        return Voice(1f + a * INDIVIDUAL, 1f + b * INDIVIDUAL / 2)
    }

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

    fun of(id: String, expression: Expression? = null, distance: Distance = Distance.NEAR, condition: Condition = Condition.DEFAULT): Voice =
        individual(id) * mood(expression) * Voice(gain = distance.gain * condition.gain)
}
