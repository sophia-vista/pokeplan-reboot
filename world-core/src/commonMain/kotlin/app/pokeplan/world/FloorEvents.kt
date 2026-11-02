package app.pokeplan.world

import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.PokemonType
import app.pokeplan.core.pokemon.Items
import app.pokeplan.core.pokemon.OwnedPokemon
import kotlin.random.Random

/** A field move: what a team member can do on a floor to open a side path (docs/world-module.md, "Floor events"). */
enum class FieldMove(val identifier: String, val label: String) {
    CUT("cut", "Cut"), SURF("surf", "Surf"), ROCK_SMASH("rock-smash", "Rock Smash"), STRENGTH("strength", "Strength"),
    FLASH("flash", "Flash"), DIG("dig", "Dig"), FLY("fly", "Fly");

    /** The Dex move id, or null if the data has no such move. */
    val moveId: Int? get() = Dex.move(identifier)?.id
}

/** What a floor event gives when it is opened. Never a loss: a closed event only means the team went around. */
enum class EventReward { BERRIES, POKE, XP, TREASURE }

/** The kinds of floor event. [move] null means anyone can take it (a helpful traveller). */
enum class FloorEventKind(val title: String, val blocked: String, val opened: String, val move: FieldMove?, val reward: EventReward, val types: Set<PokemonType> = emptySet()) {
    THICKET("Overgrown path", "A thicket covers a side path.", "cleared the thicket and found berries", FieldMove.CUT, EventReward.BERRIES),
    STREAM("A stream", "A stream cuts across the floor.", "crossed the stream to a quiet corner", FieldMove.SURF, EventReward.POKE),
    ROCKS("A wall of rock", "A cracked wall hides something.", "smashed the wall and found a hidden room", FieldMove.ROCK_SMASH, EventReward.TREASURE),
    BOULDER("A heavy boulder", "A boulder blocks a passage.", "moved the boulder and found a stash", FieldMove.STRENGTH, EventReward.POKE),
    DARK_ROOM("A dark room", "A room is too dark to enter.", "lit the room and learned its secrets", FieldMove.FLASH, EventReward.XP),
    BURROW("A burrow", "A burrow runs under the wall.", "dug through to a shortcut", FieldMove.DIG, EventReward.POKE),
    GAP("A gap in the floor", "A wide gap splits the floor.", "flew across to a ledge", FieldMove.FLY, EventReward.XP),
    // Hazards: a team with a member of the right type weathers it and gathers something; any other team only hurries past.
    FUMES("Toxic fumes", "Fumes seep from a crack in the wall.", "weathered the fumes and gathered ore", null, EventReward.POKE, setOf(PokemonType.POISON, PokemonType.STEEL)),
    VENT("A heat vent", "A vent blasts hot air across the path.", "stood the heat and gathered warm stones", null, EventReward.XP, setOf(PokemonType.FIRE, PokemonType.WATER)),
    ICE_PATCH("A slick of ice", "The floor is a sheet of ice.", "crossed the ice and found a cache", null, EventReward.BERRIES, setOf(PokemonType.ICE, PokemonType.FLYING)),
    STATIC("A crackling field", "The air crackles with static.", "shrugged off the static and found a spark stone", null, EventReward.POKE, setOf(PokemonType.GROUND, PokemonType.ELECTRIC)),
    TRAVELLER("A traveller", "A traveller passes by.", "was given berries by a traveller", null, EventReward.BERRIES),
}

/** One floor event on a floor: what it was, and who (if anyone) opened it. */
data class FloorEventResult(val kind: FloorEventKind, val floor: Int, val user: OwnedPokemon?)

/**
 * Floor events: now and then (about one floor in ten, never the first) something is on the floor that a field move can open. They only ever
 * add: a team that cannot open one goes around and loses nothing, and nothing on the way to a Guardian needs one. Whether and what is worked out
 * from the dungeon, the team, the trip and the floor alone, so a settle gives the same result however it is split, and offline play equals
 * staying and watching. Who can open it is the team's: a Pokémon whose learnset has the move and whose level is at least [MIN_LEVEL].
 */
object FloorEvents {
    /** One floor in this many percent has an event. *(tune)* */
    const val PERCENT = 10

    /** The lowest level that can use a field move. */
    const val MIN_LEVEL = 10

    /** The event on floor number [floor] (1-based, within the dungeon) of the trip, or null. [index] is the floor's position in the whole trip. */
    fun kindAt(teamId: Int, trip: Expedition, floor: Int, index: Int): FloorEventKind? {
        if (floor < 2) return null
        val random = Random(WorldSim.eventSeed(teamId, trip, index))
        if (random.nextInt(100) >= PERCENT) return null
        return FloorEventKind.entries[random.nextInt(FloorEventKind.entries.size)]
    }

    /** Whether [mon] can use [move]: it learns it (by any method) and is high enough in level. */
    fun canUse(mon: OwnedPokemon, move: FieldMove): Boolean {
        val id = move.moveId ?: return false
        return mon.level >= MIN_LEVEL && Dex.learnset(mon.form).learns(id)
    }

    /** The team member who opens [kind]: the first by Pokémon id who can use its move (anyone, for a kind with none). Null when nobody can. */
    fun opener(kind: FloorEventKind, members: List<OwnedPokemon>): OwnedPokemon? {
        val sorted = members.sortedBy { it.id }
        kind.move?.let { move -> return sorted.firstOrNull { canUse(it, move) } }
        if (kind.types.isNotEmpty()) return sorted.firstOrNull { m -> m.form.types.any { it in kind.types } }
        return sorted.firstOrNull()
    }

    /** The reward amounts for an opened event on [floor] of [dungeon]; only the one that matches the kind's [EventReward] is used. */
    fun poke(floor: Int): Int = 10 + floor * 3
    fun xp(floor: Int): Int = WorldSim.XP_BASE + floor
    fun treasureTier(dungeon: DungeonDef, floor: Int): Int = if (floor >= dungeon.floors - 1) 3 else if (floor >= dungeon.floors / 2) 2 else 1

    /** A berry flavour for an event's berries, from the same seed. */
    fun berryFor(random: Random): String = Items.berry(listOf("sweet", "dry", "sour", "spicy", "bitter").random(random))
}

/** What a floor event gives, granted into [data] (the same for an event opened on the floor and a choice answered later). Returns the new data and a phrase for the log. */
internal fun grantEventReward(data: WorldData, reward: EventReward, dungeon: DungeonDef, floor: Int, random: Random, events: MutableList<WorldEvent>? = null): Pair<WorldData, String> {
    var d = data
    val gave: String
    when (reward) {
        EventReward.BERRIES -> {
            val berry = FloorEvents.berryFor(random)
            events?.add(WorldEvent.Item(berry, 2))
            d = d.copy(items = d.items + (berry to (d.items[berry] ?: 0) + 2)); gave = "2 berries"
        }
        EventReward.POKE -> { val p = FloorEvents.poke(floor); events?.add(WorldEvent.Poke(p)); d = d.copy(poke = d.poke + p); gave = "$p Poké" }
        EventReward.XP -> { val x = FloorEvents.xp(floor); d = d.copy(xp = d.xp + x); gave = "$x XP" }
        EventReward.TREASURE -> { val t = FloorEvents.treasureTier(dungeon, floor); events?.add(WorldEvent.Treasure(t)); d = d.copy(treasure = d.treasure + (t to (d.treasure[t] ?: 0) + 1)); gave = "a treasure box" }
    }
    return d to gave
}
