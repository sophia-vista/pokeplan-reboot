package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.HatchRules
import app.pokeplan.core.dex.Rarity

enum class TransferBlock(val reason: String) {
    BUDDY("This is your buddy."),
    LAST_PARTY_MEMBER("This is your last party member."),
    SPECIAL_SPECIES("Legendary and mythical Pokémon cannot be transferred."),
}

data class TransferQuote(val rareCandies: Int, val coins: Int, val returnedItem: String?, val warnings: List<String>)

object Transfer {
    val candiesByRarity: Map<Rarity, Int> = mapOf(
        Rarity.COMMON to 8, Rarity.UNCOMMON to 12, Rarity.RARE to 22, Rarity.SUPER_RARE to 60,
    )

    private const val COIN_FACTOR = 2
    private const val REFUND_DIVISOR = 5

    fun block(p: OwnedPokemon, isBuddy: Boolean, partyCount: Int): TransferBlock? = when {
        p.species.isSpecial -> TransferBlock.SPECIAL_SPECIES
        isBuddy -> TransferBlock.BUDDY
        p.inParty && partyCount <= 1 -> TransferBlock.LAST_PARTY_MEMBER
        else -> null
    }

    fun quote(p: OwnedPokemon): TransferQuote {
        val rarity = checkNotNull(HatchRules.rarity(p.speciesId)) { "${p.nickname} cannot be transferred" }
        val candies = candiesByRarity.getValue(rarity) + p.totalCandies / REFUND_DIVISOR
        val warnings = buildList {
            if (p.isShiny) add("${p.nickname} is shiny.")
            if (p.ivs.values.all { it == StatCalculator.MAX_IV }) add("${p.nickname} has perfect IVs.")
            if (p.evs.total >= 400) add("${p.nickname} is well trained.")
            if (p.level >= 50) add("${p.nickname} is level ${p.level}.")
        }
        return TransferQuote(candies, candiesByRarity.getValue(rarity) * COIN_FACTOR, p.heldItem, warnings)
    }
}
