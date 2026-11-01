package app.pokeplan.world

import kotlinx.serialization.Serializable

/** One line of the expedition log: what happened and when (milliseconds). */
@Serializable
data class JournalLine(val at: Long, val text: String)

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
    /** Until when the Café's brew helps (milliseconds), 0 for none. */
    val brewUntil: Long = 0,
    /** The Guardian has been faced (the story is complete) and whether its reward was given. */
    val guardianDone: Boolean = false,
    val guardianGranted: Boolean = false,
    /** Rare Candies bought from the shop so far (the price rises with each). */
    val shopCandies: Int = 0,
    /** For a daily or weekly job, the day number it was last claimed. */
    val missionClaimedDay: Map<String, Long> = emptyMap(),
    /** The expedition log: finds, milestones, clears, finished jobs, newest last, the latest [JOURNAL_SIZE] kept. */
    val journal: List<JournalLine> = emptyList(),
    /** Skill XP by [WorldSkill] name. */
    val skillXp: Map<String, Int> = emptyMap(),
    /** Facility levels by [Facility] name. */
    val facilities: Map<String, Int> = emptyMap(),
    /** The research milestones already claimed, by species count. */
    val researchClaimed: Set<Int> = emptySet(),
    /** The jobs taken from the mission board, with their progress. */
    val missions: List<MissionProgress> = emptyList(),
    /** The one-time jobs (rescues) already claimed. */
    val missionsDone: Set<String> = emptySet(),
    /** When the team list was last settled, for the "while you were away" line. */
    val lastSeenAt: Long = 0,
    /** The layers other than the home one whose Guardian has been faced, by [WorldLayer.id]. */
    val layersDone: Set<String> = emptySet(),
    /** The legendary quests whose Pokémon has been given, by [LegendQuest.id]. */
    val legends: Set<String> = emptySet(),
) {
    val rank: ExplorerRank get() = ExplorerRank.at(xp)

    /** Whether [layer]'s Guardian has been faced (its story is complete). */
    fun layerDone(layer: WorldLayer): Boolean = if (layer == WorldLayer.HOME) guardianDone else layer.id in layersDone

    /** Whether the legendary quest [id]'s Pokémon has been given. */
    fun hasLegend(id: String): Boolean = id in legends

    /**
     * How much faster floors are in [dungeon] from the legendaries given for its layer, in percent: each Kanto bird makes the dungeons
     * that favour its type quicker. The home Guardian's 10% is separate (`mastery`).
     */
    fun legendFaster(dungeon: DungeonDef): Int =
        Legends.of(dungeon.layer).filter { it.id in legends && it.type != null && it.type in dungeon.favored }.size * Legends.BIRD_FASTER_PERCENT

    companion object { const val JOURNAL_SIZE = 40 }

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
        if (team.members.isEmpty() || team.expedition != null || rank < dungeon.needs || Story.opened(this, dungeon.layer) < dungeon.chapter || (dungeon.postStory && !layerDone(dungeon.layer))) return null
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

    /** Takes a job from the board; null when it is not offered or [Missions.MAX_ACTIVE] are already held. */
    fun acceptMission(id: String): WorldData? {
        val m = Missions.byId(id) ?: return null
        if (m !in Missions.available(this) || missions.size >= Missions.MAX_ACTIVE) return null
        return copy(missions = missions + MissionProgress(m.id))
    }

    /** Gives up a job that is not finished yet (a finished one is claimed instead). */
    fun abandonMission(id: String): WorldData? {
        val p = missions.firstOrNull { it.id == id } ?: return null
        if (p.done) return null
        return copy(missions = missions - p)
    }

    /** Claims a finished job's reward (Poké, Exploration XP, an item) and clears it; null when it is not finished. */
    fun claimMission(id: String, utcOffsetMillis: Long = 0): WorldData? {
        val p = missions.firstOrNull { it.id == id && it.done } ?: return null
        val m = Missions.byId(id) ?: return copy(missions = missions - p)
        val bag = m.itemId?.let { items + (it to (items[it] ?: 0) + m.itemCount) } ?: items
        return copy(
            missions = missions - p, poke = poke + m.poke, xp = xp + m.xp, items = bag,
            missionsDone = if (m.once) missionsDone + m.id else missionsDone,
            missionClaimedDay = if (m.period > 0) missionClaimedDay + (m.id to Missions.day(lastSeenAt, utcOffsetMillis)) else missionClaimedDay,
        )
    }

    fun skillLevel(skill: WorldSkill): Int = WorldSkill.levelAt(skillXp[skill.name] ?: 0)

    fun facilityLevel(facility: Facility): Int = facilities[facility.name] ?: 0

    internal fun withSkillXp(skill: WorldSkill, amount: Int) = copy(skillXp = skillXp + (skill.name to (skillXp[skill.name] ?: 0) + amount))

    /** Builds the next level of [facility] for Poké; null when it is at the top or Poké is short. */
    fun upgrade(facility: Facility): WorldData? {
        val level = facilityLevel(facility)
        val cost = facility.cost(level)
        if (level >= Facility.MAX_LEVEL || poke < cost) return null
        return copy(poke = poke - cost, facilities = facilities + (facility.name to level + 1))
    }

    /** Research milestones reached by the species discovered and not yet claimed. */
    fun researchReady(): List<ResearchStep> = Research.steps.filter { discovered.size >= it.species && it.species !in researchClaimed }

    /** Claims the research milestone at [species] species; null when it is not reached or already claimed. */
    fun claimResearch(species: Int): WorldData? {
        val step = researchReady().firstOrNull { it.species == species } ?: return null
        val bag = step.itemId?.let { items + (it to (items[it] ?: 0) + step.count) } ?: items
        return copy(poke = poke + step.poke, items = bag, researchClaimed = researchClaimed + species)
    }

    /** The Poké the next shop Rare Candy costs. *(tune)* */
    val candyPrice: Int get() = Shop.CANDY_BASE + Shop.CANDY_STEP * shopCandies

    /** Buys a Rare Candy at the Kecleon Shop; null when Poké is short. The caller puts the candy in the Bag. */
    fun buyCandy(): WorldData? {
        if (poke < candyPrice) return null
        return copy(poke = poke - candyPrice, shopCandies = shopCandies + 1)
    }

    /** Brews at the Café at [now] (the caller took the berries; null while a brew still works, so berries are never wasted): every floor finished in the next [Cafe.HOURS] hours is luckier. */
    fun brew(now: Long): WorldData? = if (now < brewUntil) null else copy(brewUntil = now + Cafe.HOURS * 3_600_000L)

    /** Dojo training: [Dojo.COST] Poké for [Dojo.SKILL_XP] XP in [skill]; null when Poké is short or the skill is at the top. */
    fun train(skill: WorldSkill): WorldData? {
        if (poke < Dojo.COST || skillLevel(skill) >= WorldSkill.MAX_LEVEL) return null
        return copy(poke = poke - Dojo.COST).withSkillXp(skill, Dojo.SKILL_XP)
    }

    /** Takes out of every team the Pokémon no longer in [roster] (transferred away); a team left with no one comes home. */
    fun pruned(roster: Set<String>): WorldData {
        if (teams.all { t -> t.members.all { it in roster } }) return this
        return copy(teams = teams.map { t ->
            val left = t.members.filter { it in roster }
            t.copy(members = left, expedition = if (left.isEmpty()) null else t.expedition)
        })
    }

    internal fun withTeam(team: WorldTeam) = replace(team)
}
