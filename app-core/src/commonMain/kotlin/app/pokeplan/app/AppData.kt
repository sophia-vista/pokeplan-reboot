package app.pokeplan.app

import app.pokeplan.world.WorldData
import app.pokeplan.core.dex.Region
import app.pokeplan.core.reward.PlayerState
import app.pokeplan.core.reward.Reward
import app.pokeplan.plan.FailReason
import app.pokeplan.plan.FocusRecord
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Goal
import app.pokeplan.plan.FrequencyTarget
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppData(
    val schema: Int = SCHEMA,
    val trainerName: String = "",
    val onboarded: Boolean = false,
    val player: PlayerState = PlayerState(),
    val settings: Settings = Settings(),
    val tasks: List<Task> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val events: List<PlanEvent> = emptyList(),
    val series: List<TaskSeries> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val legacyHabits: List<Habit> = emptyList(),
    val habitsMergedAtMillis: Long? = null,
    val goals: List<Goal> = emptyList(),
    val reviews: List<WeeklyReview> = emptyList(),
    val restDays: Set<LocalDate> = emptySet(),
    val activeDays: Set<LocalDate> = emptySet(),
    val focusHistory: List<FocusRecord> = emptyList(),
    val focus: FocusSession? = null,
    val lastFocusEnd: FocusEnd? = null,
    val pending: List<PendingCompletion> = emptyList(),
    val rewardLog: List<RewardEntry> = emptyList(),
    val quests: Map<String, QuestState> = emptyMap(),
    val completedTrials: Set<String> = emptySet(),
    val stepWalks: Map<String, StepWalk> = emptyMap(),
    val emergencyPausesDay: LocalDate? = null,
    val emergencyPausesUsed: Int = 0,
    val pickedTaskId: String? = null,
    val seenRank: Int = 0,
    val lastSeenVersion: String = "0.1.0",
    val favorites: Set<String> = emptySet(),
    val world: WorldData = WorldData(),
    val gearHelpers: Map<String, String> = emptyMap(),
    val pickerOptions: List<String> = emptyList(),
    val gear: GearData = GearData(),
    val gearRates: GearRates? = null,
    val origins: Map<String, Origin> = emptyMap(),
    val shinySpecies: Set<Int> = emptySet(),
    val caughtForms: Set<Int> = emptySet(),
    val shinyForms: Set<Int> = emptySet(),
    val seenSpecies: Set<Int> = emptySet(),
    val counters: Map<String, Int> = emptyMap(),
    val shownCalendars: Set<Long> = emptySet(),
    val pokeMigration: PokeMigration? = null,
    val placesVersion: Int = 0,
    val tutorials: Set<String> = emptySet(),
    val healPaid: List<String> = emptyList(),
    val mintPaid: List<String> = emptyList(),
    val outbreaks: List<app.pokeplan.core.reward.Outbreak> = emptyList(),
    @SerialName("homePaid") val basePaid: List<String> = emptyList(),
    val linkPaid: List<String> = emptyList(),
    val ledgerNotes: List<LedgerNote> = emptyList(),
    val decorations: List<String> = emptyList(),
    val decorationStarters: Boolean = false,
) {
    companion object {
        const val SCHEMA = 1
    }
}

@Serializable
data class LedgerNote(
    val eventId: String,
    val module: String,
    val ref: String,
    val kind: String,
    val atMillis: Long,
    val poke: Long = 0,
    val items: Map<String, Long> = emptyMap(),
)

@Serializable
data class PokeMigration(val legacyCoins: Int, val legacyWorldPoke: Int, val migratedAtMillis: Long, val rulesVersion: Int)

object Tutorials {
    const val PLAN_FOCUS = "plan-focus"

    const val CARE_BREATH = "care-breath"

    const val HEAL_WALK = "heal-walk"

    const val MINT_TALLY = "mint-tally"

    const val BASE_TIDY = "home-tidy"

    const val LINK_POSTCARD = "link-postcard"

    const val DEMO_SECONDS = 5
}

@Serializable
enum class ThemeMode(val label: String) { SYSTEM("Match phone"), LIGHT("Light"), DARK("Dark") }

@Serializable
data class GearTimer(
    val id: String,
    val label: String,
    val durationMs: Long,
    val endsAt: Long = 0,
    val pausedLeftMs: Long = -1,
    val finished: Boolean = false,
) {
    val running: Boolean get() = endsAt > 0 && !finished
    val paused: Boolean get() = pausedLeftMs >= 0 && !finished

    fun leftAt(now: Long): Long = when {
        finished -> 0
        running -> (endsAt - now).coerceAtLeast(0)
        paused -> pausedLeftMs
        else -> durationMs
    }
}

@Serializable
data class GearStopwatch(
    val accumulatedMs: Long = 0,
    val runningSince: Long = 0,
    val laps: List<Long> = emptyList(),
) {
    val running: Boolean get() = runningSince > 0

    fun elapsedAt(now: Long): Long = accumulatedMs + if (running) (now - runningSince).coerceAtLeast(0) else 0
}

@Serializable
data class GearData(
    val favorites: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    val homeCurrency: String = "PHP",
    val currencyBoard: List<String> = listOf("USD", "JPY", "EUR", "KRW", "CNY"),
    val manualRates: Map<String, Double> = emptyMap(),
    val feePercent: Double = 0.0,
    val feeFixed: Double = 0.0,
    val unitFavorites: List<String> = emptyList(),
    val unitCategory: String = "length",
    val unitDecimals: Int = 6,
    val calcHistory: List<String> = emptyList(),
    val calcHistoryOn: Boolean = false,
    val calcMemory: Double = 0.0,
    val calcDegrees: Boolean = true,
    val timers: List<GearTimer> = emptyList(),
    val stopwatch: GearStopwatch = GearStopwatch(),
    val pickerWeights: Map<String, Int> = emptyMap(),
    val pickerHistory: List<String> = emptyList(),
    val pickerHistoryOn: Boolean = false,
    val diceHistory: List<String> = emptyList(),
    val diceHistoryOn: Boolean = false,
    val lightColor: String = "white",
    val lightBrightness: Float = 1f,
    val clockZones: List<String> = emptyList(),
    val seaLevelHpa: Float = 1013.25f,
    val waypoints: List<Waypoint> = emptyList(),
    val soundOffsetDb: Float = 90f,
) {
    companion object {
        const val MAX_RECENTS = 6
        const val MAX_HISTORY = 30
    }
}

@Serializable
data class Waypoint(val id: String, val name: String, val latitude: Double, val longitude: Double, val accuracyMeters: Float, val atMillis: Long) {
    companion object { const val MAX = 30 }
}

@Serializable
data class GearRates(val date: String, val perUsd: Map<String, Double>, val fetchedAtMillis: Long = 0, val source: String = "")

@Serializable
data class Settings(
    val region: Region = Region.PLAN,
    val defaultFocusMinutes: Int = 25,
    val deepFocus: Boolean = false,
    val sound: Boolean = true,
    val effectsVolume: Int = 100,
    val criesVolume: Int = -1,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val weekStartsOnMonday: Boolean = true,
    val dateFormat: String = "SHORT",
    val clock12Hour: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val appBackground: String = "day",
    val reminders: Boolean = true,
    val quietHours: QuietHours = QuietHours(),
    val repeatReminders: Boolean = true,
    val focusHalfway: Boolean = true,
    val focusEnd: Boolean = true,
    val worldJobAlerts: Boolean = true,
    val lockedModules: Set<String> = emptySet(),
    val streakReminder: Boolean = true,
    val streakReminderHour: Int = 20,
    val weeklyRecap: Boolean = true,
    val worldScroll: Boolean = true,
    val defaultReward: app.pokeplan.core.reward.RewardFocus = app.pokeplan.core.reward.RewardFocus.EXP
) {
    val effectsLevel: Float get() = if (sound) effectsVolume / 100f else 0f

    val criesLevel: Float get() = if (sound) (if (criesVolume < 0) effectsVolume else criesVolume) / 100f else 0f

    val weekStart: DayOfWeek get() = if (weekStartsOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

    fun clamped(): Settings = copy(
        defaultFocusMinutes = defaultFocusMinutes.coerceIn(FocusRules.RETRY_MINUTES, FocusRules.MAX_MINUTES),
        effectsVolume = effectsVolume.coerceIn(0, 100),
        criesVolume = criesVolume.coerceIn(-1, 100),
        streakReminderHour = streakReminderHour.coerceIn(0, 23),
    )
}

@Serializable
data class TaskSeries(
    val template: Task,
    val recurrence: Recurrence? = null,
    val firstTaskId: String? = null,
    val target: FrequencyTarget? = null,
    val createdOn: LocalDate? = null,
    val completions: Set<LocalDate> = emptySet(),
    val paidOn: LocalDate? = null,
) {
    init {
        require((recurrence != null) != (target != null)) { "a recurring task has a calendar rule or a frequency target, not both" }
    }

    val isFrequency: Boolean get() = target != null

    fun asHabit(): Habit? = target?.let { Habit(template.id, template.title, template.rank, it, createdOn ?: LocalDate(1970, 1, 1), completions, paidOn) }

    companion object {
        fun of(h: Habit, createdAtMillis: Long): TaskSeries = TaskSeries(
            template = Task(id = h.id, seriesId = h.id, title = h.title, rank = h.rank, createdAtMillis = createdAtMillis),
            target = h.target, createdOn = h.createdOn, completions = h.completions, paidOn = h.paidOn,
        )
    }
}

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

@Serializable
data class VisitorOrigin(val speciesId: Int, val visitorId: String, val epochDay: Long, val inherited: List<String> = emptyList(), val note: String = "")

@Serializable
data class Origin(val how: How, val region: Region? = null, val visitor: VisitorOrigin? = null) {
    enum class How(val label: String) {
        STARTER("Your first partner"),
        FOCUS_EGG("Hatched from a Focus Egg"),
        TASK_EGG("Hatched from a Task Egg"),
        CARE_EGG("Arrived through Care"),
        FIELD_EGG("Found by Field Research"),
        LEDGER_EGG("Found by Ledger Research"),
        HABITAT_EGG("Found by Habitat Discovery"),
        POSTCARD_EGG("Found by Postcard Trail"),
        SAFARI("Found in a Friend Safari"),
        BEFRIENDED("Befriended on a Care visit"),
        QUEST("Earned on a quest"),
        WORLD("Found in the World"),
    }
}

@Serializable
data class RewardEntry(
    val atMillis: Long,
    val source: String,
    val rewards: List<Reward>,
    val seen: Boolean = false,
)

@Serializable
data class FocusEnd(
    val atMillis: Long,
    val completed: Boolean,
    val failReason: FailReason? = null,
    val guarded: Boolean = false,
    val minutes: Int = 0,
    val plannedMinutes: Int = 0,
    val taskId: String? = null,
    val deepFocus: Boolean = false,
)


@Serializable
data class QuestState(val acceptedAtMillis: Long, val completedAtMillis: Long? = null)

fun AppData.upcomingReminders(nowMillis: Long, zone: TimeZone): List<DueReminder> {
    if (!settings.reminders) return emptyList()
    val focusEnd = focus?.takeIf { !it.isFinished }?.let { it.startedAtMillis + it.durationMillis + it.pausedMillis }
    return Reminders.upcoming(tasks, nowMillis, zone, settings.quietHours, restDays, focusEnd)
}

enum class DexState(val label: String) {
    OWNED("Owned"),
    CAUGHT("Caught"),
    SEEN("Seen"),
    UNKNOWN("Not found yet");

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

val AppData.perks: PerkBonuses get() = player.let { Perks.total(it.planLead, it.party, guardians = listOfNotNull(it.guardianOf(app.pokeplan.core.pokemon.PartyIds.PLAN))) }

fun AppData.streakAt(now: LocalDateTime): Streaks.Result =
    Streaks.daily(activeDays, restDays, Streaks.streakDay(now), perks.extraForgivenDays, settings.weekStart)

@Serializable
data class StepWalk(val lastCounter: Long, val walked: Int = 0) {
    fun reading(counter: Long): StepWalk = copy(lastCounter = counter, walked = walked + (if (counter >= lastCounter) (counter - lastCounter) else counter).toInt().coerceAtLeast(0))
}
