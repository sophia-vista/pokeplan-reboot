package app.pokeplan.ui

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.reward.CandyType
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.Reward

/** True for a reward-log source that is World's (a recruit or a legendary there is not an egg). */
fun isWorldSource(source: String): Boolean = source.startsWith("World")

/** One short line per reward, in the voice of the old games' text boxes. [source] is the reward log entry's, so what World gives is not said to hatch. */
fun Reward.describe(source: String = ""): String = when (this) {
    is Reward.Hatched -> {
        val name = Dex.species(pokemon.speciesId).name
        when {
            isWorldSource(source) -> if (pokemon.isShiny) "✨ A shiny $name joined you! ✨" else "$name joined you!"
            pokemon.isShiny -> "✨ A shiny $name hatched! ✨"
            fromTask -> "A Task Egg hatched: $name!"
            else -> "Your egg hatched: $name!"
        }
    }
    is Reward.Candies -> "$amount ${if (type == CandyType.SUPER) "Super" else "Rare"} ${if (amount == 1) "Candy" else "Candies"}"
    is Reward.Coins -> "$amount coins"
    is Reward.Poke -> "$amount Poké"
    is Reward.ItemDrop -> "${Items.name(itemId)}" + if (amount > 1) " x$amount" else ""
    is Reward.Evs -> "Training: +$points"
    is Reward.Friendship -> "Friendship +$amount"
}
