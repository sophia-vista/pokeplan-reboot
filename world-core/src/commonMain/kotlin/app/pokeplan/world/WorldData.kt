package app.pokeplan.world

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** One line of the expedition log: what happened and when (milliseconds). */
@Serializable
data class JournalLine(val at: Long, val text: String, val kind: String = KIND_NOTE, val place: String = "") {
    companion object {
        /** An old line, or anything else without a kind. */
        const val KIND_NOTE = "note"
        const val KIND_FIND = "find"
        const val KIND_MILESTONE = "milestone"
        const val KIND_CLEAR = "clear"
        const val KIND_JOB = "job"
        const val KIND_EVENT = "event"
        const val KIND_GUARDIAN = "guardian"
        const val KIND_SETBACK = "setback"
        /** The kinds in the order the Journal's filter shows them. */
        val KINDS = listOf(KIND_FIND, KIND_CLEAR, KIND_MILESTONE, KIND_EVENT, KIND_JOB, KIND_GUARDIAN, KIND_SETBACK)
    }
}

/** A saved team: a name and who was on it (by Pokémon id), to put together again in one tap. */
@Serializable
data class TeamPreset(val name: String, val members: List<String>)

/** An expedition under way: the dungeon, when it began, and how far settling has got (so a floor is never counted twice). */
@Serializable
data class Expedition(val dungeonId: String, val startedAt: Long, val settledTo: Long, val floorsClimbed: Int = 0)

/**
 * An expedition team: up to [MAX_MEMBERS] Pokémon of the shared collection by id, and what it is doing. A Pokémon belongs to
 * one team at a time (docs/pokelife/MODULES/WORLD.md section 14): assigned, it cannot join another until recalled.
 */
@Serializable
data class WorldTeam(
    val id: Int, val members: List<String> = emptyList(), val expedition: Expedition? = null,
    /** What the player called it (empty: "Team 1"), and its accent, an index into [ACCENTS]. */
    val name: String = "", val accent: Int = 0,
) {
    /** What to call it on screen. */
    val title: String get() = name.ifBlank { "Team $id" }

    companion object {
        const val MAX_MEMBERS = 4
        const val NAME_MAX = 16
        const val ACCENTS = 6
    }
}

/** A Pokémon the exploring found, waiting to be recruited into the shared collection (an Exploration Find). */
@Serializable
data class Find(val speciesId: Int, val dungeonId: String, val floor: Int, val at: Long)

/**
 * Everything the World game keeps: its teams, how deep each dungeon has been explored, its own XP and Poké, finds waiting
 * to be recruited, the items collected waiting for the Bag, and how far the story has got. It is client-side data of the save.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WorldData(
    val teams: List<WorldTeam> = emptyList(),
    /** The deepest floor reached in each dungeon, by dungeon id. */
    val deepest: Map<String, Int> = emptyMap(),
    /** How many times each dungeon has been cleared, by id. */
    val clears: Map<String, Int> = emptyMap(),
    val xp: Int = 0,
    /**
     * Poké the World rules earned in the last call (floors, milestones, floor events, a job, a research step) and the app has not yet put in
     * the one balance, `PlayerState.coins`. **Never saved**, and never a balance: the app adds it to the coins (through the ledger) and sets
     * it back to 0 after every action, and the rules that spend Poké are given the balance as an input and charge nothing themselves
     * (docs/pokelife POKEMON_ECOSYSTEM.md section 11.2).
     */
    @Transient val pokeEarned: Int = 0,
    /** Legacy: the World balance of a save from before the Poké merge, read once by the app's merge into the balance and then 0 (and so not written). */
    @SerialName("poke") @EncodeDefault(EncodeDefault.Mode.NEVER) val legacyPoke: Int = 0,
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
    /**
     * The latest moment already settled before the phone's clock was set back, 0 when it was not (WORLD.md 3.1). Teams keep climbing on the earlier clock,
     * but when the clock reaches this moment again the time up to it was counted once already, so a clock set back and forward does not pay those floors twice.
     */
    val rewoundFrom: Long = 0,
    /** The layers other than the home one whose Guardian has been faced, by [WorldLayer.id]. */
    val layersDone: Set<String> = emptySet(),
    /** The legendary quests whose Pokémon has been given, by [LegendQuest.id]. */
    val legends: Set<String> = emptySet(),
    /** Evolution Trials done in WORLD ([EvolutionTrialSites]), as `EvolutionTrials.doneKey`: one per evolution and Pokémon. */
    val evolutionTrials: Set<String> = emptySet(),
    /** The shape of this save (for later migrations): 1 since Phase 2 added team names, presets and the typed journal. */
    val worldVersion: Int = 1,
    /** Saved teams, up to [MAX_PRESETS]. */
    val presets: List<TeamPreset> = emptyList(),
    /** The highest rank whose moment was shown (-1: not recorded yet, an old save adopts what it has) and, by layer id, how many story chapters were shown. */
    val seenRank: Int = -1,
    val seenChapters: Map<String, Int> = emptyMap(),
    /** Floor choices waiting for an answer (see [Choices]). */
    val choices: List<PendingChoice> = emptyList(),
    /** The legendary quests whose scene was shown (null: not recorded yet, an older save adopts the ones it has). */
    val legendsSeen: Set<String>? = null,
    /** The story scenes and episodes (`<layer>:<chapter>`) watched to the end: the Story archive's record, never a reward gate. */
    val scenesDone: Set<String> = emptySet(),
    val episodesDone: Set<String> = emptySet(),
    /** The answers given in story choices (flags, for later callbacks only) and where an unfinished episode left off (the next scene's index, by episode key). */
    val storyFlags: Set<String> = emptySet(),
    val storyResume: Map<String, Int> = emptyMap(),
) {
    val rank: ExplorerRank get() = ExplorerRank.at(xp)

    /** Whether [layer]'s Guardian has been faced (its story is complete). */
    fun layerDone(layer: WorldLayer): Boolean = if (layer == WorldLayer.EXPLORERS) guardianDone else layer.id in layersDone

    /** Whether the legendary quest [id]'s Pokémon has been given. */
    fun hasLegend(id: String): Boolean = id in legends

    /** The legendaries given for [layer]'s quests. */
    private fun given(layer: WorldLayer): List<LegendQuest> = Legends.of(layer).filter { it.id in legends }

    /**
     * How much faster floors are in [dungeon] from the legendaries given for its layer, in percent: each one whose types include one the
     * dungeon favours (the Kanto birds, Lugia). The home Guardian's 10% is separate (`mastery`).
     */
    fun legendFaster(dungeon: DungeonDef): Int = given(dungeon.layer).count { q -> q.types.any { it in dungeon.favored } } * Legends.FASTER_PERCENT

    /** More chance of a find on a floor of [dungeon]'s layer from the legendaries given there, in permille. */
    fun legendFindPermille(dungeon: DungeonDef): Int = given(dungeon.layer).sumOf { it.findPermille }

    /** More chance of a treasure box on a floor of [dungeon]'s layer from the legendaries given there, in percent. */
    fun legendTreasurePercent(dungeon: DungeonDef): Int = given(dungeon.layer).sumOf { it.treasurePercent }

    companion object { const val JOURNAL_SIZE = 40; const val MAX_PRESETS = 6 }

    /** Names team [id] (cleaned up and cut to [WorldTeam.NAME_MAX]; empty goes back to "Team n"). Null for a slot the rank has not opened. */
    fun renameTeam(id: Int, name: String): WorldData? {
        if (id !in 1..teamSlots) return null
        val clean = name.trim().replace(Regex("\\s+"), " ").take(WorldTeam.NAME_MAX)
        return replace((team(id) ?: WorldTeam(id)).copy(name = clean))
    }

    /** Gives team [id] the accent number [accent] (0 until [WorldTeam.ACCENTS]). */
    fun recolourTeam(id: Int, accent: Int): WorldData? {
        if (id !in 1..teamSlots || accent !in 0 until WorldTeam.ACCENTS) return null
        return replace((team(id) ?: WorldTeam(id)).copy(accent = accent))
    }

    /** Saves [members] under [name] (the same name replaces; the newest [MAX_PRESETS] stay). Null when there is no name or nobody. */
    fun savePreset(name: String, members: List<String>): WorldData? {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(WorldTeam.NAME_MAX)
        if (clean.isEmpty() || members.isEmpty()) return null
        return copy(presets = (presets.filter { it.name != clean } + TeamPreset(clean, members.distinct().take(WorldTeam.MAX_MEMBERS))).takeLast(MAX_PRESETS))
    }

    /** Answers the waiting choice [id] with [option] (0 or 1); null when there is no such choice. */
    fun resolveChoice(id: String, option: Int, now: Long): WorldData? = choices.firstOrNull { it.id == id }?.let { Choices.answer(this, it, option, now) }

    fun deletePreset(name: String): WorldData = copy(presets = presets.filter { it.name != name })

    /**
     * Puts a saved team on team [id]: those of its members that are still [owned] and not on another team (a Pokémon traded away, or
     * busy elsewhere, is left out). Null when the preset is unknown, nobody is left, or the team cannot be changed.
     */
    fun applyPreset(id: Int, name: String, owned: Set<String>): WorldData? {
        val preset = presets.firstOrNull { it.name == name } ?: return null
        val others = teams.filter { it.id != id }.flatMap { it.members }.toSet()
        val usable = preset.members.filter { it in owned && it !in others }
        return if (usable.isEmpty()) null else assign(id, usable)
    }

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
    fun acceptMission(id: String, utcOffsetMillis: Long = 0): WorldData? {
        val m = Missions.byId(id) ?: return null
        // The same day the board and the claim count in (the player's), or a job the board offers could be refused here.
        if (m !in Missions.available(this, utcOffsetMillis = utcOffsetMillis) || missions.size >= Missions.MAX_ACTIVE) return null
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
            missions = missions - p, pokeEarned = pokeEarned + m.poke, xp = xp + m.xp, items = bag,
            missionsDone = if (m.once) missionsDone + m.id else missionsDone,
            missionClaimedDay = if (m.period > 0) missionClaimedDay + (m.id to Missions.day(lastSeenAt, utcOffsetMillis)) else missionClaimedDay,
        )
    }

    fun skillLevel(skill: WorldSkill): Int = WorldSkill.levelAt(skillXp[skill.name] ?: 0)

    fun facilityLevel(facility: Facility): Int = facilities[facility.name] ?: 0

    internal fun withSkillXp(skill: WorldSkill, amount: Int) = copy(skillXp = skillXp + (skill.name to (skillXp[skill.name] ?: 0) + amount))

    /** What the next level of [facility] costs in Poké. */
    fun upgradeCost(facility: Facility): Int = facility.cost(facilityLevel(facility))

    /** Builds the next level of [facility]; null when it is at the top or the [balance] is short. The caller charges [upgradeCost]. */
    fun upgrade(facility: Facility, balance: Int): WorldData? {
        val level = facilityLevel(facility)
        if (level >= Facility.MAX_LEVEL || balance < facility.cost(level)) return null
        return copy(facilities = facilities + (facility.name to level + 1))
    }

    /** Research milestones reached by the species discovered and not yet claimed. */
    fun researchReady(): List<ResearchStep> = Research.steps.filter { discovered.size >= it.species && it.species !in researchClaimed }

    /** Claims the research milestone at [species] species; null when it is not reached or already claimed. */
    fun claimResearch(species: Int): WorldData? {
        val step = researchReady().firstOrNull { it.species == species } ?: return null
        val bag = step.itemId?.let { items + (it to (items[it] ?: 0) + step.count) } ?: items
        return copy(pokeEarned = pokeEarned + step.poke, items = bag, researchClaimed = researchClaimed + species)
    }

    /** The Poké the next shop Rare Candy costs. *(tune)* */
    val candyPrice: Int get() = Shop.CANDY_BASE + Shop.CANDY_STEP * shopCandies

    /** Buys a Rare Candy at the Kecleon Shop; null when the [balance] is short. The caller charges [candyPrice] (as it was before) and puts the candy in the Bag. */
    fun buyCandy(balance: Int): WorldData? {
        if (balance < candyPrice) return null
        return copy(shopCandies = shopCandies + 1)
    }

    /** Brews at the Café at [now] (the caller took the berries; null while a brew still works, so berries are never wasted): every floor finished in the next [Cafe.HOURS] hours is luckier. */
    fun brew(now: Long): WorldData? = if (now < brewUntil) null else copy(brewUntil = now + Cafe.HOURS * 3_600_000L)

    /** Dojo training: [Dojo.SKILL_XP] XP in [skill] for [Dojo.COST] Poké, which the caller charges; null when the [balance] is short or the skill is at the top. */
    fun train(skill: WorldSkill, balance: Int): WorldData? {
        if (balance < Dojo.COST || skillLevel(skill) >= WorldSkill.MAX_LEVEL) return null
        return withSkillXp(skill, Dojo.SKILL_XP)
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
