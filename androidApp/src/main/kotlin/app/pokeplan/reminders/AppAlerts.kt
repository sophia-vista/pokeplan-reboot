package app.pokeplan.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pokeplan.MainActivity
import app.pokeplan.app.AppData
import app.pokeplan.app.plan.FocusAlert
import app.pokeplan.app.gear.GearTimers
import app.pokeplan.app.plan.Nudges
import app.pokeplan.app.world.WorldAlerts
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Phase
import app.pokeplan.ui.Place
import app.pokeplan.widgetStore
import kotlinx.datetime.TimeZone
import app.pokeplan.app.gear.timerAddTime
import app.pokeplan.app.gear.timersDismissFinished
import app.pokeplan.app.gear.timersTick
import app.pokeplan.app.world.worldSettle

object AppAlerts {
    enum class Kind(val code: Int, val channel: String, val notificationId: Int) {
        HALFWAY(9101, Channels.FOCUS_HALFWAY, 7101),
        END(9102, Channels.FOCUS_END, 7102),
        STREAK(9103, Channels.EVENING_NUDGES, 7103),
        RECAP(9104, Channels.EVENING_NUDGES, 7104),

        JOB(9107, Channels.WORLD_JOBS, 7107),

        TIMER(9108, Channels.GEAR_TIMERS, 7108),
    }

    const val ACTION_ALERT = "app.pokeplan.reminders.ALERT"
    const val EXTRA_KIND = "kind"
    const val EXTRA_SESSION = "session"

    data class Plan(val halfway: Long?, val end: Long?, val sessionId: String?, val job: Long? = null, val timer: Long? = null)

    fun plan(data: AppData, nowMillis: Long, zone: TimeZone): Plan {
        val alerts = Nudges.focusAlerts(data.focus, data.settings, nowMillis)
        return Plan(
            halfway = alerts.firstOrNull { it.kind == FocusAlert.Kind.HALFWAY }?.atMillis,
            end = alerts.firstOrNull { it.kind == FocusAlert.Kind.END }?.atMillis,
            sessionId = data.focus?.takeIf { !it.isFinished }?.id,
            job = WorldAlerts.nextJobDone(data, nowMillis),
            timer = GearTimers.nextEnd(data, nowMillis),
        )
    }

    fun schedule(context: Context, plan: Plan) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        if (plan.sessionId == null) cancel(context, Kind.HALFWAY)
        set(context, alarms, Kind.HALFWAY, plan.halfway, plan.sessionId, exact = true)
        set(context, alarms, Kind.END, plan.end, plan.sessionId, exact = true)
        set(context, alarms, Kind.JOB, plan.job, null, exact = false)
        set(context, alarms, Kind.TIMER, plan.timer, null, exact = true)
    }

    fun rescheduleFromSave(context: Context) {
        val store = widgetStore(context)
        schedule(context, plan(store.data, store.nowMillis, TimeZone.currentSystemDefault()))
    }

    internal fun set(context: Context, alarms: AlarmManager, kind: Kind, atMillis: Long?, sessionId: String?, exact: Boolean) {
        if (atMillis == null) {
            pending(context, kind, null, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
            return
        }
        val intent = pending(context, kind, sessionId, PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (exact) Alarms.set(context, alarms, atMillis, intent) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
    }

    private fun pending(context: Context, kind: Kind, sessionId: String?, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, kind.code,
            Intent(context, NudgeReceiver::class.java).setAction(ACTION_ALERT).putExtra(EXTRA_KIND, kind.name).apply { sessionId?.let { putExtra(EXTRA_SESSION, it) } },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun channels(context: Context): NotificationManager? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        manager.createNotificationChannel(NotificationChannel(Channels.FOCUS_HALFWAY, "Focus halfway", NotificationManager.IMPORTANCE_LOW))
        manager.retireOldChannels()
        manager.createNotificationChannel(NotificationChannel(Channels.FOCUS_END, "Focus time is up", NotificationManager.IMPORTANCE_DEFAULT).withGameSound(context))
        manager.createNotificationChannel(NotificationChannel(Channels.EVENING_NUDGES, "Streak and weekly recap", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(Channels.WORLD_JOBS, "Finished World jobs", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel(Channels.GEAR_TIMERS, "Pokégear timers", NotificationManager.IMPORTANCE_HIGH).withGameSound(context))
        return manager
    }

    fun show(context: Context, kind: Kind, title: String, text: String, tab: String) {
        channels(context) ?: return
        val open = PendingIntent.getActivity(
            context, kind.code, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, kind.channel)
            .setSmallIcon(notificationIcon(context, "poke-ball"))
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(if (kind == Kind.HALFWAY || kind == Kind.END) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_RECOMMENDATION)
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply { if (kind == Kind.HALFWAY) setOnlyAlertOnce(true) }
            .build()
        notifySafely(context, kind.notificationId, notification)
    }

    const val ACTION_TIMER_STOP = "app.pokeplan.reminders.TIMER_STOP"
    const val ACTION_TIMER_PLUS = "app.pokeplan.reminders.TIMER_PLUS"

    fun showTimers(context: Context, labels: List<String>) {
        channels(context) ?: return
        fun action(code: Int, name: String) = PendingIntent.getBroadcast(context, code, Intent(context, TimerActionReceiver::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(
            context, Kind.TIMER.code, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Place.GEAR.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, Kind.TIMER.channel)
            .setSmallIcon(notificationIcon(context, "timer-ball"))
            .setContentTitle(if (labels.size == 1) "Time is up: ${labels.first()}" else "${labels.size} timers are done")
            .setContentText(if (labels.size == 1) "Your Pokégear timer has run out." else labels.joinToString(", "))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Stop", action(Kind.TIMER.code + 1000, ACTION_TIMER_STOP)).build())
            .addAction(Notification.Action.Builder(null, "+1 min", action(Kind.TIMER.code + 2000, ACTION_TIMER_PLUS)).build())
            .build()
        notifySafely(context, Kind.TIMER.notificationId, notification)
    }

    fun cancel(context: Context, kind: Kind) {
        context.getSystemService(NotificationManager::class.java)?.cancel(kind.notificationId)
    }

    fun sessionAt(data: AppData, nowMillis: Long): FocusSession? = data.focus?.tick(nowMillis)
}

class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(AppAlerts.EXTRA_KIND)?.let { name -> AppAlerts.Kind.entries.firstOrNull { it.name == name } } ?: return
        val sessionId = intent.getStringExtra(AppAlerts.EXTRA_SESSION)
        val app = context.applicationContext
        doAsync { handle(app, kind, sessionId) }
    }

    private fun handle(context: Context, kind: AppAlerts.Kind, sessionId: String?) {
        val store = widgetStore(context)
        val data = store.data
        val now = store.nowMillis
        val zone = TimeZone.currentSystemDefault()
        val session = AppAlerts.sessionAt(data, now)
        when (kind) {
            AppAlerts.Kind.HALFWAY -> {
                if (session != null && session.id == sessionId && data.settings.focusHalfway && (session.phase is Phase.Running || session.phase is Phase.Away)) {
                    val minutes = ((session.remainingMillis(now) + 59_999) / 60_000).coerceAtLeast(1)
                    AppAlerts.show(context, kind, "Halfway there", "$minutes min to go. Your Egg is warming up.", Place.FOCUS.name)
                }
            }
            AppAlerts.Kind.END -> {
                if (session != null && session.id == sessionId && data.settings.focusEnd && session.phase !is Phase.Failed && session.phase !is Phase.Paused) {
                    AppAlerts.show(context, kind, "Focus time is up", "Open PokéLife to see your Egg hatch.", Place.FOCUS.name)
                }
            }
            AppAlerts.Kind.STREAK, AppAlerts.Kind.RECAP -> Unit
            AppAlerts.Kind.TIMER -> {
                store.timersTick()
                val ringing = store.data.gear.timers.filter { it.finished }.map { it.label }
                if (ringing.isNotEmpty()) AppAlerts.showTimers(context, ringing)
            }
            AppAlerts.Kind.JOB -> {
                if (data.settings.worldJobAlerts) {
                    store.worldSettle()
                    val done = store.data.world.missions.filter { it.done }.mapNotNull { app.pokeplan.world.Missions.byId(it.id)?.title }
                    if (done.isNotEmpty()) {
                        val text = if (done.size == 1) "${done.first()}. Claim it on the mission board." else "${done.size} jobs are ready to claim on the mission board."
                        AppAlerts.show(context, kind, "A World job is done", text, Place.WORLD.name)
                    }
                }
            }
        }
        AppAlerts.rescheduleFromSave(context)
    }
}

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        doAsync {
            val store = widgetStore(app)
            val ringing = store.data.gear.timers.filter { it.finished }
            when (action) {
                AppAlerts.ACTION_TIMER_STOP -> store.timersDismissFinished()
                AppAlerts.ACTION_TIMER_PLUS -> ringing.forEach { store.timerAddTime(it.id, 60_000) }
            }
            AppAlerts.cancel(app, AppAlerts.Kind.TIMER)
            AppAlerts.rescheduleFromSave(app)
        }
    }
}
