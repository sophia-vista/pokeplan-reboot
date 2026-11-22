package app.pokeplan.plan

import kotlinx.serialization.Serializable
import app.pokeplan.core.pokemon.Items

@Serializable
data class Milestone(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val doneAtMillis: Long? = null,
    val rewarded: Boolean = false,
) {
    val isDone: Boolean get() = doneAtMillis != null
}

@Serializable
data class GoalPartyMember(val formId: Int, val level: Int, val shiny: Boolean = false)

data class BadgeSlot(val badgeId: Int, val earned: Boolean)

@Serializable
data class Goal(
    val id: String,
    val title: String,
    val targetDate: kotlinx.datetime.LocalDate? = null,
    val milestones: List<Milestone> = emptyList(),
    val linkedTaskIds: Set<String> = emptySet(),
    val linkedHabitIds: Set<String> = emptySet(),
    val listId: String? = null,
    val createdAtMillis: Long,
    val completedAtMillis: Long? = null,
    val bonusGiven: Boolean = false,
    val hallOfFameParty: List<GoalPartyMember> = emptyList(),
) {
    init {
        require(title.isNotBlank()) { "a goal needs a title" }
    }
}

data class MilestoneResult(
    val goal: Goal,
    val pays: Boolean,
    val goalCompleted: Boolean,
    val bonusItem: String?,
)

object GoalRules {
    const val MILESTONE_MIN_AGE_MILLIS = 24L * 60 * 60 * 1000

    const val MAX_MILESTONE_REWARDS_PER_DAY = 3

    const val SUPER_CANDIES_PER_MILESTONE = 1

    const val GOAL_BONUS_ITEM = Items.NATURE_MINT

    const val BADGES_PER_SET = 8
    const val BADGE_SETS = 5

    fun badgeSet(goalId: String): Int = goalId.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7fffffff } % BADGE_SETS

    fun badgeId(goalId: String, index: Int): Int = badgeSet(goalId) * BADGES_PER_SET + index % BADGES_PER_SET + 1

    fun badgeCase(goal: Goal): List<BadgeSlot> =
        if (goal.milestones.isEmpty()) listOf(BadgeSlot(badgeId(goal.id, 0), goal.completedAtMillis != null))
        else goal.milestones.mapIndexed { i, m -> BadgeSlot(badgeId(goal.id, i), m.isDone) }

    fun hallOfFame(goals: List<Goal>): List<Goal> =
        goals.filter { it.completedAtMillis != null }.sortedByDescending { it.completedAtMillis }

    fun progress(goal: Goal, tasks: List<Task>): Double {
        if (goal.milestones.isNotEmpty()) return goal.milestones.count { it.isDone }.toDouble() / goal.milestones.size
        val linked = tasks.filter { it.id in goal.linkedTaskIds && it.state != TaskState.DELETED && it.state != TaskState.WONT_DO }
        return if (linked.isEmpty()) 0.0 else linked.count { it.state == TaskState.DONE }.toDouble() / linked.size
    }

    fun addMilestone(goal: Goal, milestone: Milestone): Goal =
        goal.copy(milestones = goal.milestones + milestone, completedAtMillis = null)

    fun completeMilestone(goal: Goal, milestoneId: String, nowMillis: Long, rewardedToday: Int, party: List<GoalPartyMember> = emptyList()): MilestoneResult {
        val milestone = goal.milestones.first { it.id == milestoneId }
        check(!milestone.isDone) { "that milestone is already done" }
        val pays = !milestone.rewarded && rewardedToday < MAX_MILESTONE_REWARDS_PER_DAY &&
            nowMillis - milestone.createdAtMillis >= MILESTONE_MIN_AGE_MILLIS
        val milestones = goal.milestones.map {
            if (it.id == milestoneId) it.copy(doneAtMillis = nowMillis, rewarded = it.rewarded || pays) else it
        }
        val finished = milestones.all { it.isDone }
        val bonus = finished && !goal.bonusGiven
        val updated = goal.copy(
            milestones = milestones,
            completedAtMillis = if (finished) nowMillis else null,
            bonusGiven = goal.bonusGiven || bonus,
            hallOfFameParty = if (finished && goal.completedAtMillis == null) party else goal.hallOfFameParty,
        )
        return MilestoneResult(updated, pays, finished, if (bonus) GOAL_BONUS_ITEM else null)
    }

    fun reopenMilestone(goal: Goal, milestoneId: String): Goal = goal.copy(
        milestones = goal.milestones.map { if (it.id == milestoneId) it.copy(doneAtMillis = null) else it },
        completedAtMillis = null,
    )
}
