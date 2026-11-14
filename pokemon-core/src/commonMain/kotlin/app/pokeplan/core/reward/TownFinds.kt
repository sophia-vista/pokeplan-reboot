package app.pokeplan.core.reward

import app.pokeplan.core.pokemon.Items
import kotlin.random.Random

/**
 * What lies on the launcher town's floor each day (owner, 14 November 2026; REP-052, LAUNCHER.md section 6.4): [PER_DAY] finds, the same
 * for everyone on that date and whatever the floor's layout (a new layout moves them, it never makes more). A tap puts one in the Bag
 * (or adds its Poké); a collected find is gone until the next day. Common Bag things only: berries, vitamins and a little Poké. *(tune)*
 */
object TownFinds {
    const val PER_DAY = 3

    /** The id a Poké find carries instead of an item. */
    const val POKE = "poke"

    /** A Poké find pays between these. */
    const val POKE_MIN = 10
    const val POKE_MAX = 30

    /** One find: an item id (or [POKE]) and how many. */
    data class Find(val itemId: String, val amount: Int) {
        val isPoke: Boolean get() = itemId == POKE
    }

    /** Relative weights: each berry 4, each vitamin 2, Poké 8. */
    val weights: Map<String, Double> by lazy {
        buildMap {
            Items.flavors.values.forEach { put(Items.berry(it), 4.0) }
            Items.vitamins.keys.forEach { put(it, 2.0) }
            put(POKE, 8.0)
        }
    }

    /** The [PER_DAY] finds of [epochDay]. */
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
