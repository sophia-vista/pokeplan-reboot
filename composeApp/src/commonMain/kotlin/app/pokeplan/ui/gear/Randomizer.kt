package app.pokeplan.ui.gear

import kotlin.random.Random

data class DiceRoll(val spec: String, val sides: Int, val rolls: List<Int>, val modifier: Int) {
    val total: Int get() = rolls.sum() + modifier

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

object Randomizer {
    const val MAX_DICE = 20
    const val MAX_SIDES = 1000
    const val MAX_MODIFIER = 1000
    const val MAX_WEIGHT = 10

    fun weightOf(option: String, weights: Map<String, Int>): Int = (weights[option] ?: 1).coerceIn(1, MAX_WEIGHT)

    fun pick(options: List<String>, weights: Map<String, Int>, random: Random, avoid: String? = null): String? {
        val pool = if (avoid != null && options.size > 1) options.filter { it != avoid }.ifEmpty { options } else options
        if (pool.isEmpty()) return null
        val total = pool.sumOf { weightOf(it, weights) }
        var roll = random.nextInt(total)
        for (o in pool) { roll -= weightOf(o, weights); if (roll < 0) return o }
        return pool.last()
    }

    fun pickMany(options: List<String>, weights: Map<String, Int>, count: Int, random: Random): List<String> {
        val left = options.toMutableList()
        val out = mutableListOf<String>()
        repeat(count.coerceAtMost(options.size)) { pick(left, weights, random)?.let { out += it; left.remove(it) } }
        return out
    }

    fun shuffle(options: List<String>, random: Random): List<String> = options.shuffled(random)

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
