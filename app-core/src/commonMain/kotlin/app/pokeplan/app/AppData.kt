package app.pokeplan.app

import app.pokeplan.core.dex.Region
import app.pokeplan.core.reward.PlayerState
import app.pokeplan.core.reward.Reward
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusRecord
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Goal
import app.pokeplan.plan.Habit
import app.pokeplan.plan.QuietHours
import app.pokeplan.plan.DueReminder
import app.pokeplan.plan.Reminders
import app.pokeplan.plan.Recurrence
import app.pokeplan.plan.PlanEvent
import app.pokeplan.plan.Task
import app.pokeplan.plan.TaskList
import app.pokeplan.plan.WeeklyReview
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

/** Everything the app saves on the device. The whole object is stored as one JSON document. */
@Serializable
data class AppData(
    val schema: Int = SCHEMA,
    val trainerName: String = "",
    val onboarded: Boolean = false,
    /** The Pokémon side: collection, bag, coins, Egg meter, trainer points. */
    val player: PlayerState = PlayerState(),
    val settings: Settings = Settings(),
    val tasks: List<Task> = emptyList(),
    /** The player's own task lists (TickTick's lists); a task in none is in the Inbox. */
    val lists: List<TaskList> = emptyList(),
    /** Events on PokéPlan's own calendar: never paid, synced with the rest of the save. */
    val events: List<PlanEvent> = emptyList(),
    val series: List<TaskSeries> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val reviews: List<WeeklyReview> = emptyList(),
    val restDays: Set<LocalDate> = emptySet(),
    /** Days with a paid task or a finished session: they keep the streak going. */
    val activeDays: Set<LocalDate> = emptySet(),
    val focusHistory: List<FocusRecord> = emptyList(),
    /** The session in progress, if any. */
    val focus: FocusSession? = null,
    /** How the last focus session ended, until the player has seen it. */
    val lastFocusEnd: FocusEnd? = null,
    /** Completions waiting out their 10 second undo window. */
    val pending: List<PendingCompletion> = emptyList(),
    /** What recent rewards were, newest last; the UI shows the unseen ones. */
    val rewardLog: List<RewardEntry> = emptyList(),
    /** The signed-in cloud account, if any. */
    val account: Account? = null,
    /** Legendary and mythical quests the player has accepted, by quest id. */
    val quests: Map<String, QuestState> = emptyMap(),
    /** Evolution trials already completed. */
    val completedTrials: Set<String> = emptySet(),
    /** Emergency pauses used on [emergencyPausesDay]. */
    val emergencyPausesDay: LocalDate? = null,
    val emergencyPausesUsed: Int = 0,
    /** The highest trainer rank whose "Rank up" card the player has seen. */
    val seenRank: Int = 0,
    /** The app version whose "What's new" the player has seen. Saves from before this existed count as 0.1.0. */
    val lastSeenVersion: String = "0.1.0",
    /** Pokémon the player starred: they cannot be transferred by accident. Stays on this device's save. */
    val favorites: Set<String> = emptySet(),
    /** Species the player has ever owned as a shiny, kept even after the shiny is transferred. */
    val shinySpecies: Set<Int> = emptySet(),
    /** Form ids the player has ever had (see [FormDex]), kept after the Pokémon is transferred. */
    val caughtForms: Set<Int> = emptySet(),
    /** Form ids the player has ever had as a shiny. */
    val shinyForms: Set<Int> = emptySet(),
    /** Species seen but maybe never caught: a friend's buddy or a quest's Pokémon. Kept once seen, like caught. */
    val seenSpecies: Set<Int> = emptySet(),
    /** Rare Candy rewards from subtasks and the like are counted per day here when a cap needs them. */
    val counters: Map<String, Int> = emptyMap(),
    /** Egg tickets for hatching offline; kept on this device only. */
    val eggTickets: EggTickets = EggTickets(),
    /** Which of this phone's calendars show on the day timeline (calendar ids differ per device, so this stays here). */
    val shownCalendars: Set<Long> = emptySet(),
) {
    companion object {
        const val SCHEMA = 1
    }
}

/** Which colours the app uses: the phone's choice, or always light or dark. */
@Serializable
enum class ThemeMode(val label: String) { SYSTEM("Match phone"), LIGHT("Light"), DARK("Dark") }

@Serializable
data class Settings(
    val region: Region = Region.HOME,
    val defaultFocusMinutes: Int = 25,
    val deepFocus: Boolean = false,
    /** Master switch kept from the old Sound toggle; the sliders turn it on again. */
    val sound: Boolean = true,
    /** Volume of cries and sound effects, 0 to 100. */
    val effectsVolume: Int = 100,
    /** Volume of music, 0 to 100. There is no music yet; the setting is kept for when there is. */
    val musicVolume: Int = 70,
    val reduceMotion: Boolean = false,
    /** A short vibration when the buddy is petted or fed (Android). */
    val haptics: Boolean = true,
    val weekStartsOnMonday: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Task reminders as notifications (Android). */
    val reminders: Boolean = true,
    /** Reminders that fall in these hours wait for their end. */
    val quietHours: QuietHours = QuietHours(),
) {
    /** How loud cries and effects play, 0 to 1 (0 when Sound was switched off). */
    val effectsLevel: Float get() = if (sound) effectsVolume.coerceIn(0, 100) / 100f else 0f
}

/**
 * A repeating task: the template its instances are copied from, and when they fall. [firstTaskId] is the plain task the
 * player turned into a repeating one: it stays the task of its own day and is not an instance, so it keeps the
 * minimum-age rule, but it still shows that it repeats.
 */
@Serializable
data class TaskSeries(val template: Task, val recurrence: Recurrence, val firstTaskId: String? = null)

/** A finished task that may still be undone. [original] is what to restore. */
@Serializable
data class PendingCompletion(
    val original: Task,
    val done: Task,
    val pays: Boolean,
    val tier: Int,
    val onTime: Boolean,
    val focusedOnIt: Boolean,
    val finalAtMillis: Long,
)

/** A batch of rewards from one source ("Task: Write report"). */
@Serializable
data class RewardEntry(
    val atMillis: Long,
    val source: String,
    val rewards: List<Reward>,
    val seen: Boolean = false,
)

/** The end of a focus session: finished (the egg hatched) or failed for [failReason]; or both, see [guarded]. */
@Serializable
data class FocusEnd(
    val atMillis: Long,
    val completed: Boolean,
    val failReason: FailReason? = null,
    /** The session failed, but the buddy's Magic Guard kept its egg (so [completed] is true and [failReason] says why). */
    val guarded: Boolean = false,
)

/**
 * A signed-in cloud account and where this device stands with it. [version] is the cloud save version this device last saw;
 * [unsynced] is true while there are changes the cloud does not have yet.
 */
@Serializable
data class Account(
    val email: String,
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val version: Int = 0,
    val unsynced: Boolean = true,
    val syncedAtMillis: Long = 0,
)

/** A quest the player has taken on. Progress is counted from [acceptedAtMillis]; [completedAtMillis] is set when the reward is claimed. */
@Serializable
data class QuestState(val acceptedAtMillis: Long, val completedAtMillis: Long? = null)

/** Every reminder still to come after [nowMillis], earliest first; none when reminders are switched off. */
fun AppData.upcomingReminders(nowMillis: Long, zone: TimeZone): List<DueReminder> {
    if (!settings.reminders) return emptyList()
    val focusEnd = focus?.takeIf { !it.isFinished }?.let { it.startedAtMillis + it.durationMillis + it.pausedMillis }
    return Reminders.upcoming(tasks, nowMillis, zone, settings.quietHours, restDays, focusEnd)
}

/** Where a species stands in the Pokédex, from the strongest claim down. */
enum class DexState(val label: String) {
    /** In the party or the PC now. */
    OWNED("Owned"),
    /** Caught once and since transferred: caught is permanent. */
    CAUGHT("Caught"),
    /** Seen on a friend's buddy or in a quest, never caught. */
    SEEN("Seen"),
    UNKNOWN("Not found yet");

    /** True when the species has ever been caught, so its entry can be read. */
    val caught: Boolean get() = this == OWNED || this == CAUGHT

    companion object {
        fun of(data: AppData, speciesId: Int): DexState = when {
            data.player.pokemon.any { it.speciesId == speciesId } -> OWNED
            speciesId in data.player.caught -> CAUGHT
            speciesId in data.seenSpecies -> SEEN
            else -> UNKNOWN
        }
    }
}
