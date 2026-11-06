package app.pokeplan.app

import app.pokeplan.care.CareData
import app.pokeplan.plan.Task
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** The stable action keys a notification may carry (INTEGRATIONS_AND_PRIVACY.md, section 9.6.2). */
object ReminderActions {
    const val DONE = "done"
    const val SNOOZE = "snooze"
    const val DISMISS = "dismiss"
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
            ReminderActions.SNOOZE -> ActionResult.SNOOZED // the caller reschedules the same id with [snoozed]
            else -> if (adapter.execute(request, action)) ActionResult.DONE else ActionResult.NOT_ALLOWED
        }
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
    private val complete: (taskId: String) -> Boolean,
) : ReminderSourceAdapter {
    override val module = ReminderModule.PLAN

    override fun requests(nowMillis: Long, zone: TimeZone): List<ReminderRequest> =
        app().upcomingReminders(nowMillis, zone).map { ReminderRequest(module, it.taskId, "task", it.atMillis, occurrence = it.atMillis.toString()) }

    private fun task(request: ReminderRequest): Task? = app().tasks.firstOrNull { it.id == request.sourceRef }?.takeIf { it.isOpen }

    override fun isStillWanted(request: ReminderRequest) = app().settings.reminders && task(request)?.reminders?.isNotEmpty() == true

    override fun present(request: ReminderRequest): NudgeText? = task(request)?.let { NudgeText(it.title, "") }

    override fun execute(request: ReminderRequest, action: String): Boolean =
        action == ReminderActions.DONE && task(request) != null && complete(request.sourceRef)
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
