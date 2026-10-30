package app.pokeplan.ui

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.reward.CandyType
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.Reward

/** One short line per reward, in the voice of the old games' text boxes. */
fun Reward.describe(): String = when (this) {
    is Reward.Hatched -> {
        val name = Dex.species(pokemon.speciesId).name
        when {
            pokemon.isShiny -> "✨ A shiny $name hatched! ✨"
            fromTask -> "A Task Egg hatched: $name!"
            else -> "Your egg hatched: $name!"
        }
    }
    is Reward.Candies -> "$amount ${if (type == CandyType.SUPER) "Super" else "Rare"} ${if (amount == 1) "Candy" else "Candies"}"
    is Reward.Coins -> "$amount coins"
    is Reward.ItemDrop -> "${Items.name(itemId)}" + if (amount > 1) " x$amount" else ""
    is Reward.Evs -> "Training: +$points"
    is Reward.Friendship -> "Friendship +$amount"
}
