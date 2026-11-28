package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.FormRoutes
import app.pokeplan.core.pokemon.HeldItems
import app.pokeplan.core.pokemon.Items
import kotlin.random.Random

data class ShopOffer(val itemId: String, val price: Int, val count: Int = 1)

object KecleonShop {
    const val SHELF_SIZE = 5

    const val STONE_PRICE = 400
    const val LINK_CORD_PRICE = 500
    const val EVERSTONE_PRICE = 300
    const val MEGA_STONE_PRICE = 2_000
    const val FORM_ITEM_PRICE = 1_500

    val fixed: List<ShopOffer> by lazy {
        Items.evolutionItems.map { ShopOffer(it, STONE_PRICE) } + listOf(
            ShopOffer(Items.LINK_CORD, LINK_CORD_PRICE),
            ShopOffer(Items.EVERSTONE, EVERSTONE_PRICE),
            ShopOffer(Items.GIMMIGHOUL_COIN, 150, count = 50),
        )
    }

    val catalogue: List<ShopOffer> by lazy {
        Items.tradeItems.map { ShopOffer(it, 500) } +
            Items.heldEvolutionItems.map { ShopOffer(it, 500) } +
            Items.vitamins.keys.map { ShopOffer(it, 100) } +
            HeldItems.perks.keys.map { ShopOffer(it, if (it == "shiny-charm") 1_500 else 500) } +
            PokemonType.entries.map { ShopOffer(Items.teraShard(it), 1_000) } +
            listOf(
                ShopOffer(Items.NATURE_MINT, 400), ShopOffer(Items.ABILITY_CAPSULE, 600), ShopOffer(Items.BOTTLE_CAP, 800),
                ShopOffer(Items.ABILITY_PATCH, 2_000),
            )
    }

    /** Every Mega form's stone, always sold on its own shelf (the catalogue would take months to cycle). */
    val megaStones: List<ShopOffer> by lazy { Items.megaStones.map { ShopOffer(it, MEGA_STONE_PRICE) } }

    /** Every form item (Gracidea, Reveal Glass...), always sold on its own shelf. */
    val formItems: List<ShopOffer> by lazy { FormRoutes.formItems.map { ShopOffer(it.id, FORM_ITEM_PRICE) } }

    fun shelf(day: Long): List<ShopOffer> = catalogue.shuffled(Random(day * 2_654_435_761L + 17)).take(SHELF_SIZE)

    fun onSale(day: Long): List<ShopOffer> = fixed + megaStones + formItems + shelf(day)

    fun offer(day: Long, itemId: String): ShopOffer? = onSale(day).firstOrNull { it.itemId == itemId }
}
