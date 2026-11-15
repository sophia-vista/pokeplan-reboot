package app.pokeplan.plan

import kotlinx.serialization.Serializable
import app.pokeplan.core.pokemon.Items

@Serializable
data class Milestone(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val doneAtMillis: Long? = null,
    /** Already paid; never pays twice. */
    val rewarded: Boolean = false,
) {
    val isDone: Boolean get() = doneAtMillis != null
}

/** One Pokémon of the Plan party on the day a goal was finished, as the Hall of Fame row remembers it (display only). */
@Serializable
data class GoalPartyMember(val formId: Int, val level: Int, val shiny: Boolean = false)

/** One slot of a goal's badge case: which Gym badge picture it holds ([badgeId], `assets/badges/N.png`) and whether the milestone behind it is done. */
data class BadgeSlot(val badgeId: Int, val earned: Boolean)

/** A long-term aim ("Learn guitar") above tasks and habits. */
@Serializable
data class Goal(
    val id: String,
    val title: String,
    val category: Category = Category.PERSONAL,
    val targetDate: kotlinx.datetime.LocalDate? = null,
    val milestones: List<Milestone> = emptyList(),
    val linkedTaskIds: Set<String> = emptySet(),
    val linkedHabitIds: Set<String> = emptySet(),
    val listId: String? = null,
    val createdAtMillis: Long,
    val completedAtMillis: Long? = null,
    val bonusGiven: Boolean = false,
    /** The Plan party (lead first) on the day the goal was finished; the Hall of Fame row shows it. Empty for a goal finished before 0.197.0. */
    val hallOfFameParty: List<GoalPartyMember> = emptyList(),
) {
    init {
        require(title.isNotBlank()) { "a goal needs a title" }
    }
}

/** What finishing a milestone did. */
data class MilestoneResult(
    val goal: Goal,
    /** A Super Candy is due. */
    val pays: Boolean,
    val goalCompleted: Boolean,
    /** An item for finishing the whole goal, given once. */
    val bonusItem: String?,
)

object GoalRules {
    /** A milestone must exist this long before finishing it pays, so one cannot be made and ticked at once. */
    const val MILESTONE_MIN_AGE_MILLIS = 24L * 60 * 60 * 1000

    /** Milestone rewards stop after this many a day. *(tune)* */
    const val MAX_MILESTONE_REWARDS_PER_DAY = 3

    const val SUPER_CANDIES_PER_MILESTONE = 1

    /** The bonus for finishing a goal. *(tune)* */
    const val GOAL_BONUS_ITEM = Items.NATURE_MINT

    /** Gym badge pictures come in sets of this many (Kanto 1 to 8, Johto 9 to 16, Hoenn 17 to 24, Sinnoh 25 to 32, Unova 33 to 40). */
    const val BADGES_PER_SET = 8
    const val BADGE_SETS = 5

    /** The set a goal draws its badges from, 0 until [BADGE_SETS]: fixed by the goal's id, so it never changes. */
    fun badgeSet(goalId: String): Int = goalId.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7fffffff } % BADGE_SETS

    /** The badge picture number for the milestone at [index] of a goal: the set's badges in order, repeating after eight. */
    fun badgeId(goalId: String, index: Int): Int = badgeSet(goalId) * BADGES_PER_SET + index % BADGES_PER_SET + 1

    /** The goal's case: one slot per milestone, filled when it is done; a goal with no milestones has one slot, filled when the goal is finished. */
    fun badgeCase(goal: Goal): List<BadgeSlot> =
        if (goal.milestones.isEmpty()) listOf(BadgeSlot(badgeId(goal.id, 0), goal.completedAtMillis != null))
        else goal.milestones.mapIndexed { i, m -> BadgeSlot(badgeId(goal.id, i), m.isDone) }

    /** The finished goals, newest first: the Hall of Fame. */
    fun hallOfFame(goals: List<Goal>): List<Goal> =
        goals.filter { it.completedAtMillis != null }.sortedByDescending { it.completedAtMillis }

    /** Progress 0..1: finished milestones, or, with none, finished linked tasks. */
    fun progress(goal: Goal, tasks: List<Task>): Double {
        if (goal.milestones.isNotEmpty()) return goal.milestones.count { it.isDone }.toDouble() / goal.milestones.size
        val linked = tasks.filter { it.id in goal.linkedTaskIds && it.state != TaskState.DELETED && it.state != TaskState.WONT_DO }
        return if (linked.isEmpty()) 0.0 else linked.count { it.state == TaskState.DONE }.toDouble() / linked.size
    }

    fun addMilestone(goal: Goal, milestone: Milestone): Goal =
        goal.copy(milestones = goal.milestones + milestone, completedAtMillis = null)

    /**
     * [rewardedToday] is how many milestone rewards the player has already had today; [party] is the Plan party now, kept on the goal
     * if this milestone finishes it (the Hall of Fame shows who was with the player that day).
     */
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

    /** Un-ticks a milestone. It stays marked as rewarded, so ticking it again never pays twice. */
    fun reopenMilestone(goal: Goal, milestoneId: String): Goal = goal.copy(
        milestones = goal.milestones.map { if (it.id == milestoneId) it.copy(doneAtMillis = null) else it },
        completedAtMillis = null,
    )
}
