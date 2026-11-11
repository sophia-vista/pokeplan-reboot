package app.pokeplan.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pokeplan.LiveStore
import app.pokeplan.app.ActionResult
import app.pokeplan.app.CareReminderAdapter
import app.pokeplan.app.HealReminderAdapter
import app.pokeplan.app.HomeReminderAdapter
import app.pokeplan.app.LinkReminderAdapter
import app.pokeplan.linkStore
import app.pokeplan.app.MintReminderAdapter
import app.pokeplan.homeStore
import app.pokeplan.mintStore
import app.pokeplan.healStore
import app.pokeplan.app.FireDecision
import app.pokeplan.app.FocusWindow
import app.pokeplan.app.Nudges
import app.pokeplan.app.PlanReminderAdapter
import app.pokeplan.ui.Place
import app.pokeplan.app.ReminderActions
import app.pokeplan.app.ReminderCoordinator
import app.pokeplan.app.ReminderModule
import app.pokeplan.app.ReminderRequest
import app.pokeplan.app.ReminderSnooze
import app.pokeplan.careStore
import app.pokeplan.data.HeldReminder
import app.pokeplan.data.ReminderRepository
import app.pokeplan.widgetStore
import app.pokeplan.widgets.updateWidgets
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import app.pokeplan.plan.toEpochMillis
import app.pokeplan.plan.toLocalDateTime
import kotlinx.datetime.TimeZone

/**
 * The one reminder scheduler for PLAN, CARE, HEAL, MINT, HOME and LINK (INTEGRATIONS_AND_PRIVACY.md section 9). The policy and the decisions are in
 * [ReminderCoordinator] (JVM-tested); this object only sets alarms at the times it plans, posts what it says to show, and routes
 * a tapped action to the module that owns the source. Alarms carry only the opaque request (module, reference, kind,
 * occurrence), never a title or a note. Snoozes and repeats are the one thing it stores (`reminder_hold`); everything else is
 * rebuilt from the modules' own saves, so a reboot, an update or a time change just plans again.
 */
object SharedReminders {
    private const val PREFS = "shared_reminders"
    private const val HORIZON_MILLIS = 3 * 24 * 60 * 60_000L
    private const val HOLD_GRACE_MILLIS = 24 * 60 * 60_000L
    private const val OVERDUE_DELAY_MILLIS = 5_000L

    const val ACTION_FIRE = "app.pokeplan.reminders.SHARED_FIRE"
    const val ACTION_ACT = "app.pokeplan.reminders.SHARED_ACT"
    const val ACTION_REFRESH = "app.pokeplan.reminders.SHARED_REFRESH"
    private const val REFRESH_CODE = 0x5EF5
    private const val X_MODULE = "module"
    private const val X_REF = "ref"
    private const val X_KIND = "kind"
    private const val X_OCC = "occ"
    private const val X_KEY = "key"

    private class Live(val coordinator: ReminderCoordinator, val quiet: app.pokeplan.plan.QuietHours, val focus: FocusWindow?)

    private fun live(context: Context): Live {
        val game = widgetStore(context)
        val care = careStore(context, game)
        val heal = healStore(context, game)
        val mint = mintStore(context, game)
        val home = homeStore(context, game)
        val link = linkStore(context, game)
        val coordinator = ReminderCoordinator(
            listOf(
                PlanReminderAdapter({ game.data }, { System.currentTimeMillis() }) { id -> if (game.data.tasks.any { it.id == id && it.isOpen }) { game.completeTask(id); game.data.tasks.any { it.id == id && !it.isOpen } } else false },
                HealReminderAdapter({ heal.data }, { game.data }, { heal.today }),
                MintReminderAdapter({ mint.data }, { game.data }, { mint.today }),
                // A chore's "Done" is Home's own completion, the one that pays Habitat Discovery once; nothing else of Home is ever done from a notification.
                HomeReminderAdapter({ home.data }, { game.data }, { home.today }) { id -> home.complete(id).record != null },
                // A follow-up's "Done" is Link's own completion, the one that pays Postcard Trail once; a date's reminder offers no action but "Tomorrow".
                LinkReminderAdapter({ link.data }, { game.data }, { link.today }) { id -> link.complete(id).record != null },
                CareReminderAdapter({ care.data }, { game.data }) { id, day ->
                    // Ticked like on the Care screen, and paid the same way, only for today's routine.
                    if (day == care.today && care.data.routines.any { it.id == id && !it.doneOn(day) }) { care.completeRoutine(id); true } else false
                },
            ),
        )
        val session = game.data.focus?.takeIf { !it.isFinished }
        val focus = session?.let { FocusWindow(it.startedAtMillis, it.startedAtMillis + it.durationMillis + it.pausedMillis) }
        return Live(coordinator, game.data.settings.quietHours, focus)
    }

    private fun repo(context: Context): ReminderRepository? = LiveStore.get(context)?.reminders

    /** Plans every reminder again and sets the alarms; the one call the app, the boot receiver and every fired alarm make. */
    @Synchronized
    fun reschedule(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val live = live(app)
        val repo = repo(app)
        repo?.dropOlderThan(now - HOLD_GRACE_MILLIS)
        // A snooze or a repeat that came due while the phone was off (or the app was being replaced) is still owed: it goes off now, and
        // fire() checks again that it is wanted. Without this the planner, which looks only ahead, would leave it until it was dropped.
        val held = repo?.all().orEmpty().map { it.toRequest() }.map { if (it.atMillis <= now) it.copy(atMillis = now + OVERDUE_DELAY_MILLIS) else it }
        val plan = live.coordinator.schedule(now, TimeZone.currentSystemDefault(), live.quiet, live.focus, held, now + HORIZON_MILLIS)
        Alarms.reschedule(app, PREFS, plan, { it.first.id.hashCode() }, { it.second }) { code, item, flags -> fireIntent(app, code, item?.first, flags) }
        LegacyReminders.cancelOnce(app)
        setDailyRefresh(app, now)
    }

    /**
     * One inexact alarm a night that only plans everything again. The planner looks [HORIZON_MILLIS] ahead, so without it a reminder set days before a date
     * (Link's "a week before" birthday) would be planned only if the app happened to open, or another alarm happened to fire, in the last three days.
     */
    private fun setDailyRefresh(app: Context, now: Long) {
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val zone = TimeZone.currentSystemDefault()
        val day = now.toLocalDateTime(zone).date
        val at = LocalDateTime(day.plus(1, DateTimeUnit.DAY), LocalTime(4, 0)).toEpochMillis(zone)
        val intent = PendingIntent.getBroadcast(
            app, REFRESH_CODE, Intent(app, SharedReminderReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try { alarms.setAndAllowWhileIdle(AlarmManager.RTC, at, intent) } catch (e: SecurityException) { /* the next opening plans again */ }
    }

    private fun fireIntent(context: Context, code: Int, request: ReminderRequest?, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, SharedReminderReceiver::class.java).setAction(ACTION_FIRE).apply { request?.let { putRequest(it) } },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    /** A button on a notification: the request and a stable action key. */
    fun actionIntent(context: Context, request: ReminderRequest, key: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, (request.id + key).hashCode(),
            Intent(context, SharedReminderReceiver::class.java).setAction(ACTION_ACT).putRequest(request).putExtra(X_KEY, key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun Intent.putRequest(r: ReminderRequest): Intent =
        putExtra(X_MODULE, r.module.name).putExtra(X_REF, r.sourceRef).putExtra(X_KIND, r.kind).putExtra(X_OCC, r.occurrence)

    private fun Intent.request(): ReminderRequest? = ReminderRequest(
        module = getStringExtra(X_MODULE)?.let { m -> ReminderModule.entries.firstOrNull { it.name == m } } ?: return null,
        sourceRef = getStringExtra(X_REF) ?: return null, kind = getStringExtra(X_KIND) ?: return null,
        atMillis = 0, occurrence = getStringExtra(X_OCC).orEmpty(),
    )

    private fun HeldReminder.toRequest() = ReminderRequest(
        ReminderModule.entries.first { it.name == module }, sourceRef, kind, atMillis, occurrence = occurrence,
    )

    private fun ReminderRequest.hold(reason: String) = HeldReminder(id, module.name, sourceRef, kind, occurrence, atMillis, reason)

    /** Keeps [request] (at its own time) until it is due, so a reboot does not lose it. Without a database it still gets its alarm. */
    private fun hold(context: Context, request: ReminderRequest, reason: String, now: Long) {
        repo(context)?.hold(request.hold(reason), now) ?: run {
            val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
            Alarms.set(context, alarms, request.atMillis, fireIntent(context, request.id.hashCode(), request, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        }
    }

    /** Lets go of what is held for [request]'s source: all of it when the thing is done, only [occurrence]'s (and its repeats) when one reminder is put off. */
    private fun releaseSource(context: Context, request: ReminderRequest, occurrence: String? = null) {
        val repo = repo(context) ?: return
        repo.all().filter { it.module == request.module.name && it.sourceRef == request.sourceRef && (occurrence == null || it.occurrence.substringBefore('#') == occurrence) }
            .forEach { repo.release(it.id) }
    }

    /** An alarm went off: re-check the source and the delivery rules, then show it, hold it for later, or drop it. */
    fun fire(context: Context, request: ReminderRequest) {
        val now = System.currentTimeMillis()
        val live = live(context)
        val zone = TimeZone.currentSystemDefault()
        repo(context)?.release(request.id)
        when (val decision = live.coordinator.onFire(request, now, zone, live.quiet, live.focus)) {
            is FireDecision.Defer -> hold(context, request.copy(atMillis = decision.untilMillis), "snooze", now)
            FireDecision.Drop -> Unit
            is FireDecision.Show -> {
                // A repeat only shows if the last notification of that reminder is still there, unanswered.
                val repeat = request.kind == PlanReminderAdapter.KIND_REPEAT
                if (!repeat || TaskReminders.isShowing(context, request.sourceRef)) {
                    present(context, request, decision.text.title, now)
                    live.coordinator.followUp(request, now)?.let { hold(context, it, "repeat", now) }
                }
            }
        }
        reschedule(context)
    }

    private fun present(context: Context, request: ReminderRequest, title: String, now: Long) {
        when (request.module) {
            ReminderModule.PLAN -> when (request.kind) {
                // The evening streak reminder and the Sunday recap: the shared scheduler decided; the words and the channel are the app's own.
                PlanReminderAdapter.KIND_STREAK -> AppAlerts.show(context, AppAlerts.Kind.STREAK, title, widgetStore(context).let { Nudges.streakText(it.data, now, TimeZone.currentSystemDefault()).body }, Place.HOME.name)
                PlanReminderAdapter.KIND_RECAP -> AppAlerts.show(context, AppAlerts.Kind.RECAP, title, Nudges.weeklyRecap(widgetStore(context).data, now, TimeZone.currentSystemDefault(), quietApplies = false)?.body.orEmpty(), Place.ME.name)
                else -> widgetStore(context).data.tasks.firstOrNull { it.id == request.sourceRef }?.let { TaskReminders.show(context, it, now, request) }
            }
            ReminderModule.CARE -> RoutineReminders.show(context, request.sourceRef, title, LocalDate.parse(request.occurrence), request)
            ReminderModule.HEAL -> HydrationReminders.show(context, request.sourceRef, title, request)
            ReminderModule.MINT -> BillReminders.show(context, request.sourceRef, title, request)
            ReminderModule.HOME -> UpkeepReminders.show(context, request, title)
            ReminderModule.LINK -> LinkNotices.show(context, request, title)
            else -> Unit
        }
    }

    /** A button was tapped (or an old notification's action arrived through a shim): route it to the owner, then plan again. */
    suspend fun act(context: Context, request: ReminderRequest, key: String) {
        val now = System.currentTimeMillis()
        val live = live(context)
        when (live.coordinator.router.handle(request, key)) {
            ActionResult.DONE -> {
                releaseSource(context, request)
                cancelNotification(context, request)
                updateWidgets(context)
            }
            ActionResult.SNOOZED -> {
                val until = ReminderSnooze.untilMillis(request.module, key, now, TimeZone.currentSystemDefault())
                if (until != null) {
                    releaseSource(context, request, occurrence = request.occurrence.substringBefore('#'))
                    // The same logical reminder later (section 9.7): the base request keeps its id, a repeat becomes the base again.
                    val base = if (request.kind == PlanReminderAdapter.KIND_REPEAT) request.copy(kind = "task", occurrence = request.occurrence.substringBefore('#')) else request
                    hold(context, live.coordinator.router.snoozed(base, until), "snooze", now)
                    cancelNotification(context, request)
                }
            }
            ActionResult.DISMISSED -> cancelNotification(context, request)
            ActionResult.STALE -> cancelNotification(context, request)
            else -> Unit
        }
        reschedule(context)
    }

    private fun cancelNotification(context: Context, request: ReminderRequest) {
        val id = when (request.module) {
            ReminderModule.PLAN -> TaskReminders.notificationId(request.sourceRef)
            ReminderModule.CARE -> RoutineReminders.notificationId(request.sourceRef)
            ReminderModule.HEAL -> HydrationReminders.notificationId(request.sourceRef)
            ReminderModule.MINT -> BillReminders.notificationId(request.sourceRef)
            ReminderModule.HOME -> UpkeepReminders.notificationId(request.sourceRef, request.kind)
            ReminderModule.LINK -> LinkNotices.notificationId(request.sourceRef, request.kind)
            else -> return
        }
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }

    internal fun requestOf(intent: Intent): ReminderRequest? = intent.request()
    internal fun keyOf(intent: Intent): String? = intent.getStringExtra(X_KEY)
}

/** An alarm going off, or a notification button tapped. */
class SharedReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == SharedReminders.ACTION_REFRESH) {
            val app = context.applicationContext
            doAsync { SharedReminders.reschedule(app) }
            return
        }
        val request = SharedReminders.requestOf(intent) ?: return
        val key = SharedReminders.keyOf(intent)
        val app = context.applicationContext
        doAsync {
            if (intent.action == SharedReminders.ACTION_ACT && key != null) SharedReminders.act(app, request, key)
            else if (intent.action == SharedReminders.ACTION_FIRE) SharedReminders.fire(app, request)
        }
    }
}
