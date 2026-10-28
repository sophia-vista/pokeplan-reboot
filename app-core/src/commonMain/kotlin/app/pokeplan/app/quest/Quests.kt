package app.pokeplan.app.quest

import app.pokeplan.app.AppData
import app.pokeplan.app.QuestState
import app.pokeplan.core.dex.Dex
import app.pokeplan.core.dex.Region
import app.pokeplan.core.reward.TrainerRank
import app.pokeplan.plan.Rank
import app.pokeplan.plan.TaskState
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.TimeZone

/** What a quest asks for. Each is counted from the day the quest was accepted. */
enum class RequirementKind(val describe: (Int) -> String) {
    FOCUS_SESSIONS({ "Finish $it focus sessions" }),
    DEEP_FOCUS_SESSIONS({ "Finish $it Deep Focus sessions" }),
    FOCUS_MINUTES({ "Focus for $it minutes" }),
    TASKS_DONE({ "Finish $it tasks" }),
    TASKS_DONE_HARD({ "Finish $it tasks of rank B or A" }),
    HABIT_DAYS({ "Do your habits on $it days" }),
    WEEKLY_REVIEWS({ "Finish $it weekly reviews" }),
    MILESTONES_DONE({ "Reach $it goal milestones" }),
    ACTIVE_DAYS({ "Be active on $it days" }),
    STREAK_DAYS({ "Reach a streak of $it days" }),
}

data class Requirement(val kind: RequirementKind, val target: Int) {
    val text: String get() = kind.describe(target)
}

/**
 * A quest for one legendary-class or mythical species. Finishing it gives that Pokémon, as a Guardian candidate.
 * [dexShare] is the share of [dexRegion]'s ordinary species that must already be caught; null means none.
 */
data class Quest(
    val id: String,
    val speciesId: Int,
    val title: String,
    val story: String,
    val requirements: List<Requirement>,
    /** Trainer rank index needed to see the quest. */
    val unlockRank: Int,
    val dexRegion: Region? = null,
    val dexShare: Double = 0.0,
    /** A slow quest: available early, finished over weeks. */
    val longTerm: Boolean = false,
)

/** Every legendary-class and mythical species has a quest. See "Legendaries and mythicals" in docs/design.md. Numbers are *(tune)*. */
object Quests {
    /** Quests in progress at once, so nobody is buried in them. */
    const val MAX_ACTIVE = 3

    private val lakeTrio = mapOf(
        "uxie" to Quest(
            "uxie", 0, "The Being of Knowledge",
            "Uxie rewards steady learning: show up to focus again and again, reach the milestones of a real goal, and look back on your weeks.",
            listOf(Requirement(RequirementKind.FOCUS_SESSIONS, 15), Requirement(RequirementKind.MILESTONES_DONE, 3), Requirement(RequirementKind.WEEKLY_REVIEWS, 3)),
            unlockRank = 4,
        ),
        "mesprit" to Quest(
            "mesprit", 0, "The Being of Emotion",
            "Mesprit answers to care: small habits kept gently, a weekly look at how you are doing, and days spent looking after yourself.",
            listOf(Requirement(RequirementKind.HABIT_DAYS, 21), Requirement(RequirementKind.WEEKLY_REVIEWS, 4), Requirement(RequirementKind.ACTIVE_DAYS, 21)),
            unlockRank = 4,
        ),
        "azelf" to Quest(
            "azelf", 0, "The Being of Willpower",
            "Azelf respects effort that holds: deep, undisturbed focus, the harder tasks you have been putting off, and a streak that keeps going.",
            listOf(Requirement(RequirementKind.DEEP_FOCUS_SESSIONS, 10), Requirement(RequirementKind.TASKS_DONE_HARD, 10), Requirement(RequirementKind.STREAK_DAYS, 7)),
            unlockRank = 4,
        ),
    )

    val all: List<Quest> by lazy {
        val regionOf = mutableMapOf<Int, Region>()
        for (region in Region.entries.filter { it.released }) for (s in Dex.regionDex(region)) if (s.id !in regionOf) regionOf[s.id] = region
        Dex.species.filter { it.isSpecial }.map { s ->
            val name = s.name
            lakeTrio[s.identifier]?.copy(speciesId = s.id, dexRegion = Region.PLAN)
                ?: if (s.isMythical) Quest(
                    s.identifier, s.id, "The long road to $name",
                    "$name is a rare friend, and it takes time. Keep showing up: steady days, finished tasks and focused hours add up.",
                    listOf(
                        Requirement(RequirementKind.ACTIVE_DAYS, 45), Requirement(RequirementKind.TASKS_DONE, 120),
                        Requirement(RequirementKind.FOCUS_MINUTES, 1200), Requirement(RequirementKind.WEEKLY_REVIEWS, 6),
                    ),
                    unlockRank = 2, longTerm = true,
                ) else {
                    val strength = Dex.lineStrength(s.id)
                    val tier = when { strength >= 680 -> 3; strength >= 600 -> 2; else -> 1 }
                    val region = regionOf[s.id]
                    val (deep, hard, days, rank, share) = when (tier) {
                        3 -> Tier(12, 25, 30, 8, 0.7)
                        2 -> Tier(8, 15, 21, 6, 0.6)
                        else -> Tier(5, 8, 14, 4, 0.5)
                    }
                    Quest(
                        s.identifier, s.id, "Earn the trust of $name",
                        "$name watches from afar. Prove your focus with deep, undisturbed sessions and the harder tasks, over time.",
                        listOf(Requirement(RequirementKind.DEEP_FOCUS_SESSIONS, deep), Requirement(RequirementKind.TASKS_DONE_HARD, hard), Requirement(RequirementKind.ACTIVE_DAYS, days)),
                        unlockRank = maxOf(rank, region?.let(TrainerRank::rankToUnlock) ?: 0), dexRegion = region, dexShare = if (region == null) 0.0 else share,
                    )
                }
        }
    }

    private data class Tier(val deep: Int, val hard: Int, val days: Int, val rank: Int, val share: Double)

    private val byId: Map<String, Quest> by lazy { all.associateBy { it.id } }

    fun get(id: String): Quest? = byId[id]

    fun forSpecies(speciesId: Int): Quest? = all.firstOrNull { it.speciesId == speciesId }

    /** Why a quest cannot be taken yet, or null if it is available. */
    fun lockedReason(quest: Quest, data: AppData): String? {
        val rank = TrainerRank.rankFor(data.player.trainerPoints)
        if (rank < quest.unlockRank) return "Reach rank ${TrainerRank.names[quest.unlockRank]}"
        val region = quest.dexRegion
        if (region != null && quest.dexShare > 0) {
            val dex = Dex.regionDex(region).filter { !it.isSpecial }
            val share = if (dex.isEmpty()) 1.0 else dex.count { it.id in data.player.caught }.toDouble() / dex.size
            if (share < quest.dexShare) return "Catch ${(quest.dexShare * 100).toInt()}% of the ${region.label} (now ${(share * 100).toInt()}%)"
        }
        return null
    }

    /** Progress on one requirement, counted from the day [state] was accepted. */
    fun current(req: Requirement, state: QuestState, data: AppData, zone: TimeZone, streak: Int): Int {
        val since = state.acceptedAtMillis
        val sinceDay = since.toLocalDateTime(zone).date
        return when (req.kind) {
            RequirementKind.FOCUS_SESSIONS -> data.focusHistory.count { it.completed && it.startedAtMillis >= since }
            RequirementKind.DEEP_FOCUS_SESSIONS -> data.focusHistory.count { it.completed && it.deepFocus && it.startedAtMillis >= since }
            RequirementKind.FOCUS_MINUTES -> data.focusHistory.filter { it.completed && it.startedAtMillis >= since }.sumOf { it.minutes }
            RequirementKind.TASKS_DONE -> data.tasks.count { it.state == TaskState.DONE && it.paid && (it.completedAtMillis ?: 0) >= since }
            RequirementKind.TASKS_DONE_HARD -> data.tasks.count {
                it.state == TaskState.DONE && it.paid && (it.completedAtMillis ?: 0) >= since && (it.rank == Rank.B || it.rank == Rank.A)
            }
            RequirementKind.HABIT_DAYS -> data.habits.flatMap { it.completions }.toSet().count { it >= sinceDay }
            RequirementKind.WEEKLY_REVIEWS -> data.reviews.count { it.completedAtMillis >= since }
            RequirementKind.MILESTONES_DONE -> data.goals.flatMap { it.milestones }.count { it.rewarded && (it.doneAtMillis ?: 0) >= since }
            RequirementKind.ACTIVE_DAYS -> data.activeDays.count { it >= sinceDay }
            RequirementKind.STREAK_DAYS -> streak
        }
    }

    /** True when every requirement is met. */
    fun isComplete(quest: Quest, state: QuestState, data: AppData, zone: TimeZone, streak: Int): Boolean =
        quest.requirements.all { current(it, state, data, zone, streak) >= it.target }
}
