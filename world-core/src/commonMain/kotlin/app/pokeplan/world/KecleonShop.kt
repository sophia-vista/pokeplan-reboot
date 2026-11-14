package app.pokeplan.world

import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.HeldItems
import app.pokeplan.core.pokemon.Items
import kotlin.random.Random

/** One thing on the Kecleon Shop's shelves: [count] of [itemId] for [price] Poké. */
data class ShopOffer(val itemId: String, val price: Int, val count: Int = 1)

/**
 * The Kecleon Shop's items (owner, 14 November 2026: a fixed stock and a daily shelf; docs/pokelife/FEATURES/POKEMON_ECOSYSTEM.md 20.2.3).
 * The [fixed] stock is always sold: every evolution stone, the Link Cord, the Everstone and Gimmighoul Coins, so an evolution never waits on
 * luck. The [shelf] holds [SHELF_SIZE] more, chosen from the rest of the catalogue for each day and the same all day; nothing on it is sold
 * only there, and there is no countdown. Exp. Candy keeps its own climbing price ([Shop]). Prices are Poké. *(tune)*
 */
object KecleonShop {
    const val SHELF_SIZE = 5

    const val STONE_PRICE = 2_000
    const val LINK_CORD_PRICE = 3_000
    const val EVERSTONE_PRICE = 1_500

    /** Always on sale. */
    val fixed: List<ShopOffer> by lazy {
        Items.evolutionItems.map { ShopOffer(it, STONE_PRICE) } + listOf(
            ShopOffer(Items.LINK_CORD, LINK_CORD_PRICE),
            ShopOffer(Items.EVERSTONE, EVERSTONE_PRICE),
            ShopOffer(Items.GIMMIGHOUL_COIN, 600, count = 50),
        )
    }

    /** What the daily shelf is chosen from. */
    val catalogue: List<ShopOffer> by lazy {
        Items.tradeItems.map { ShopOffer(it, 2_500) } +
            Items.heldEvolutionItems.map { ShopOffer(it, 2_500) } +
            Items.vitamins.keys.map { ShopOffer(it, 1_000) } +
            HeldItems.perks.keys.map { ShopOffer(it, if (it == "shiny-charm") 12_000 else 4_000) } +
            PokemonType.entries.map { ShopOffer(Items.teraShard(it), 10_000) } +
            listOf(
                ShopOffer(Items.NATURE_MINT, 5_000), ShopOffer(Items.ABILITY_CAPSULE, 6_000), ShopOffer(Items.BOTTLE_CAP, 8_000),
                ShopOffer(Items.ABILITY_PATCH, 20_000),
            )
    }

    /** The shelf of [day] (the player's local day number): the same five all day, different the next. */
    fun shelf(day: Long): List<ShopOffer> = catalogue.shuffled(Random(day * 2_654_435_761L + 17)).take(SHELF_SIZE)

    /** Everything on sale on [day]: the fixed stock, then the day's shelf. */
    fun onSale(day: Long): List<ShopOffer> = fixed + shelf(day)

    /** The offer for [itemId] on [day], or null when it is not on sale that day. */
    fun offer(day: Long, itemId: String): ShopOffer? = onSale(day).firstOrNull { it.itemId == itemId }
}
