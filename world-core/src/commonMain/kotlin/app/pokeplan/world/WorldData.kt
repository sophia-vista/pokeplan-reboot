package app.pokeplan.world

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class JournalLine(val at: Long, val text: String, val kind: String = KIND_NOTE, val place: String = "") {
    companion object {
        const val KIND_NOTE = "note"
        const val KIND_FIND = "find"
        const val KIND_MILESTONE = "milestone"
        const val KIND_CLEAR = "clear"
        const val KIND_JOB = "job"
        const val KIND_EVENT = "event"
        const val KIND_GUARDIAN = "guardian"
        const val KIND_SETBACK = "setback"
        val KINDS = listOf(KIND_FIND, KIND_CLEAR, KIND_MILESTONE, KIND_EVENT, KIND_JOB, KIND_GUARDIAN, KIND_SETBACK)
    }
}

@Serializable
data class TeamPreset(val name: String, val members: List<String>)

@Serializable
data class Expedition(val dungeonId: String, val startedAt: Long, val settledTo: Long, val floorsClimbed: Int = 0)

@Serializable
data class WorldTeam(
    val id: Int, val members: List<String> = emptyList(), val expedition: Expedition? = null,
    val name: String = "", val accent: Int = 0,
) {
    val title: String get() = name.ifBlank { "Team $id" }

    companion object {
        const val MAX_MEMBERS = 4
        const val NAME_MAX = 16
        const val ACCENTS = 6
    }
}

@Serializable
data class Find(val speciesId: Int, val dungeonId: String, val floor: Int, val at: Long)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class WorldData(
    val teams: List<WorldTeam> = emptyList(),
    val deepest: Map<String, Int> = emptyMap(),
    val clears: Map<String, Int> = emptyMap(),
    val xp: Int = 0,
    @Transient val pokeEarned: Int = 0,
    @SerialName("poke") @EncodeDefault(EncodeDefault.Mode.NEVER) val legacyPoke: Int = 0,
    val finds: List<Find> = emptyList(),
    val items: Map<String, Int> = emptyMap(),
    val treasure: Map<Int, Int> = emptyMap(),
    val milestonesPaid: Set<String> = emptySet(),
    val discovered: Set<Int> = emptySet(),
    val storyChapter: Int = 0,
    val brewUntil: Long = 0,
    val guardianDone: Boolean = false,
    val guardianGranted: Boolean = false,
    val shopCandies: Int = 0,
    val missionClaimedDay: Map<String, Long> = emptyMap(),
    val journal: List<JournalLine> = emptyList(),
    val skillXp: Map<String, Int> = emptyMap(),
    val facilities: Map<String, Int> = emptyMap(),
    val researchClaimed: Set<Int> = emptySet(),
    val missions: List<MissionProgress> = emptyList(),
    val missionsDone: Set<String> = emptySet(),
    val lastSeenAt: Long = 0,
    val rewoundFrom: Long = 0,
    val layersDone: Set<String> = emptySet(),
    val legends: Set<String> = emptySet(),
    val evolutionTrials: Set<String> = emptySet(),
    val worldVersion: Int = 1,
    val presets: List<TeamPreset> = emptyList(),
    val seenRank: Int = -1,
    val seenChapters: Map<String, Int> = emptyMap(),
    val choices: List<PendingChoice> = emptyList(),
    val legendsSeen: Set<String>? = null,
    val scenesDone: Set<String> = emptySet(),
    val episodesDone: Set<String> = emptySet(),
    val storyFlags: Set<String> = emptySet(),
    val storyResume: Map<String, Int> = emptyMap(),
) {
    val rank: ExplorerRank get() = ExplorerRank.at(xp)

    fun layerDone(layer: WorldLayer): Boolean = if (layer == WorldLayer.EXPLORERS) guardianDone else layer.id in layersDone

    fun hasLegend(id: String): Boolean = id in legends

    private fun given(layer: WorldLayer): List<LegendQuest> = Legends.of(layer).filter { it.id in legends }

    fun legendFaster(dungeon: DungeonDef): Int = given(dungeon.layer).count { q -> q.types.any { it in dungeon.favored } } * Legends.FASTER_PERCENT

    fun legendFindPermille(dungeon: DungeonDef): Int = given(dungeon.layer).sumOf { it.findPermille }

    fun legendTreasurePercent(dungeon: DungeonDef): Int = given(dungeon.layer).sumOf { it.treasurePercent }

    companion object { const val JOURNAL_SIZE = 40; const val MAX_PRESETS = 6 }

    fun renameTeam(id: Int, name: String): WorldData? {
        if (id !in 1..teamSlots) return null
        val clean = name.trim().replace(Regex("\\s+"), " ").take(WorldTeam.NAME_MAX)
        return replace((team(id) ?: WorldTeam(id)).copy(name = clean))
    }

    fun recolourTeam(id: Int, accent: Int): WorldData? {
        if (id !in 1..teamSlots || accent !in 0 until WorldTeam.ACCENTS) return null
        return replace((team(id) ?: WorldTeam(id)).copy(accent = accent))
    }

    fun savePreset(name: String, members: List<String>): WorldData? {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(WorldTeam.NAME_MAX)
        if (clean.isEmpty() || members.isEmpty()) return null
        return copy(presets = (presets.filter { it.name != clean } + TeamPreset(clean, members.distinct().take(WorldTeam.MAX_MEMBERS))).takeLast(MAX_PRESETS))
    }

    fun resolveChoice(id: String, option: Int, now: Long): WorldData? = choices.firstOrNull { it.id == id }?.let { Choices.answer(this, it, option, now) }

    fun deletePreset(name: String): WorldData = copy(presets = presets.filter { it.name != name })

    fun applyPreset(id: Int, name: String, owned: Set<String>): WorldData? {
        val preset = presets.firstOrNull { it.name == name } ?: return null
        val others = teams.filter { it.id != id }.flatMap { it.members }.toSet()
        val usable = preset.members.filter { it in owned && it !in others }
        return if (usable.isEmpty()) null else assign(id, usable)
    }

    val teamSlots: Int get() = rank.teamSlots

    fun team(id: Int): WorldTeam? = teams.firstOrNull { it.id == id }

    fun slots(): List<WorldTeam> = (1..teamSlots).map { id -> team(id) ?: WorldTeam(id) }

    val assigned: Set<String> get() = teams.flatMap { it.members }.toSet()

    private fun replace(team: WorldTeam) = copy(teams = (teams.filter { it.id != team.id } + team).sortedBy { it.id })

    fun assign(id: Int, members: List<String>): WorldData? {
        if (id !in 1..teamSlots || members.size > WorldTeam.MAX_MEMBERS || members.distinct().size != members.size) return null
        val current = team(id) ?: WorldTeam(id)
        if (current.expedition != null) return null
        val others = teams.filter { it.id != id }.flatMap { it.members }.toSet()
        if (members.any { it in others }) return null
        return replace(current.copy(members = members))
    }

    fun start(id: Int, dungeonId: String, now: Long): WorldData? {
        val team = team(id) ?: return null
        val dungeon = Dungeons.byId(dungeonId) ?: return null
        if (team.members.isEmpty() || team.expedition != null || rank < dungeon.needs || Story.opened(this, dungeon.layer) < dungeon.chapter || (dungeon.postStory && !layerDone(dungeon.layer))) return null
        return replace(team.copy(expedition = Expedition(dungeonId, now, now)))
    }

    fun recall(id: Int): WorldData? {
        val team = team(id) ?: return null
        if (team.expedition == null) return null
        return replace(team.copy(expedition = null))
    }

    fun withoutFind(index: Int): WorldData = copy(finds = finds.filterIndexed { i, _ -> i != index })

    fun acceptMission(id: String, utcOffsetMillis: Long = 0): WorldData? {
        val m = Missions.byId(id) ?: return null
        if (m !in Missions.available(this, utcOffsetMillis = utcOffsetMillis) || missions.size >= Missions.MAX_ACTIVE) return null
        return copy(missions = missions + MissionProgress(m.id))
    }

    fun abandonMission(id: String): WorldData? {
        val p = missions.firstOrNull { it.id == id } ?: return null
        if (p.done) return null
        return copy(missions = missions - p)
    }

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

    fun upgradeCost(facility: Facility): Int = facility.cost(facilityLevel(facility))

    fun upgrade(facility: Facility, balance: Int): WorldData? {
        val level = facilityLevel(facility)
        if (level >= Facility.MAX_LEVEL || balance < facility.cost(level)) return null
        return copy(facilities = facilities + (facility.name to level + 1))
    }

    fun researchReady(): List<ResearchStep> = Research.steps.filter { discovered.size >= it.species && it.species !in researchClaimed }

    fun claimResearch(species: Int): WorldData? {
        val step = researchReady().firstOrNull { it.species == species } ?: return null
        val bag = step.itemId?.let { items + (it to (items[it] ?: 0) + step.count) } ?: items
        return copy(pokeEarned = pokeEarned + step.poke, items = bag, researchClaimed = researchClaimed + species)
    }


    fun brew(now: Long): WorldData? = if (now < brewUntil) null else copy(brewUntil = now + Cafe.HOURS * 3_600_000L)

    fun train(skill: WorldSkill, balance: Int): WorldData? {
        if (balance < Dojo.COST || skillLevel(skill) >= WorldSkill.MAX_LEVEL) return null
        return withSkillXp(skill, Dojo.SKILL_XP)
    }

    fun pruned(roster: Set<String>): WorldData {
        if (teams.all { t -> t.members.all { it in roster } }) return this
        return copy(teams = teams.map { t ->
            val left = t.members.filter { it in roster }
            t.copy(members = left, expedition = if (left.isEmpty()) null else t.expedition)
        })
    }

    internal fun withTeam(team: WorldTeam) = replace(team)
}
