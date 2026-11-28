package app.pokeplan.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pokeplan.LiveStore
import app.pokeplan.app.AppData
import app.pokeplan.app.reminders.ActionResult
import app.pokeplan.app.reminders.CareReminderAdapter
import app.pokeplan.app.heal.HealReminderAdapter
import app.pokeplan.app.base.BaseReminderAdapter
import app.pokeplan.app.link.LinkReminderAdapter
import app.pokeplan.linkStore
import app.pokeplan.app.mint.MintReminderAdapter
import app.pokeplan.baseStore
import app.pokeplan.mintStore
import app.pokeplan.healStore
import app.pokeplan.app.reminders.FireDecision
import app.pokeplan.app.reminders.FocusWindow
import app.pokeplan.app.plan.Nudges
import app.pokeplan.app.reminders.PlanReminderAdapter
import app.pokeplan.app.reminders.ReminderPolicy
import app.pokeplan.ui.Place
import app.pokeplan.app.reminders.ReminderActions
import app.pokeplan.app.reminders.ReminderCoordinator
import app.pokeplan.app.reminders.ReminderModule
import app.pokeplan.app.reminders.ReminderRequest
import app.pokeplan.app.reminders.ReminderSnooze
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

object SharedReminders {
    private const val PREFS = "shared_reminders"
    private const val HORIZON_MILLIS = 3 * 24 * 60 * 60_000L
    private const val HOLD_GRACE_MILLIS = 24 * 60 * 60_000L
    private const val OVERDUE_DELAY_MILLIS = 5_000L
    private const val SETTLE_MILLIS = 60_000L
    private const val LAST_PLANNED = "last_planned"
    private const val DELIVERED = "delivered"

    const val ACTION_FIRE = "app.pokeplan.reminders.SHARED_FIRE"
    const val ACTION_ACT = "app.pokeplan.reminders.SHARED_ACT"
    const val ACTION_REFRESH = "app.pokeplan.reminders.SHARED_REFRESH"
    private const val REFRESH_CODE = 0x5EF5
    private const val X_MODULE = "module"
    private const val X_REF = "ref"
    private const val X_KIND = "kind"
    private const val X_OCC = "occ"
    private const val X_AT = "at"
    private const val X_KEY = "key"

    private class Live(val coordinator: ReminderCoordinator, val quiet: app.pokeplan.plan.QuietHours, val focus: FocusWindow?)

    private fun live(context: Context): Live {
        val game = widgetStore(context)
        game.refresh()
        val care = careStore(context, game)
        val heal = healStore(context, game)
        val mint = mintStore(context, game)
        val home = baseStore(context, game)
        val link = linkStore(context, game)
        val coordinator = ReminderCoordinator(
            listOf(
                PlanReminderAdapter({ game.data }, { System.currentTimeMillis() }) { id -> if (game.data.tasks.any { it.id == id && it.isOpen }) { game.completeTask(id); game.data.tasks.any { it.id == id && !it.isOpen } } else false },
                HealReminderAdapter({ heal.data }, { game.data }, { heal.today }),
                MintReminderAdapter({ mint.data }, { game.data }, { mint.today }),
                BaseReminderAdapter({ home.data }, { game.data }, { home.today }) { id -> home.complete(id).record != null },
                LinkReminderAdapter({ link.data }, { game.data }, { link.today }) { id -> link.complete(id).record != null },
                CareReminderAdapter({ care.data }, { game.data }) { id, day ->
                    if (day == care.today && care.data.routines.any { it.id == id && !it.doneOn(day) }) { care.completeRoutine(id); true } else false
                },
            ),
        )
        val focus = ReminderPolicy.focusWindow(game.data.focus)
        return Live(coordinator, game.data.settings.quietHours, focus)
    }

    private fun repo(context: Context): ReminderRepository? = LiveStore.get(context)?.reminders

    /** Plans every reminder again and sets the alarms; the one call the app, the boot receiver and every fired alarm make. */
    fun reschedule(context: Context) = reschedule(context, null)

    /**
     * [known] is the plan the caller already built (a fired alarm), so a fire does not read and decrypt every module twice.
     * [force] is for a reboot or a clock change, when the alarms the phone held are gone even though their times did not move.
     */
    @Synchronized
    private fun reschedule(context: Context, known: Live?, force: Boolean = false) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val live = known ?: live(app)
        val repo = repo(app)
        repo?.dropOlderThan(now - HOLD_GRACE_MILLIS)
        val zone = TimeZone.currentSystemDefault()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val delivered = prefs.getStringSet(DELIVERED, emptySet()).orEmpty().filter { (it.substringAfterLast('@').toLongOrNull() ?: 0) >= now - HOLD_GRACE_MILLIS }.toSet()
        val since = prefs.getLong(LAST_PLANNED, 0L).takeIf { it > 0 }?.coerceAtLeast(now - HOLD_GRACE_MILLIS)
        val missed = since?.let { live.coordinator.missed(it, now - SETTLE_MILLIS, zone, live.quiet, live.focus, delivered) }.orEmpty()
            .map { it.copy(atMillis = now + OVERDUE_DELAY_MILLIS) }
        if (repo != null) missed.forEach { repo.hold(it.hold("snooze"), now) }
        val held = repo?.all().orEmpty().map { it.toRequest() }.map { if (it.atMillis <= now) it.copy(atMillis = now + OVERDUE_DELAY_MILLIS) else it } +
            (if (repo == null) missed else emptyList())
        val plan = live.coordinator.schedule(now, zone, live.quiet, live.focus, held, now + HORIZON_MILLIS)
        Alarms.reschedule(
            app, PREFS, plan, { it.first.id }, { it.second },
            intent = { key, item, flags -> fireIntent(app, key, item?.first, flags) },
            legacy = { code -> PendingIntent.getBroadcast(app, code, Intent(app, SharedReminderReceiver::class.java).setAction(ACTION_FIRE), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) },
            force = force,
        )
        prefs.edit().putLong(LAST_PLANNED, maxOf(since ?: 0L, now - SETTLE_MILLIS)).putStringSet(DELIVERED, delivered).apply()
        LegacyReminders.cancelOnce(app)
        setDailyRefresh(app, now)
    }

    private fun setDailyRefresh(app: Context, now: Long) {
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val zone = TimeZone.currentSystemDefault()
        val day = now.toLocalDateTime(zone).date
        val at = LocalDateTime(day.plus(1, DateTimeUnit.DAY), LocalTime(4, 0)).toEpochMillis(zone)
        val intent = PendingIntent.getBroadcast(
            app, REFRESH_CODE, Intent(app, SharedReminderReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try { alarms.setAndAllowWhileIdle(AlarmManager.RTC, at, intent) } catch (e: SecurityException) {  }
    }

    private fun fireIntent(context: Context, id: String, request: ReminderRequest?, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, id.hashCode(),
            Intent(context, SharedReminderReceiver::class.java).setAction(ACTION_FIRE).setData(identity("fire", id)).apply { request?.let { putRequest(it) } },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    fun actionIntent(context: Context, request: ReminderRequest, key: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, (request.id + key).hashCode(),
            Intent(context, SharedReminderReceiver::class.java).setAction(ACTION_ACT).setData(identity("act", "${request.id}/$key")).putRequest(request).putExtra(X_KEY, key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun identity(what: String, id: String): android.net.Uri = android.net.Uri.fromParts("pokelife-reminder", "$what/$id", null)

    private fun Intent.putRequest(r: ReminderRequest): Intent =
        putExtra(X_MODULE, r.module.name).putExtra(X_REF, r.sourceRef).putExtra(X_KIND, r.kind).putExtra(X_OCC, r.occurrence).putExtra(X_AT, r.atMillis)

    private fun Intent.request(): ReminderRequest? = ReminderRequest(
        module = getStringExtra(X_MODULE)?.let { m -> ReminderModule.parse(m) } ?: return null,
        sourceRef = getStringExtra(X_REF) ?: return null, kind = getStringExtra(X_KIND) ?: return null,
        atMillis = getLongExtra(X_AT, 0L), occurrence = getStringExtra(X_OCC).orEmpty(),
    )

    private fun HeldReminder.toRequest() = ReminderRequest(
        ReminderModule.parse(module) ?: ReminderModule.PLAN, sourceRef, kind, atMillis, occurrence = occurrence,
    )

    private fun ReminderRequest.hold(reason: String) = HeldReminder(id, module.name, sourceRef, kind, occurrence, atMillis, reason)

    private fun hold(context: Context, request: ReminderRequest, reason: String, now: Long) {
        repo(context)?.hold(request.hold(reason), now) ?: run {
            val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
            Alarms.set(context, alarms, request.atMillis, fireIntent(context, request.id, request, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        }
    }

    private fun releaseSource(context: Context, request: ReminderRequest, occurrence: String? = null) {
        val repo = repo(context) ?: return
        repo.all().filter { it.module == request.module.name && it.sourceRef == request.sourceRef && (occurrence == null || it.occurrence.substringBefore('#') == occurrence) }
            .forEach { repo.release(it.id) }
    }

    fun fire(context: Context, request: ReminderRequest) {
        val now = System.currentTimeMillis()
        val live = live(context)
        val zone = TimeZone.currentSystemDefault()
        markDelivered(context, live.coordinator.deliveredKey(request))
        repo(context)?.release(request.id)
        when (val decision = live.coordinator.onFire(request, now, zone, live.quiet, live.focus)) {
            is FireDecision.Defer -> hold(context, request.copy(atMillis = decision.untilMillis), "snooze", now)
            FireDecision.Drop -> Unit
            is FireDecision.Show -> {
                val repeat = request.kind == PlanReminderAdapter.KIND_REPEAT
                if (!repeat || TaskReminders.isShowing(context, request.sourceRef)) {
                    present(context, request, decision.text.title, now)
                    live.coordinator.followUp(request, now)?.let { hold(context, it, "repeat", now) }
                }
            }
        }
        reschedule(context, live)
    }

    @Synchronized
    private fun markDelivered(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(DELIVERED, prefs.getStringSet(DELIVERED, emptySet()).orEmpty() + key).apply()
    }

    private fun present(context: Context, request: ReminderRequest, title: String, now: Long) {
        when (request.module) {
            ReminderModule.PLAN -> when (request.kind) {
                PlanReminderAdapter.KIND_STREAK -> AppAlerts.show(context, AppAlerts.Kind.STREAK, title, widgetStore(context).let { Nudges.streakText(it.data, now, TimeZone.currentSystemDefault()).body }, Place.HOME.name)
                PlanReminderAdapter.KIND_RECAP -> AppAlerts.show(context, AppAlerts.Kind.RECAP, title, Nudges.weeklyRecap(widgetStore(context).data, now, TimeZone.currentSystemDefault(), quietApplies = false)?.body.orEmpty(), Place.ME.name)
                PlanReminderAdapter.KIND_EVENT -> TaskReminders.showEvent(context, request, title, eventBody(widgetStore(context).data, request))
                else -> widgetStore(context).data.tasks.firstOrNull { it.id == request.sourceRef }?.let { TaskReminders.show(context, it, now, request) }
            }
            ReminderModule.CARE -> RoutineReminders.show(context, request.sourceRef, title, LocalDate.parse(request.occurrence), request)
            ReminderModule.HEAL -> HydrationReminders.show(context, request.sourceRef, title, request)
            ReminderModule.MINT -> BillReminders.show(context, request.sourceRef, title, request)
            ReminderModule.BASE -> UpkeepReminders.show(context, request, title)
            ReminderModule.LINK -> LinkNotices.show(context, request, title)
            else -> Unit
        }
    }

    private fun eventBody(data: AppData, request: ReminderRequest): String {
        val e = data.events.firstOrNull { it.id == request.sourceRef } ?: return ""
        return if (e.allDay) "All day" else "Starts at ${e.start.hour}:${e.start.minute.toString().padStart(2, '0')}"
    }

    /** Plans again after a reboot or a clock/time-zone change: the alarms are gone even if their times did not move. */
    @Synchronized
    fun rescheduleAfterReboot(context: Context) = reschedule(context, null, force = true)

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
            ReminderModule.BASE -> UpkeepReminders.notificationId(request.sourceRef, request.kind)
            ReminderModule.LINK -> LinkNotices.notificationId(request.sourceRef, request.kind)
            else -> return
        }
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }

    internal fun requestOf(intent: Intent): ReminderRequest? = intent.request()
    internal fun keyOf(intent: Intent): String? = intent.getStringExtra(X_KEY)
}

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
