package app.pokeplan.app.reminders

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
import app.pokeplan.app.AppData
import app.pokeplan.app.care.CareReminders
import app.pokeplan.app.plan.NudgeText
import app.pokeplan.app.plan.Nudges
import app.pokeplan.app.upcomingReminders

object ReminderActions {
    const val DONE = "done"
    const val SNOOZE = "snooze"
    const val DISMISS = "dismiss"
    const val SNOOZE_10 = "snooze10"
    const val SNOOZE_60 = "snooze60"
    const val TOMORROW = "tomorrow"
    const val SNOOZE_30 = "snooze30"
}

object ReminderSnooze {
    private const val MINUTE = 60_000L

    fun untilMillis(module: ReminderModule, action: String, nowMillis: Long, zone: TimeZone): Long? = when (module) {
        ReminderModule.PLAN -> when (action) {
            ReminderActions.SNOOZE_10 -> nowMillis + 10 * MINUTE
            ReminderActions.SNOOZE_60, ReminderActions.SNOOZE -> nowMillis + 60 * MINUTE
            ReminderActions.TOMORROW -> app.pokeplan.plan.Reminders.snoozedTo(nowMillis.toLocalDateTime(zone), app.pokeplan.plan.Snooze.TOMORROW, zone).toEpochMillis(zone)
            else -> null
        }
        ReminderModule.CARE, ReminderModule.HEAL -> if (action == ReminderActions.SNOOZE_30 || action == ReminderActions.SNOOZE) nowMillis + 30 * MINUTE else null
        ReminderModule.MINT, ReminderModule.BASE, ReminderModule.LINK -> if (action == ReminderActions.TOMORROW) app.pokeplan.plan.Reminders.snoozedTo(nowMillis.toLocalDateTime(zone), app.pokeplan.plan.Snooze.TOMORROW, zone).toEpochMillis(zone) else null
        else -> null
    }
}

enum class ActionResult { DONE, SNOOZED, DISMISSED, STALE, NOT_ALLOWED, UNKNOWN_MODULE }

class ReminderRouter(adapters: List<ReminderSourceAdapter>) {
    private val byModule = adapters.associateBy { it.module }

    fun handle(request: ReminderRequest, action: String): ActionResult {
        val adapter = byModule[request.module] ?: return ActionResult.UNKNOWN_MODULE
        if (action == ReminderActions.DISMISS) return ActionResult.DISMISSED
        if (!adapter.isStillWanted(request)) return ActionResult.STALE
        return when (action) {
            in SNOOZES -> ActionResult.SNOOZED
            else -> if (adapter.execute(request, action)) ActionResult.DONE else ActionResult.NOT_ALLOWED
        }
    }

    private companion object {
        val SNOOZES = setOf(ReminderActions.SNOOZE, ReminderActions.SNOOZE_10, ReminderActions.SNOOZE_30, ReminderActions.SNOOZE_60, ReminderActions.TOMORROW)
    }

    fun snoozed(request: ReminderRequest, untilMillis: Long): ReminderRequest = request.copy(atMillis = untilMillis)

    fun rebuild(nowMillis: Long, zone: TimeZone): List<ReminderRequest> =
        byModule.values.flatMap { it.requests(nowMillis, zone) }
}

class PlanReminderAdapter(
    private val app: () -> AppData,
    private val clock: (() -> Long)? = null,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val complete: (taskId: String) -> Boolean,
) : ReminderSourceAdapter {
    override val module = ReminderModule.PLAN

    override fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest> =
        app().upcomingReminders(nowMillis, zone).map { ReminderRequest(module, it.taskId, KIND_TASK, it.atMillis, occurrence = it.atMillis.toString()) } +
            if (clock != null) nudgeRequests(nowMillis, zone) else emptyList()

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
            else -> t.reminders.isNotEmpty() || t.nudge != null
        }
    }

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

    override fun execute(request: ReminderRequest, action: String): Boolean =
        action == ReminderActions.DONE && request.kind != KIND_STREAK && request.kind != KIND_RECAP && task(request) != null && complete(request.sourceRef)
}

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

sealed interface FireDecision {
    data class Show(val text: NudgeText) : FireDecision
    data class Defer(val untilMillis: Long) : FireDecision
    data object Drop : FireDecision
}

class ReminderCoordinator(adapters: List<ReminderSourceAdapter>) {
    private val byModule = adapters.associateBy { it.module }
    val router = ReminderRouter(adapters)

    fun schedule(
        nowMillis: Long, zone: TimeZone, quiet: app.pokeplan.plan.QuietHours, focus: FocusWindow?, held: List<ReminderRequest>,
        horizonMillis: Long = Long.MAX_VALUE,
    ): List<Pair<ReminderRequest, Long>> {
        val kept = held.filter { byModule[it.module]?.isStillWanted(it) == true }
        return ReminderPolicy.plan(router.rebuild(nowMillis, zone) + kept, nowMillis, quiet, zone, focus, horizonMillis)
    }

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

    fun deliveredKey(request: ReminderRequest): String = "${request.id}@${request.atMillis}"

    fun onFire(request: ReminderRequest, nowMillis: Long, zone: TimeZone, quiet: app.pokeplan.plan.QuietHours, focus: FocusWindow?): FireDecision {
        val adapter = byModule[request.module] ?: return FireDecision.Drop
        if (!adapter.isStillWanted(request)) return FireDecision.Drop
        val at = ReminderPolicy.deliverAt(request.copy(atMillis = nowMillis), quiet, zone, focus)
        if (at > nowMillis) return FireDecision.Defer(at)
        return adapter.present(request)?.let { FireDecision.Show(it) } ?: FireDecision.Drop
    }

    fun followUp(request: ReminderRequest, nowMillis: Long): ReminderRequest? = byModule[request.module]?.followUp(request, nowMillis)
}
