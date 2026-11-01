package app.pokeplan.ui.gear

import kotlin.random.Random

/** One roll of dice like `2d6+3`: the dice thrown and the modifier. */
data class DiceRoll(val spec: String, val sides: Int, val rolls: List<Int>, val modifier: Int) {
    val total: Int get() = rolls.sum() + modifier

    /** `2d6+3: 4 + 5 + 3 = 12`, or just `d20: 17` for one die and no modifier. */
    val text: String get() = buildString {
        append(spec).append(": ")
        if (rolls.size == 1 && modifier == 0) append(total)
        else {
            append(rolls.joinToString(" + "))
            if (modifier > 0) append(" + ").append(modifier) else if (modifier < 0) append(" - ").append(-modifier)
            append(" = ").append(total)
        }
    }
}

/**
 * The Picker's and the dice's choosing, with the random generator passed in so it is testable. This is everyday randomness for choosing a dinner or
 * a first player: **it is not secure randomness and is not for gambling, draws with a prize or anything that must be provably fair.**
 */
object Randomizer {
    const val MAX_DICE = 20
    const val MAX_SIDES = 1000
    const val MAX_MODIFIER = 1000
    const val MAX_WEIGHT = 10

    fun weightOf(option: String, weights: Map<String, Int>): Int = (weights[option] ?: 1).coerceIn(1, MAX_WEIGHT)

    /** One of [options], chosen by weight; [avoid] (the last pick) is left out when there is any other choice. Null when there are no options. */
    fun pick(options: List<String>, weights: Map<String, Int>, random: Random, avoid: String? = null): String? {
        val pool = if (avoid != null && options.size > 1) options.filter { it != avoid }.ifEmpty { options } else options
        if (pool.isEmpty()) return null
        val total = pool.sumOf { weightOf(it, weights) }
        var roll = random.nextInt(total)
        for (o in pool) { roll -= weightOf(o, weights); if (roll < 0) return o }
        return pool.last()
    }

    /** [count] different options, each chosen by weight among what is left, in the order drawn (fewer when there are not that many). */
    fun pickMany(options: List<String>, weights: Map<String, Int>, count: Int, random: Random): List<String> {
        val left = options.toMutableList()
        val out = mutableListOf<String>()
        repeat(count.coerceAtMost(options.size)) { pick(left, weights, random)?.let { out += it; left.remove(it) } }
        return out
    }

    fun shuffle(options: List<String>, random: Random): List<String> = options.shuffled(random)

    /** `2d6+3`, `d20`, `3D8-1` as (dice, sides, modifier); null when it is not that or outside 1 to 20 dice of 2 to 1000 sides and a modifier up to 1000. */
    fun parseDice(spec: String): Triple<Int, Int, Int>? {
        val m = Regex("(\\d{0,2})[dD](\\d{1,4})(?:([+-])(\\d{1,4}))?").matchEntire(spec.trim().replace(" ", "")) ?: return null
        val count = m.groupValues[1].ifEmpty { "1" }.toInt()
        val sides = m.groupValues[2].toInt()
        val modifier = (m.groupValues[4].ifEmpty { "0" }.toInt()) * if (m.groupValues[3] == "-") -1 else 1
        if (count !in 1..MAX_DICE || sides !in 2..MAX_SIDES || modifier !in -MAX_MODIFIER..MAX_MODIFIER) return null
        return Triple(count, sides, modifier)
    }

    fun roll(spec: String, random: Random): DiceRoll? {
        val (count, sides, modifier) = parseDice(spec) ?: return null
        val clean = (if (count == 1) "" else count.toString()) + "d" + sides + (if (modifier > 0) "+$modifier" else if (modifier < 0) "$modifier" else "")
        return DiceRoll(clean, sides, List(count) { random.nextInt(1, sides + 1) }, modifier)
    }
}
