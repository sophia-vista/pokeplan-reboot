package app.pokeplan.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import app.pokeplan.MainActivity
import app.pokeplan.app.AppData
import app.pokeplan.app.FocusAlert
import app.pokeplan.app.Nudges
import app.pokeplan.plan.FocusSession
import app.pokeplan.plan.Phase
import app.pokeplan.widgetStore
import app.pokeplan.widgets.WidgetArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The app's own notifications beyond task reminders: the halfway and end alerts of a focus session, the evening streak
 * reminder and the Sunday recap. When they are due comes from the pure rules in [Nudges]; this sets an alarm for each and
 * [NudgeReceiver] checks the save again when one goes off (the save may have changed since). The app calls [schedule]
 * whenever one of the times changes, and a fired alarm or a reboot calls [rescheduleFromSave].
 *
 * None of these counts as leaving the app: a notification posted by the app never pauses its activity, and none uses a
 * full-screen intent, so a running focus session never sees them as "Left".
 */
object AppAlerts {
    enum class Kind(val code: Int, val channel: String, val notificationId: Int) {
        HALFWAY(9101, "focus-halfway", 7101),
        END(9102, "focus-end", 7102),
        STREAK(9103, "evening-nudges", 7103),
        RECAP(9104, "evening-nudges", 7104),

        /** A streak reminder or recap that came due during a focus session and waits for its end. */
        STREAK_LATER(9105, "evening-nudges", 7103),
        RECAP_LATER(9106, "evening-nudges", 7104),
    }

    const val ACTION_ALERT = "app.pokeplan.reminders.ALERT"
    const val EXTRA_KIND = "kind"
    const val EXTRA_SESSION = "session"

    /** What the alarms should be: when each one goes off, and the running session they belong to. Equal plans need no new alarms. */
    data class Plan(val halfway: Long?, val end: Long?, val sessionId: String?, val streak: Long?, val recap: Long?)

    fun plan(data: AppData, nowMillis: Long, zone: TimeZone): Plan {
        val alerts = Nudges.focusAlerts(data.focus, data.settings, nowMillis)
        return Plan(
            halfway = alerts.firstOrNull { it.kind == FocusAlert.Kind.HALFWAY }?.atMillis,
            end = alerts.firstOrNull { it.kind == FocusAlert.Kind.END }?.atMillis,
            sessionId = data.focus?.takeIf { !it.isFinished }?.id,
            streak = Nudges.nextStreakCheck(data, nowMillis, zone),
            recap = Nudges.nextRecap(data, nowMillis, zone),
        )
    }

    fun schedule(context: Context, plan: Plan) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        // A session that ended, failed or was given up takes its halfway notification with it.
        if (plan.sessionId == null) cancel(context, Kind.HALFWAY)
        set(context, alarms, Kind.HALFWAY, plan.halfway, plan.sessionId, exact = true)
        set(context, alarms, Kind.END, plan.end, plan.sessionId, exact = true)
        set(context, alarms, Kind.STREAK, plan.streak, null, exact = false)
        set(context, alarms, Kind.RECAP, plan.recap, null, exact = false)
    }

    fun rescheduleFromSave(context: Context) {
        val store = widgetStore(context)
        schedule(context, plan(store.data, store.nowMillis, TimeZone.currentSystemDefault()))
    }

    /** Sets the alarm for [kind] at [atMillis], or cancels it when that is null (switched off, failed, given up, past). */
    internal fun set(context: Context, alarms: AlarmManager, kind: Kind, atMillis: Long?, sessionId: String?, exact: Boolean) {
        if (atMillis == null) {
            pending(context, kind, null, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
            return
        }
        val intent = pending(context, kind, sessionId, PendingIntent.FLAG_UPDATE_CURRENT)!!
        if (exact) TaskReminders.set(context, alarms, atMillis, intent) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
    }

    private fun pending(context: Context, kind: Kind, sessionId: String?, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, kind.code,
            Intent(context, NudgeReceiver::class.java).setAction(ACTION_ALERT).putExtra(EXTRA_KIND, kind.name).apply { sessionId?.let { putExtra(EXTRA_SESSION, it) } },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun channels(context: Context): NotificationManager? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        manager.createNotificationChannel(NotificationChannel("focus-halfway", "Focus halfway", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel("focus-end", "Focus time is up", NotificationManager.IMPORTANCE_DEFAULT))
        manager.createNotificationChannel(NotificationChannel("evening-nudges", "Streak and weekly recap", NotificationManager.IMPORTANCE_DEFAULT))
        return manager
    }

    fun show(context: Context, kind: Kind, title: String, text: String, tab: String) {
        val manager = channels(context) ?: return
        val open = PendingIntent.getActivity(
            context, kind.code, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = WidgetArt.item(context, "poke-ball", 1)?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(context, android.R.drawable.ic_popup_reminder)
        val notification = Notification.Builder(context, kind.channel)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(if (kind == Kind.HALFWAY || kind == Kind.END) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_RECOMMENDATION)
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply { if (kind == Kind.HALFWAY) setOnlyAlertOnce(true) }
            .build()
        try {
            manager.notify(kind.notificationId, notification)
        } catch (e: SecurityException) {
            // Notifications are not allowed: nothing to show.
        }
    }

    fun cancel(context: Context, kind: Kind) {
        context.getSystemService(NotificationManager::class.java)?.cancel(kind.notificationId)
    }

    /** The running session at [nowMillis], with a lapsed grace period or timer applied; null when there is none. */
    fun sessionAt(data: AppData, nowMillis: Long): FocusSession? = data.focus?.tick(nowMillis)
}

/** An alarm of [AppAlerts] going off. */
class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(AppAlerts.EXTRA_KIND)?.let { name -> AppAlerts.Kind.entries.firstOrNull { it.name == name } } ?: return
        val sessionId = intent.getStringExtra(AppAlerts.EXTRA_SESSION)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handle(app, kind, sessionId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handle(context: Context, kind: AppAlerts.Kind, sessionId: String?) {
        val store = widgetStore(context)
        val data = store.data
        val now = store.nowMillis
        val zone = TimeZone.currentSystemDefault()
        val session = AppAlerts.sessionAt(data, now)
        when (kind) {
            AppAlerts.Kind.HALFWAY -> {
                // Only for the session it was set for, and only while it is still going: not failed, given up or paused.
                if (session != null && session.id == sessionId && data.settings.focusHalfway && (session.phase is Phase.Running || session.phase is Phase.Away)) {
                    val minutes = ((session.remainingMillis(now) + 59_999) / 60_000).coerceAtLeast(1)
                    AppAlerts.show(context, kind, "Halfway there", "$minutes min to go. Your Egg is warming up.", "FOCUS")
                }
            }
            AppAlerts.Kind.END -> {
                if (session != null && session.id == sessionId && data.settings.focusEnd && session.phase !is Phase.Failed && session.phase !is Phase.Paused) {
                    AppAlerts.show(context, kind, "Focus time is up", "Open PokéPlan to see your Egg hatch.", "FOCUS")
                }
            }
            AppAlerts.Kind.STREAK, AppAlerts.Kind.STREAK_LATER -> {
                if (!deferDuringFocus(context, kind, session) && Nudges.streakAtRisk(data, now, zone)) {
                    val text = Nudges.streakText(data, now, zone)
                    AppAlerts.show(context, AppAlerts.Kind.STREAK, text.title, text.body, "HOME")
                }
            }
            AppAlerts.Kind.RECAP, AppAlerts.Kind.RECAP_LATER -> {
                if (!deferDuringFocus(context, kind, session)) {
                    Nudges.weeklyRecap(data, now, zone)?.let { AppAlerts.show(context, AppAlerts.Kind.RECAP, it.title, it.body, "ME") }
                }
            }
        }
        AppAlerts.rescheduleFromSave(context)
    }

    /** Evening notifications are silenced during focus: a running session sets them again for its end. */
    private fun deferDuringFocus(context: Context, kind: AppAlerts.Kind, session: FocusSession?): Boolean {
        if (session == null || session.isFinished) return false
        val later = if (kind == AppAlerts.Kind.STREAK || kind == AppAlerts.Kind.STREAK_LATER) AppAlerts.Kind.STREAK_LATER else AppAlerts.Kind.RECAP_LATER
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return true
        AppAlerts.set(context, alarms, later, session.startedAtMillis + session.pausedMillis + session.durationMillis + 60_000L, null, exact = false)
        return true
    }
}
