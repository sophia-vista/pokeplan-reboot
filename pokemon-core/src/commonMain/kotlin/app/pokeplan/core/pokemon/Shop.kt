package app.pokeplan.core.pokemon

object Shop {
    const val COINS_PER_RARE_CANDY = 5

    const val SUPER_CANDY_PRICE = 100

    const val BERRY_PRICE = 20

    val stock: List<Pair<String, Int>> by lazy {
        listOf(Items.RARE_CANDY to COINS_PER_RARE_CANDY, Items.SUPER_CANDY to SUPER_CANDY_PRICE) +
            Items.flavors.values.map { Items.berry(it) to BERRY_PRICE }
    }

    fun price(itemId: String): Int? = stock.firstOrNull { it.first == itemId }?.second
}
