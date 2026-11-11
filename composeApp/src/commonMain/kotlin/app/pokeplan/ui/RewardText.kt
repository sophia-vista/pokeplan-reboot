package app.pokeplan.ui

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.reward.CandyType
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.Reward

/** True for a reward-log source that is World's (a recruit or a legendary there is not an egg). */
fun isWorldSource(source: String): Boolean = source.startsWith("World")

/**
 * How a new Pokémon came to the player, which decides the scene and the words (owner, 8 November 2026: not everything is an egg). Only Plan's
 * eggs, a visitor's gift and anything unrecognised hatch from an egg; Care's friends wander over, Heal's are found on a route and Mint's
 * are catalogued by Ledger Research, Home's settle into the habitat and Link's come by postcard.
 */
enum class Acquisition { EGG, ARRIVAL, FIELD, LEDGER, HABITAT, POSTCARD }

/** The acquisition an origin means. */
fun acquisitionOf(how: app.pokeplan.app.Origin.How?): Acquisition = when (how) {
    app.pokeplan.app.Origin.How.CARE_EGG -> Acquisition.ARRIVAL
    app.pokeplan.app.Origin.How.FIELD_EGG -> Acquisition.FIELD
    app.pokeplan.app.Origin.How.LEDGER_EGG -> Acquisition.LEDGER
    app.pokeplan.app.Origin.How.HABITAT_EGG -> Acquisition.HABITAT
    app.pokeplan.app.Origin.How.POSTCARD_EGG -> Acquisition.POSTCARD
    else -> Acquisition.EGG
}

/** The same, read from a reward-log source (the log keeps no origin beside it): "Care: a routine" arrives, "Care: made a friend" is an egg. */
fun acquisitionOfSource(source: String): Acquisition = when {
    source.startsWith(app.pokeplan.app.GameStore.HEAL_LOG_PREFIX) -> Acquisition.FIELD
    source.startsWith(app.pokeplan.app.GameStore.MINT_LOG_PREFIX) -> Acquisition.LEDGER
    source.startsWith(app.pokeplan.app.GameStore.HOME_LOG_PREFIX) -> Acquisition.HABITAT
    source.startsWith(app.pokeplan.app.GameStore.LINK_LOG_PREFIX) -> Acquisition.POSTCARD
    source.startsWith(app.pokeplan.app.GameStore.CARE_LOG_PREFIX) && source != app.pokeplan.app.GameStore.CARE_FRIEND_SOURCE -> Acquisition.ARRIVAL
    else -> Acquisition.EGG
}

/** One short line per reward, in the voice of the old games' text boxes. [source] is the reward log entry's, so what World gives is not said to hatch. */
fun Reward.describe(source: String = ""): String = when (this) {
    is Reward.Hatched -> {
        val name = Dex.species(pokemon.speciesId).name
        when {
            isWorldSource(source) -> if (pokemon.isShiny) "A shiny $name joined you!" else "$name joined you!"
            acquisitionOfSource(source) == Acquisition.ARRIVAL -> if (pokemon.isShiny) "A shiny $name wandered over!" else "A new friend wandered over: $name!"
            acquisitionOfSource(source) == Acquisition.FIELD -> "Field Research found ${if (pokemon.isShiny) "a shiny " else ""}$name!"
            acquisitionOfSource(source) == Acquisition.HABITAT -> "Habitat Discovery found ${if (pokemon.isShiny) "a shiny " else ""}$name!"
            acquisitionOfSource(source) == Acquisition.POSTCARD -> "Postcard Trail delivered ${if (pokemon.isShiny) "a shiny " else ""}$name!"
            acquisitionOfSource(source) == Acquisition.LEDGER -> "Ledger Research catalogued ${if (pokemon.isShiny) "a shiny " else ""}$name!"
            pokemon.isShiny -> "A shiny $name hatched!"
            fromTask -> "A Task Egg hatched: $name!"
            else -> "Your egg hatched: $name!"
        }
    }
    is Reward.Candies -> "$amount ${if (type == CandyType.SUPER) "Super" else "Exp."} ${if (amount == 1) "Candy" else "Candies"}"
    is Reward.Coins -> "$amount Poké"
    is Reward.Poke -> "$amount Poké"
    is Reward.ItemDrop -> "${Items.name(itemId)}" + if (amount > 1) " x$amount" else ""
    is Reward.Evs -> "Training: +$points"
    is Reward.Friendship -> "Friendship +$amount"
}
