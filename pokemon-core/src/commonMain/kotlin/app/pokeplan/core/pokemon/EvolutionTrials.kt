package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Evolution
import app.pokeplan.core.dex.EvolutionKind
import app.pokeplan.core.dex.PokemonType

enum class DeviceAction(val label: String, val howTo: String) {
    UPSIDE_DOWN("Turn the phone upside down", "Hold the phone upside down"),

    SPIN("Spin around", "Turn around once, phone held flat"),

    STEPS("Walk ${EvolutionTrials.STEPS} steps", "Walk ${EvolutionTrials.STEPS} steps with the phone"),
}

sealed interface TrialNeed {
    data class Move(val move: String) : TrialNeed

    data class MoveType(val type: PokemonType) : TrialNeed

    data object Friendship : TrialNeed

    data class Hold(val item: String) : TrialNeed

    data class PartySpecies(val speciesId: Int) : TrialNeed

    data class PartyType(val type: PokemonType) : TrialNeed

    data class Stats(val sign: Int) : TrialNeed

    data class Natures(val natures: Set<Nature>) : TrialNeed

    data class Spend(val item: String, val count: Int) : TrialNeed

    data object Linked : TrialNeed

    data class Device(val action: DeviceAction) : TrialNeed

    data class World(val condition: String) : TrialNeed
}

object EvolutionTrials {
    const val STEPS = 1_000
    const val EVERSTONE = "everstone"
    const val GIMMIGHOUL_COIN = "gimmighoul-coin"
    const val MELTAN_CANDIES = 400
    const val GIMMIGHOUL_COINS = 999

    val AMPED: Set<Nature> = setOf(
        Nature.HARDY, Nature.BRAVE, Nature.ADAMANT, Nature.NAUGHTY, Nature.DOCILE, Nature.IMPISH, Nature.LAX,
        Nature.HASTY, Nature.JOLLY, Nature.NAIVE, Nature.RASH, Nature.SASSY, Nature.QUIRKY,
    )

    private val worldConditions = listOf("rain", "region=", "trigger=shed", "trigger=three-critical-hits", "trigger=take-damage", "trigger=agile-style-move",
        "trigger=recoil-damage", "trigger=strong-style-move", "trigger=use-move", "trigger=three-defeated-bisharp")

    fun needs(e: Evolution): List<TrialNeed> {
        if (e.kind != EvolutionKind.TRIAL) return emptyList()
        val c = e.conditions
        val out = mutableListOf<TrialNeed>()
        e.heldItem?.let { out += TrialNeed.Hold(it) }
        for (s in c) {
            val v = s.substringAfter('=', "")
            when {
                s.startsWith("move=") -> out += TrialNeed.Move(v)
                s.startsWith("move-type=") -> PokemonType.entries.firstOrNull { it.name.equals(v, true) }?.let { out += TrialNeed.MoveType(it) }
                s.startsWith("affection") -> out += TrialNeed.Friendship
                s.startsWith("party-species=") -> v.toIntOrNull()?.let { out += TrialNeed.PartySpecies(it) }
                s.startsWith("party-type=") -> PokemonType.entries.firstOrNull { it.name.equals(v, true) }?.let { out += TrialNeed.PartyType(it) }
                s == "physical-stats" -> out += TrialNeed.Stats(1)
                s.startsWith("physical-stats=") -> out += TrialNeed.Stats(v.toIntOrNull() ?: 0)
                s.startsWith("nature=") -> out += TrialNeed.Natures(if (e.evolvedForm?.endsWith("low-key") == true) Nature.entries.toSet() - AMPED else AMPED)
                s == "upside-down" -> out += TrialNeed.Device(DeviceAction.UPSIDE_DOWN)
                s == "trigger=spin" -> out += TrialNeed.Device(DeviceAction.SPIN)
                s.startsWith("steps=") -> out += TrialNeed.Device(DeviceAction.STEPS)
                s == "multiplayer" -> out += TrialNeed.Linked
                s == "trigger=meltan-candies" -> out += TrialNeed.Spend(Items.RARE_CANDY, MELTAN_CANDIES)
                s == "trigger=gimmighoul-coins" -> out += TrialNeed.Spend(GIMMIGHOUL_COIN, GIMMIGHOUL_COINS)
                worldConditions.any { s.startsWith(it) } -> if (out.none { it is TrialNeed.World }) out += TrialNeed.World(s)
            }
        }
        return out
    }

    fun device(e: Evolution): DeviceAction? = needs(e).filterIsInstance<TrialNeed.Device>().firstOrNull()?.action

    fun needsWorld(e: Evolution): Boolean = needs(e).any { it is TrialNeed.World }

    fun spendsInstead(e: Evolution): Boolean = needs(e).any { it is TrialNeed.Spend }

    fun doneKey(e: Evolution, pokemonId: String): String = "${EvolutionRules.trialId(e)}@$pokemonId"
}
