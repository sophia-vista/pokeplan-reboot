package app.pokeplan.ui

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.model.CandyType
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.Reward

/** One short line per reward, in the voice of the old games' text boxes. */
fun Reward.describe(): String = when (this) {
    is Reward.Hatched -> {
        val name = Dex.species(pokemon.speciesId).name
        val shiny = if (pokemon.isShiny) " It is shiny!" else ""
        (if (fromTask) "A Task Egg hatched: $name!" else "Your egg hatched: $name!") + shiny
    }
    is Reward.Candies -> "$amount ${if (type == CandyType.SUPER) "Super" else "Rare"} Candy" + if (amount == 1) "" else "s"
    is Reward.Coins -> "$amount coins"
    is Reward.ItemDrop -> "${Items.name(itemId)}" + if (amount > 1) " x$amount" else ""
    is Reward.Evs -> "Training: +$points"
}
