package app.pokeplan.world

import kotlinx.serialization.Serializable

/** An expedition under way: the dungeon, when it began, and how far settling has got (so a floor is never counted twice). */
@Serializable
data class Expedition(val dungeonId: String, val startedAt: Long, val settledTo: Long, val floorsClimbed: Int = 0)

/**
 * An expedition team: up to [MAX_MEMBERS] Pokémon of the shared collection by id, and what it is doing. A Pokémon belongs to
 * one team at a time (docs/world.md, section 6): assigned, it cannot join another until recalled.
 */
@Serializable
data class WorldTeam(val id: Int, val members: List<String> = emptyList(), val expedition: Expedition? = null) {
    companion object { const val MAX_MEMBERS = 4 }
}

/** A Pokémon the exploring found, waiting to be recruited into the shared collection (an Exploration Find). */
@Serializable
data class Find(val speciesId: Int, val dungeonId: String, val floor: Int, val at: Long)

/**
 * Everything the World game keeps: its teams, how deep each dungeon has been explored, its own XP and Poké, finds waiting
 * to be recruited, the items collected waiting for the Bag, and how far the story has got. It is client-side data of the save.
 */
@Serializable
data class WorldData(
    val teams: List<WorldTeam> = emptyList(),
    /** The deepest floor reached in each dungeon, by dungeon id. */
    val deepest: Map<String, Int> = emptyMap(),
    /** How many times each dungeon has been cleared, by id. */
    val clears: Map<String, Int> = emptyMap(),
    val xp: Int = 0,
    val poke: Int = 0,
    val finds: List<Find> = emptyList(),
    /** Items found and not yet in the shared Bag, by item id. */
    val items: Map<String, Int> = emptyMap(),
    /** Treasure boxes found and not yet opened, by tier (1 plain, 2 silver, 3 gold). */
    val treasure: Map<Int, Int> = emptyMap(),
    /** The milestones already paid, as `dungeon:floor`, so each pays once. */
    val milestonesPaid: Set<String> = emptySet(),
    /** The species found so far (the research notebook's start): each counts once. */
    val discovered: Set<Int> = emptySet(),
    val storyChapter: Int = 0,
    /** When the team list was last settled, for the "while you were away" line. */
    val lastSeenAt: Long = 0,
) {
    val rank: ExplorerRank get() = ExplorerRank.at(xp)

    /** How many teams the rank allows. */
    val teamSlots: Int get() = rank.teamSlots

    fun team(id: Int): WorldTeam? = teams.firstOrNull { it.id == id }

    /** The teams, with empty ones standing in for slots the rank allows but nobody has used yet. */
    fun slots(): List<WorldTeam> = (1..teamSlots).map { id -> team(id) ?: WorldTeam(id) }

    /** The Pokémon ids on any team. */
    val assigned: Set<String> get() = teams.flatMap { it.members }.toSet()

    private fun replace(team: WorldTeam) = copy(teams = (teams.filter { it.id != team.id } + team).sortedBy { it.id })

    /** Sets who is on team [id] (up to [WorldTeam.MAX_MEMBERS], none on another team, none while the team is out). Null when it cannot. */
    fun assign(id: Int, members: List<String>): WorldData? {
        if (id !in 1..teamSlots || members.size > WorldTeam.MAX_MEMBERS || members.distinct().size != members.size) return null
        val current = team(id) ?: WorldTeam(id)
        if (current.expedition != null) return null
        val others = teams.filter { it.id != id }.flatMap { it.members }.toSet()
        if (members.any { it in others }) return null
        return replace(current.copy(members = members))
    }

    /** Sends team [id] to [dungeonId] at [now]; null when the team is empty or busy, the dungeon unknown or its rank not reached. */
    fun start(id: Int, dungeonId: String, now: Long): WorldData? {
        val team = team(id) ?: return null
        val dungeon = Dungeons.byId(dungeonId) ?: return null
        if (team.members.isEmpty() || team.expedition != null || rank < dungeon.needs) return null
        return replace(team.copy(expedition = Expedition(dungeonId, now, now)))
    }

    /** Brings team [id] home; what it found so far has been settled already, so nothing is lost. */
    fun recall(id: Int): WorldData? {
        val team = team(id) ?: return null
        if (team.expedition == null) return null
        return replace(team.copy(expedition = null))
    }

    /** Removes the find at [index] (it was recruited), counting its species as discovered. */
    fun withoutFind(index: Int): WorldData = copy(finds = finds.filterIndexed { i, _ -> i != index })

    internal fun withTeam(team: WorldTeam) = replace(team)
}
