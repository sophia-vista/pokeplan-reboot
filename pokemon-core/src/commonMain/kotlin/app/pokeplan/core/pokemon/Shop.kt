package app.pokeplan.core.pokemon

/**
 * The Poké Mart: the everyday shop of the Pokémon tab, where Poké buys what a single Pokémon grows with, cheaply (owner, 14 November 2026: "anything
 * for individual Pokémon growth should be generous"; docs/economy.md, section 2). The Kecleon Shop in World sells the specialty stock. *(tune)*
 */
object Shop {
    /** Poké for one Exp. Candy (a Rare Candy). *(tune)* */
    const val COINS_PER_RARE_CANDY = 5

    /** Poké for one Super Candy, so evolving many Pokémon does not wait on drops. *(tune)* */
    const val SUPER_CANDY_PRICE = 100

    /** Poké for one berry of a chosen flavour. *(tune)* */
    const val BERRY_PRICE = 20

    /** What the Mart sells and for how much each, in shelf order: the two candies, then a berry of each flavour. */
    val stock: List<Pair<String, Int>> by lazy {
        listOf(Items.RARE_CANDY to COINS_PER_RARE_CANDY, Items.SUPER_CANDY to SUPER_CANDY_PRICE) +
            Items.flavors.values.map { Items.berry(it) to BERRY_PRICE }
    }

    /** The Mart's price for one [itemId], or null when the Mart does not sell it. */
    fun price(itemId: String): Int? = stock.firstOrNull { it.first == itemId }?.second
}
