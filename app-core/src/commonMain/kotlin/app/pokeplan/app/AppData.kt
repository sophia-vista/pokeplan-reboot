package app.pokeplan.app

import app.pokeplan.world.WorldData
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
import app.pokeplan.core.pokemon.PerkBonuses
import app.pokeplan.core.pokemon.Perks
import app.pokeplan.plan.FocusRules
import app.pokeplan.plan.Streaks
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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
    val completedTrials: Set<String> = emptySet(),
    /** Emergency pauses used on [emergencyPausesDay]. */
    val emergencyPausesDay: LocalDate? = null,
    val emergencyPausesUsed: Int = 0,
    /** The task "Pick for me" chose; finishing it pays a small bonus. */
    val pickedTaskId: String? = null,
    /** The highest trainer rank whose "Rank up" card the player has seen. */
    val seenRank: Int = 0,
    /** The app version whose "What's new" the player has seen. Saves from before this existed count as 0.1.0. */
    val lastSeenVersion: String = "0.1.0",
    /** Pokémon the player starred: they cannot be transferred by accident. Stays on this device's save. */
    val favorites: Set<String> = emptySet(),
    /** The World game's save: teams, expeditions, finds, items and rank (docs/world.md). Client-side; stays on this device's save. */
    val world: WorldData = WorldData(),
    /** The Pokémon the player chose to help each Pokégear tool, by tool name to Pokémon id. A tool with none uses the best Pokémon owned who can help, else its loan. Cosmetic; stays on this device's save. */
    val gearHelpers: Map<String, String> = emptyMap(),
    /** The Pokégear tools that have a loaner from a Ranger request (it helps until an owned Pokémon can). Cosmetic; stays on this device's save. */
    val gearLoans: Set<String> = emptySet(),
    /** The list the Picker chooses from; never synced or sent anywhere (it is in [app.pokeplan.app.GameStore.DEVICE_ONLY], so the cloud document leaves it out and an adopted cloud save keeps this device's list). */
    val pickerOptions: List<String> = emptyList(),
    /** What the Pokégear tools remember: favourites, recents, currency choices, timers, the stopwatch, picker weights. Never synced (see [app.pokeplan.app.GameStore.DEVICE_ONLY]). */
    val gear: GearData = GearData(),
    /** The last exchange rates the Currency tool fetched, kept for offline use. Public reference data fetched again on demand, so it stays on this device (in [app.pokeplan.app.GameStore.DEVICE_ONLY]). */
    val gearRates: GearRates? = null,
    /** Where each Pokémon came from, by Pokémon id, for its detail page; Pokémon from before 0.51.0 have none. */
    val origins: Map<String, Origin> = emptyMap(),
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

/** One Pokégear timer: a [label], its [durationMs], and where it is. */
@Serializable
data class GearTimer(
    val id: String,
    val label: String,
    val durationMs: Long,
    /** While running, the clock time it ends at (milliseconds); 0 otherwise. */
    val endsAt: Long = 0,
    /** While paused, how much is left; -1 when it is not paused. */
    val pausedLeftMs: Long = -1,
    /** Set when it ran out, until the player dismisses it. */
    val finished: Boolean = false,
) {
    val running: Boolean get() = endsAt > 0 && !finished
    val paused: Boolean get() = pausedLeftMs >= 0 && !finished

    /** What is left at [now]: the whole duration when idle, the paused amount when paused, the time to the end when running (never below 0). */
    fun leftAt(now: Long): Long = when {
        finished -> 0
        running -> (endsAt - now).coerceAtLeast(0)
        paused -> pausedLeftMs
        else -> durationMs
    }
}

/** The Pokégear stopwatch: time gathered while stopped runs, and the laps (each the total at that moment). */
@Serializable
data class GearStopwatch(
    val accumulatedMs: Long = 0,
    /** The clock time the current run began at; 0 while stopped. */
    val runningSince: Long = 0,
    val laps: List<Long> = emptyList(),
) {
    val running: Boolean get() = runningSince > 0

    /** The elapsed time at [now]: what was gathered plus the current run (never counting a clock set back as time lost). */
    fun elapsedAt(now: Long): Long = accumulatedMs + if (running) (now - runningSince).coerceAtLeast(0) else 0
}

/** Everything the Pokégear tools remember apart from the helpers and the Picker's list. Device-only; every field has a default so older saves load. */
@Serializable
data class GearData(
    /** Favourite tools, by [app.pokeplan.core.pokemon.GearTool] name, and the ones used last (newest first). */
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    /** Whether the helper Pokémon is shown in full (false: a small strip that can be opened). */
    val helperCompact: Boolean = true,
    /** The currency amounts are shown in first, the ones on the board, and the pair last used. */
    val homeCurrency: String = "PHP",
    val currencyBoard: List<String> = listOf("USD", "JPY", "EUR", "KRW", "CNY"),
    val currencyPair: String = "",
    /** Rates the player entered (a money changer's quote), by currency code, in units of that currency per one unit of the home currency. */
    val manualRates: Map<String, Double> = emptyMap(),
    /** Percent and fixed fee for the "what it really costs" mode, kept for next time. */
    val feePercent: Double = 0.0,
    val feeFixed: Double = 0.0,
    /** Unit pairs the player starred (`category:from>to`), the last category, and how many decimals results show. */
    val unitFavorites: List<String> = emptyList(),
    val unitCategory: String = "length",
    val unitDecimals: Int = 6,
    /** The calculator's history (kept only when [calcHistoryOn]), its memory slot and whether angles are in degrees. */
    val calcHistory: List<String> = emptyList(),
    val calcHistoryOn: Boolean = false,
    val calcMemory: Double = 0.0,
    val calcDegrees: Boolean = true,
    val timers: List<GearTimer> = emptyList(),
    val stopwatch: GearStopwatch = GearStopwatch(),
    /** Weights of the Picker's options (an option with none weighs 1) and what it chose lately (kept only when [pickerHistoryOn]). */
    val pickerWeights: Map<String, Int> = emptyMap(),
    val pickerHistory: List<String> = emptyList(),
    val pickerHistoryOn: Boolean = false,
    val diceHistory: List<String> = emptyList(),
    /** The screen light: colour name, brightness 0.1 to 1. */
    val lightColor: String = "white",
    val lightBrightness: Float = 1f,
    /** The World clock's places (zone ids, in order; empty: the first time it opens it shows a few) and the pressure at sea level the Barometer uses for height, in hPa. */
    val clockZones: List<String> = emptyList(),
    val seaLevelHpa: Float = 1013.25f,
    /** The Waypoints tool's saved places (kept on this phone only, never in the cloud save), newest first. */
    val waypoints: List<Waypoint> = emptyList(),
    /** The Sound meter's offset: decibels added to the level below full scale to estimate a sound level (a phone microphone is not calibrated). */
    val soundOffsetDb: Float = 90f,
) {
    companion object {
        const val MAX_RECENTS = 6
        const val MAX_HISTORY = 30
    }
}

/** A place saved from the Waypoints tool: where the phone was, how sure it was, and when. */
@Serializable
data class Waypoint(val id: String, val name: String, val latitude: Double, val longitude: Double, val accuracyMeters: Float, val atMillis: Long) {
    companion object { const val MAX = 30 }
}

/**
 * Exchange rates: units of each currency for one US dollar, on [date] (`2026-10-31`), as the rates service reported them (normalised to the
 * dollar whatever base the service used). [fetchedAtMillis] is when this device got them (0 for a cache from before it was kept) and [source]
 * says where they are from. These are reference rates, not what a bank, card or money changer gives.
 */
@Serializable
data class GearRates(val date: String, val perUsd: Map<String, Double>, val fetchedAtMillis: Long = 0, val source: String = "")

@Serializable
data class Settings(
    val region: Region = Region.PLAN,
    val defaultFocusMinutes: Int = 25,
    val deepFocus: Boolean = false,
    /** Master switch kept from the old Sound toggle; the sliders turn it on again. */
    val sound: Boolean = true,
    /** Volume of sound effects, 0 to 100 (and of cries too until [criesVolume] is set). */
    val effectsVolume: Int = 100,
    /** Volume of the Pokémon's cries, 0 to 100; -1 follows [effectsVolume] (every save from before it existed). */
    val criesVolume: Int = -1,
    /** Volume of music, 0 to 100. There is no music yet; the setting is kept for when there is. */
    val musicVolume: Int = 70,
    val reduceMotion: Boolean = false,
    /** A short vibration when the buddy is petted or fed (Android). */
    val haptics: Boolean = true,
    val weekStartsOnMonday: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** The page behind every screen: "day" (the time-of-day gradient), or the name of a scene or dungeon town (a veil keeps the text readable). */
    val appBackground: String = "day",
    /** Task reminders as notifications (Android). */
    val reminders: Boolean = true,
    /** Reminders that fall in these hours wait for their end. */
    val quietHours: QuietHours = QuietHours(),
    /** An unanswered task reminder repeats up to three times, ten minutes apart (Android). */
    val repeatReminders: Boolean = true,
    /** A quiet "halfway there" notification in focus sessions of 15 minutes or more (Android). */
    val focusHalfway: Boolean = true,
    /** A notification when a focus session's time is up (Android). */
    val focusEnd: Boolean = true,
    /** A notification when a World job is done, so there is something to claim (Android). */
    val worldJobAlerts: Boolean = true,
    /** An evening reminder when nothing has counted today and a streak is running (Android). */
    val streakReminder: Boolean = true,
    /** The hour of that reminder, local time. */
    val streakReminderHour: Int = 20,
    /** A recap of the week on Sunday evening (Android). */
    val weeklyRecap: Boolean = true,
    /** The Care module in Modules (docs/ecosystem.md, section 6). Off only hides it: Care's own save is kept. */
    val careModule: Boolean = true,
    /** World's expedition boxes scroll their floor while the team walks. */
    val worldScroll: Boolean = true,
    /** Pokémon in a module's party (Plan's, Care's...) stay out of World teams, so a module's party is never taken away for exploring. */
    val worldKeepPartyOut: Boolean = false,
) {
    /** How loud sound effects play, 0 to 1 (0 when Sound was switched off). */
    val effectsLevel: Float get() = if (sound) effectsVolume / 100f else 0f

    /** How loud the Pokémon's cries play, 0 to 1 (0 when Sound was switched off). */
    val criesLevel: Float get() = if (sound) (if (criesVolume < 0) effectsVolume else criesVolume) / 100f else 0f

    /** The first day of the week the player chose. */
    val weekStart: DayOfWeek get() = if (weekStartsOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

    /** These settings with every number in its range. The store applies it to everything that comes in (a change, a save, the cloud). */
    fun clamped(): Settings = copy(
        defaultFocusMinutes = defaultFocusMinutes.coerceIn(FocusRules.RETRY_MINUTES, FocusRules.MAX_MINUTES),
        effectsVolume = effectsVolume.coerceIn(0, 100),
        criesVolume = criesVolume.coerceIn(-1, 100),
        musicVolume = musicVolume.coerceIn(0, 100),
        streakReminderHour = streakReminderHour.coerceIn(0, 23),
    )
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

/** How a Pokémon joined the collection, and in which region when it hatched or was befriended there. */
@Serializable
data class Origin(val how: How, val region: Region? = null) {
    enum class How(val label: String) {
        STARTER("Your first partner"),
        FOCUS_EGG("Hatched from a Focus Egg"),
        TASK_EGG("Hatched from a Task Egg"),
        CARE_EGG("Hatched from a Care Egg"),
        BEFRIENDED("Befriended on a Care visit"),
        QUEST("Earned on a quest"),
        WORLD("Found in the World"),
    }
}

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

/** The perks of the buddy, its party and the slotted Guardians as they stand now (no task or session in view). */
val AppData.perks: PerkBonuses get() = player.let { Perks.total(it.buddy, it.party, guardians = it.guardianPokemon) }

/**
 * The daily streak at [now], wherever it is shown (the app, the evening reminder): the day turns over at
 * [Streaks.ROLLOVER_HOUR], and the perks forgive more missed days.
 */
fun AppData.streakAt(now: LocalDateTime): Streaks.Result =
    Streaks.daily(activeDays, restDays, Streaks.streakDay(now), perks.extraForgivenDays, settings.weekStart)
