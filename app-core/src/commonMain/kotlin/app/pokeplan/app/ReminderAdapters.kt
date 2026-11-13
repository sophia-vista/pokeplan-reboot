package app.pokeplan.app

import app.pokeplan.care.CareData
import app.pokeplan.plan.Streaks
import app.pokeplan.plan.Task
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/** The stable action keys a notification may carry (INTEGRATIONS_AND_PRIVACY.md, section 9.6.2). */
object ReminderActions {
    const val DONE = "done"
    const val SNOOZE = "snooze"
    const val DISMISS = "dismiss"
    // Plan's snoozes and Care's, each offered by its own module only (section 9.7).
    const val SNOOZE_10 = "snooze10"
    const val SNOOZE_60 = "snooze60"
    const val TOMORROW = "tomorrow"
    const val SNOOZE_30 = "snooze30"
}

/** How long each module's snooze lasts (section 9.7: defined by the source module, not a global default). */
object ReminderSnooze {
    private const val MINUTE = 60_000L

    /** When the snoozed reminder shows again, or null when [action] is not a snooze this module offers. */
    fun untilMillis(module: ReminderModule, action: String, nowMillis: Long, zone: TimeZone): Long? = when (module) {
        ReminderModule.PLAN -> when (action) {
            ReminderActions.SNOOZE_10 -> nowMillis + 10 * MINUTE
            ReminderActions.SNOOZE_60, ReminderActions.SNOOZE -> nowMillis + 60 * MINUTE
            ReminderActions.TOMORROW -> app.pokeplan.plan.Reminders.snoozedTo(nowMillis.toLocalDateTime(zone), app.pokeplan.plan.Snooze.TOMORROW, zone).toEpochMillis(zone)
            else -> null
        }
        ReminderModule.CARE, ReminderModule.HEAL -> if (action == ReminderActions.SNOOZE_30 || action == ReminderActions.SNOOZE) nowMillis + 30 * MINUTE else null
        // A bill, a household or a connection reminder is not a minute-by-minute matter: its one snooze is until tomorrow morning, and nothing overdue repeats by itself.
        ReminderModule.MINT, ReminderModule.HOME, ReminderModule.LINK -> if (action == ReminderActions.TOMORROW) app.pokeplan.plan.Reminders.snoozedTo(nowMillis.toLocalDateTime(zone), app.pokeplan.plan.Snooze.TOMORROW, zone).toEpochMillis(zone) else null
        else -> null
    }
}

/** What became of a notification action, for the notification layer to show. */
enum class ActionResult { DONE, SNOOZED, DISMISSED, STALE, NOT_ALLOWED, UNKNOWN_MODULE }

/**
 * Routes a notification action to the module that owns the source (section 9.6.2): find the registered adapter, check the
 * source is still wanted, check the action, run it through the owner. Dismissing and snoozing never touch a source record, so
 * they can neither complete it nor pay a reward. The router knows no module data class.
 */
class ReminderRouter(adapters: List<ReminderSourceAdapter>) {
    private val byModule = adapters.associateBy { it.module }

    fun handle(request: ReminderRequest, action: String): ActionResult {
        val adapter = byModule[request.module] ?: return ActionResult.UNKNOWN_MODULE
        if (action == ReminderActions.DISMISS) return ActionResult.DISMISSED
        if (!adapter.isStillWanted(request)) return ActionResult.STALE
        return when (action) {
            // The caller reschedules the same id with [snoozed], for the length ReminderSnooze gives the module.
            in SNOOZES -> ActionResult.SNOOZED
            else -> if (adapter.execute(request, action)) ActionResult.DONE else ActionResult.NOT_ALLOWED
        }
    }

    private companion object {
        val SNOOZES = setOf(ReminderActions.SNOOZE, ReminderActions.SNOOZE_10, ReminderActions.SNOOZE_30, ReminderActions.SNOOZE_60, ReminderActions.TOMORROW)
    }

    /** The same logical reminder later: the id is unchanged, so a repeated snooze replaces the alarm instead of adding one (section 9.7). */
    fun snoozed(request: ReminderRequest, untilMillis: Long): ReminderRequest = request.copy(atMillis = untilMillis)

    /** Every adapter's current requests, rebuilt from the modules' own records (recovery, section 9.8). */
    fun rebuild(nowMillis: Long, zone: TimeZone): List<ReminderRequest> =
        byModule.values.flatMap { it.requests(nowMillis, zone) }
}

/**
 * PLAN's side: task reminders. [complete] finishes a task through Plan (and its rewards); the adapter reads the current save
 * through [app] so it always validates against the live record, and keeps only the task id in the request.
 */
class PlanReminderAdapter(
    private val app: () -> AppData,
    /**
     * The clock for Plan's two evening nudges, the streak reminder and the Sunday recap (docs/design.md, "Notifications"), which ask the shared scheduler like
     * a task does. Without one only task reminders exist (the default, and what the older tests build).
     */
    private val clock: (() -> Long)? = null,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val complete: (taskId: String) -> Boolean,
) : ReminderSourceAdapter {
    override val module = ReminderModule.PLAN

    override fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest> =
        app().upcomingReminders(nowMillis, zone).map { ReminderRequest(module, it.taskId, KIND_TASK, it.atMillis, occurrence = it.atMillis.toString()) } +
            if (clock != null) nudgeRequests(nowMillis, zone) else emptyList()

    /**
     * The streak reminder for each of the next days at its hour and the next Sunday recap. The request names the day it is for, so one that fires late (held
     * out of quiet hours or a focus session until the next day) is stale and shows nothing. A request inside quiet hours is not made at all, as before.
     */
    private fun nudgeRequests(nowMillis: Long, zone: TimeZone): List<ReminderRequest> {
        val data = app()
        val quiet = data.settings.quietHours
        val today = nowMillis.toLocalDateTime(zone).date
        return buildList {
            if (data.settings.streakReminder) for (offset in 0..2) {
                val day = today.plus(DatePeriod(days = offset))
                val at = LocalDateTime(day, LocalTime(data.settings.streakReminderHour, 0))
                if (!quiet.contains(at.time)) add(ReminderRequest(module, SOURCE_STREAK, KIND_STREAK, at.toEpochMillis(zone), occurrence = Streaks.streakDay(at).toString()))
            }
            Nudges.nextRecap(data, nowMillis, zone)?.let { at ->
                val local = at.toLocalDateTime(zone)
                if (!quiet.contains(local.time)) add(ReminderRequest(module, SOURCE_RECAP, KIND_RECAP, at, occurrence = local.date.toString()))
            }
        }
    }

    private fun task(request: ReminderRequest): Task? = app().tasks.firstOrNull { it.id == request.sourceRef }?.takeIf { it.isOpen }

    override fun isStillWanted(request: ReminderRequest): Boolean {
        when (request.kind) {
            KIND_STREAK -> {
                val now = clock?.invoke() ?: return false
                return Streaks.streakDay(now.toLocalDateTime(zone)).toString() == request.occurrence && Nudges.streakAtRisk(app(), now, zone, quietApplies = false)
            }
            KIND_RECAP -> {
                val now = clock?.invoke() ?: return false
                return now.toLocalDateTime(zone).date.toString() == request.occurrence && Nudges.weeklyRecap(app(), now, zone, quietApplies = false) != null
            }
        }
        val t = task(request) ?: return false
        return app().settings.reminders && when (request.kind) {
            KIND_REPEAT -> app().settings.repeatReminders
            else -> t.reminders.isNotEmpty()
        }
    }

    /** An unanswered reminder repeats up to [Nudges.REMINDER_REPEATS] times, [Nudges.REMINDER_REPEAT_MILLIS] apart (Plan's own policy, kept). */
    override fun followUp(request: ReminderRequest, nowMillis: Long): ReminderRequest? {
        if (request.kind != KIND_TASK && request.kind != KIND_REPEAT) return null
        if (!app().settings.repeatReminders) return null
        val base = request.occurrence.substringBefore('#')
        val n = request.occurrence.substringAfter('#', "0").toIntOrNull() ?: 0
        if (n >= Nudges.REMINDER_REPEATS) return null
        return request.copy(kind = KIND_REPEAT, occurrence = "$base#${n + 1}", atMillis = nowMillis + Nudges.REMINDER_REPEAT_MILLIS)
    }

    companion object {
        const val KIND_TASK = "task"
        const val KIND_REPEAT = "task-repeat"
        const val KIND_STREAK = "streak"
        const val KIND_RECAP = "recap"
        const val SOURCE_STREAK = "streak"
        const val SOURCE_RECAP = "week"
    }

    override fun present(request: ReminderRequest): NudgeText? = when (request.kind) {
        KIND_STREAK -> clock?.invoke()?.let { Nudges.streakText(app(), it, zone) }
        KIND_RECAP -> clock?.invoke()?.let { Nudges.weeklyRecap(app(), it, zone, quietApplies = false) }
        else -> task(request)?.let { NudgeText(it.title, "") }
    }

    // A streak reminder or a recap has no action but the shared Tomorrow-less dismiss: opening the app is the only thing it asks for.
    override fun execute(request: ReminderRequest, action: String): Boolean =
        action == ReminderActions.DONE && request.kind != KIND_STREAK && request.kind != KIND_RECAP && task(request) != null && complete(request.sourceRef)
}

/** CARE's side: routine reminders. Completion runs through Care's own rules; nothing is routed through Plan. */
class CareReminderAdapter(
    private val care: () -> CareData,
    private val app: () -> AppData,
    private val complete: (routineId: String, day: LocalDate) -> Boolean,
) : ReminderSourceAdapter {
    override val module = ReminderModule.CARE

    override fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest> =
        CareReminders.upcoming(care(), app(), nowMillis, zone).map {
            ReminderRequest(module, it.routineId, "routine", it.atMillis, occurrence = it.day.toString())
        }

    private fun day(request: ReminderRequest) = LocalDate.parse(request.occurrence)

    override fun isStillWanted(request: ReminderRequest) =
        runCatching { CareReminders.stillWanted(care(), app(), request.sourceRef, day(request)) }.getOrDefault(false)

    override fun present(request: ReminderRequest): NudgeText? =
        care().routines.firstOrNull { it.id == request.sourceRef && !it.archived }?.let { NudgeText(it.title, "") }

    override fun execute(request: ReminderRequest, action: String): Boolean =
        action == ReminderActions.DONE && isStillWanted(request) && complete(request.sourceRef, day(request))
}

/** What to do with a reminder that has gone off. */
sealed interface FireDecision {
    data class Show(val text: NudgeText) : FireDecision
    /** Not yet: a focus session or quiet hours hold it; schedule it again for [untilMillis] (section 9.5). */
    data class Defer(val untilMillis: Long) : FireDecision
    /** The source is gone, done or off: show nothing. */
    data object Drop : FireDecision
}

/**
 * The shared scheduler's brain, free of Android (sections 9.3 to 9.8): what to have scheduled, and what to do when a reminder
 * goes off. The Android side only sets alarms at these times and posts what [onFire] says to show.
 */
class ReminderCoordinator(adapters: List<ReminderSourceAdapter>) {
    private val byModule = adapters.associateBy { it.module }
    val router = ReminderRouter(adapters)

    /**
     * The requests to have alarms for: everything the modules rebuild from their records, plus the [held] ones (snoozes and
     * repeats, which win over a rebuilt request with the same id) that their source still wants.
     */
    fun schedule(
        nowMillis: Long, zone: TimeZone, quiet: app.pokeplan.plan.QuietHours, focus: FocusWindow?, held: List<ReminderRequest>,
        horizonMillis: Long = Long.MAX_VALUE,
    ): List<Pair<ReminderRequest, Long>> {
        val kept = held.filter { byModule[it.module]?.isStillWanted(it) == true }
        return ReminderPolicy.plan(router.rebuild(nowMillis, zone) + kept, nowMillis, quiet, zone, focus, horizonMillis)
    }

    /**
     * The reminders that came due after [sinceMillis] (when the scheduler last planned) and up to [nowMillis] without going off, because the phone was off
     * or an alarm came too late and a newer plan had replaced it (section 9.8). Each is rebuilt as it was planned at [sinceMillis], with its delivery rules
     * applied; a reminder whose [deliveredKey] is in [delivered] went off already, and one whose source no longer wants it is left out. Only the latest of
     * each source and kind is kept (a daily routine missed three times is one reminder), and one the modules plan again with the same id is theirs.
     * The caller holds each one to go off at once, so [onFire] checks it once more.
     */
    fun missed(
        sinceMillis: Long, nowMillis: Long, zone: TimeZone, quiet: app.pokeplan.plan.QuietHours, focus: FocusWindow?, delivered: Set<String>,
    ): List<ReminderRequest> {
        if (sinceMillis >= nowMillis) return emptyList()
        val upcoming = router.rebuild(nowMillis, zone).map { it.id }.toSet()
        return ReminderPolicy.plan(router.rebuild(sinceMillis, zone), sinceMillis, quiet, zone, focus, horizonMillis = nowMillis)
            .map { it.first }
            .filter { deliveredKey(it) !in delivered && it.id !in upcoming && byModule[it.module]?.isStillWanted(it) == true }
            .groupBy { Triple(it.module, it.sourceRef, it.kind) }.values.map { it.last() }
    }

    /** What marks one reminder as gone off: its id and the time it was asked for, so the next occurrence of a source with one id is still owed. */
    fun deliveredKey(request: ReminderRequest): String = "${request.id}@${request.atMillis}"

    /** Re-checks the source and the delivery rules at the moment of delivery (before the notification, section 9.3). */
    fun onFire(request: ReminderRequest, nowMillis: Long, zone: TimeZone, quiet: app.pokeplan.plan.QuietHours, focus: FocusWindow?): FireDecision {
        val adapter = byModule[request.module] ?: return FireDecision.Drop
        if (!adapter.isStillWanted(request)) return FireDecision.Drop
        val at = ReminderPolicy.deliverAt(request.copy(atMillis = nowMillis), quiet, zone, focus)
        if (at > nowMillis) return FireDecision.Defer(at)
        return adapter.present(request)?.let { FireDecision.Show(it) } ?: FireDecision.Drop
    }

    /** The follow-up to hold after showing [request] (a repeat), if its module has one. */
    fun followUp(request: ReminderRequest, nowMillis: Long): ReminderRequest? = byModule[request.module]?.followUp(request, nowMillis)
}
