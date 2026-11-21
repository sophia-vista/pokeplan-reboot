package app.pokeplan.ui

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.reward.CandyType
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.reward.Reward

fun isWorldSource(source: String): Boolean = source.startsWith("World")

enum class Acquisition { EGG, ARRIVAL, FIELD, LEDGER, HABITAT, POSTCARD, SAFARI }

fun acquisitionOf(how: app.pokeplan.app.Origin.How?): Acquisition = when (how) {
    app.pokeplan.app.Origin.How.CARE_EGG -> Acquisition.ARRIVAL
    app.pokeplan.app.Origin.How.FIELD_EGG -> Acquisition.FIELD
    app.pokeplan.app.Origin.How.LEDGER_EGG -> Acquisition.LEDGER
    app.pokeplan.app.Origin.How.HABITAT_EGG -> Acquisition.HABITAT
    app.pokeplan.app.Origin.How.POSTCARD_EGG -> Acquisition.POSTCARD
    app.pokeplan.app.Origin.How.SAFARI -> Acquisition.SAFARI
    else -> Acquisition.EGG
}

fun acquisitionOfSource(source: String): Acquisition = when {
    source.startsWith(app.pokeplan.app.GameStore.HEAL_LOG_PREFIX) -> Acquisition.FIELD
    source.startsWith(app.pokeplan.app.GameStore.MINT_LOG_PREFIX) -> Acquisition.LEDGER
    source.startsWith(app.pokeplan.app.GameStore.BASE_LOG_PREFIX) || source.startsWith(app.pokeplan.app.GameStore.OLD_BASE_LOG_PREFIX) -> Acquisition.HABITAT
    source == app.pokeplan.app.GameStore.SAFARI_SOURCE -> Acquisition.SAFARI
    source.startsWith(app.pokeplan.app.GameStore.LINK_LOG_PREFIX) -> Acquisition.POSTCARD
    source.startsWith(app.pokeplan.app.GameStore.CARE_LOG_PREFIX) && source != app.pokeplan.app.GameStore.CARE_FRIEND_SOURCE -> Acquisition.ARRIVAL
    else -> Acquisition.EGG
}

fun Reward.describe(source: String = ""): String = when (this) {
    is Reward.Hatched -> {
        val name = Dex.species(pokemon.speciesId).name
        when {
            isWorldSource(source) -> if (pokemon.isShiny) "A shiny $name joined you!" else "$name joined you!"
            acquisitionOfSource(source) == Acquisition.ARRIVAL -> if (pokemon.isShiny) "A shiny $name wandered over!" else "A new friend wandered over: $name!"
            acquisitionOfSource(source) == Acquisition.FIELD -> "Field Research found ${if (pokemon.isShiny) "a shiny " else ""}$name!"
            acquisitionOfSource(source) == Acquisition.HABITAT -> "Habitat Discovery found ${if (pokemon.isShiny) "a shiny " else ""}$name!"
            acquisitionOfSource(source) == Acquisition.POSTCARD -> "Postcard Trail delivered ${if (pokemon.isShiny) "a shiny " else ""}$name!"
            acquisitionOfSource(source) == Acquisition.SAFARI -> "A Friend Safari had ${if (pokemon.isShiny) "a shiny " else ""}$name!"
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
    is Reward.Exp -> "$name +$points exp"
    is Reward.Decoration -> "A new decoration: ${app.pokeplan.base.Decorations.get(decorationId)?.name ?: "for your room"}"
}
