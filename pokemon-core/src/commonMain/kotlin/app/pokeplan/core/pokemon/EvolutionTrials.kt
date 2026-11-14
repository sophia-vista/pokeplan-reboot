package app.pokeplan.core.pokemon

import app.pokeplan.core.dex.Evolution
import app.pokeplan.core.dex.EvolutionKind
import app.pokeplan.core.dex.PokemonType

/** Something the phone itself does for an evolution (docs/design.md, "Evolution"): no battle needed, the player does it with the phone in hand. */
enum class DeviceAction(val label: String, val howTo: String) {
    /** Inkay into Malamar: the games turn the console upside down. */
    UPSIDE_DOWN("Turn the phone upside down", "Hold the phone upside down for a moment"),

    /** Milcery into Alcremie: the player spins on the spot. */
    SPIN("Spin around", "Hold the phone flat and turn around once, quickly"),

    /** Pawmo, Bramblin and Rellor: walk with it in the Let's Go mode. */
    STEPS("Walk ${EvolutionTrials.STEPS} steps", "Walk ${EvolutionTrials.STEPS} steps with the phone"),
}

/**
 * What a special evolution (an [EvolutionKind.TRIAL] row) really asks, read from the game condition the data keeps. Most are checked on the spot
 * (a known move, a held item, the party, the stats, the nature); a few are done with the phone ([Device]); the rest, which need a battle or a place
 * in the games, are an Evolution Trial in WORLD ([World]), a job on the board in the region the evolution belongs to.
 */
sealed interface TrialNeed {
    /** Knows [move] (one of its active moves). */
    data class Move(val move: String) : TrialNeed

    /** Knows a move of [type] (Sylveon: a Fairy move). */
    data class MoveType(val type: PokemonType) : TrialNeed

    /** Friendship at the evolution threshold (Sylveon's affection). */
    data object Friendship : TrialNeed

    /** Holds [item] (it is used up, as in the games). */
    data class Hold(val item: String) : TrialNeed

    /** A [speciesId] in its party (Mantyke with a Remoraid). */
    data class PartySpecies(val speciesId: Int) : TrialNeed

    /** A Pokémon of [type] in its party (Pancham with a Dark type). */
    data class PartyType(val type: PokemonType) : TrialNeed

    /** Attack above Defense ([sign] 1), below it (-1) or equal (0): Tyrogue. */
    data class Stats(val sign: Int) : TrialNeed

    /** One of [natures] (Toxel: Amped or Low Key). */
    data class Natures(val natures: Set<Nature>) : TrialNeed

    /** Uses up [count] of [item] fed or carried (Meltan's candies, Gimmighoul's coins). */
    data class Spend(val item: String, val count: Int) : TrialNeed

    /** With a friend (Finizen in the Union Circle): a Link Cord, as for a trade. */
    data object Linked : TrialNeed

    /** Done with the phone. */
    data class Device(val action: DeviceAction) : TrialNeed

    /** An Evolution Trial in WORLD, flavoured after [condition] (the game's own words, for the job's story). */
    data class World(val condition: String) : TrialNeed
}

object EvolutionTrials {
    const val STEPS = 1_000
    const val EVERSTONE = "everstone"
    const val GIMMIGHOUL_COIN = "gimmighoul-coin"
    const val MELTAN_CANDIES = 400
    const val GIMMIGHOUL_COINS = 999

    /** Toxtricity's Amped form takes these natures; every other nature gives Low Key (as in Sword and Shield). */
    val AMPED: Set<Nature> = setOf(
        Nature.HARDY, Nature.BRAVE, Nature.ADAMANT, Nature.NAUGHTY, Nature.DOCILE, Nature.IMPISH, Nature.LAX,
        Nature.HASTY, Nature.JOLLY, Nature.NAIVE, Nature.RASH, Nature.SASSY, Nature.QUIRKY,
    )

    /** Conditions that need a battle, a place or the weather in the games: they become a WORLD trial. */
    private val worldConditions = listOf("rain", "region=", "trigger=shed", "trigger=three-critical-hits", "trigger=take-damage", "trigger=agile-style-move",
        "trigger=recoil-damage", "trigger=strong-style-move", "trigger=use-move", "trigger=three-defeated-bisharp")

    /** What a TRIAL row asks. Empty for any other kind. */
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

    /** The device action an evolution asks, if any. */
    fun device(e: Evolution): DeviceAction? = needs(e).filterIsInstance<TrialNeed.Device>().firstOrNull()?.action

    /** Whether an evolution needs a WORLD trial. */
    fun needsWorld(e: Evolution): Boolean = needs(e).any { it is TrialNeed.World }

    /** Whether a trial uses no Super Candy (Meltan and Gimmighoul gather instead of levelling). */
    fun spendsInstead(e: Evolution): Boolean = needs(e).any { it is TrialNeed.Spend }

    /** The key a done trial (WORLD or phone) is kept under: one per evolution and per Pokémon. */
    fun doneKey(e: Evolution, pokemonId: String): String = "${EvolutionRules.trialId(e)}@$pokemonId"
}
