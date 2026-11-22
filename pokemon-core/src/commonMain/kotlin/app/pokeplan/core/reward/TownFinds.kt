package app.pokeplan.core.reward

import app.pokeplan.core.pokemon.Items
import kotlin.random.Random

object TownFinds {
    const val PER_DAY = 3

    const val POKE = "poke"

    const val POKE_MIN = 10
    const val POKE_MAX = 30

    data class Find(val itemId: String, val amount: Int) {
        val isPoke: Boolean get() = itemId == POKE
    }

    val weights: Map<String, Double> by lazy {
        buildMap {
            Items.flavors.values.forEach { put(Items.berry(it), 4.0) }
            Items.vitamins.keys.forEach { put(it, 2.0) }
            put(POKE, 8.0)
        }
    }

    fun forDay(epochDay: Long): List<Find> {
        val rng = Random(epochDay * 1_000_003L + SALT)
        return List(PER_DAY) {
            var pick = rng.nextDouble() * weights.values.sum()
            var id = weights.keys.last()
            for ((k, w) in weights) { pick -= w; if (pick < 0) { id = k; break } }
            if (id == POKE) Find(POKE, POKE_MIN + rng.nextInt(POKE_MAX - POKE_MIN + 1)) else Find(id, 1)
        }
    }

    private const val SALT = 0x70776EL
}
