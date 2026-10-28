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
import app.pokeplan.app.CareReminders
import app.pokeplan.app.RoutineReminder
import app.pokeplan.careStore
import app.pokeplan.widgetStore
import app.pokeplan.widgets.WidgetArt
import app.pokeplan.widgets.updateWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Care's gentle routine reminders as notifications (the rules are in [CareReminders]): one at a routine's own time, with
 * Done and "In 30 min", never repeated. The app calls [reschedule] whenever the routines or a focus session change; a fired
 * reminder and a reboot set them again from the saves.
 */
object RoutineReminders {
    private const val CHANNEL = "care-routines"
    private const val PREFS = "routine_reminders"
    private const val MAX_ALARMS = 24
    private const val LATER_MILLIS = 30 * 60_000L

    const val ACTION_FIRE = "app.pokeplan.reminders.ROUTINE_FIRE"
    const val ACTION_DONE = "app.pokeplan.reminders.ROUTINE_DONE"
    const val ACTION_LATER = "app.pokeplan.reminders.ROUTINE_LATER"
    const val EXTRA_ROUTINE = "routine"
    const val EXTRA_DAY = "day"

    fun reschedule(context: Context, upcoming: List<RoutineReminder>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wanted = upcoming.take(MAX_ALARMS).associateBy { code(it.routineId, it.day) }
        val before = prefs.getStringSet("codes", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
        for (code in before) if (code !in wanted) fireIntent(context, code, null, null, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        for ((code, r) in wanted) TaskReminders.set(context, alarms, r.atMillis, fireIntent(context, code, r.routineId, r.day, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        prefs.edit().putStringSet("codes", wanted.keys.map { it.toString() }.toSet()).apply()
    }

    fun rescheduleFromSave(context: Context) {
        val game = widgetStore(context)
        val care = careStore(context, game)
        reschedule(context, CareReminders.upcoming(care.data, game.data, game.nowMillis, TimeZone.currentSystemDefault()))
    }

    private fun code(routineId: String, day: LocalDate) = ("routine" to (routineId to day.toEpochDays())).hashCode()

    private fun fireIntent(context: Context, code: Int, routineId: String?, day: LocalDate?, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, RoutineReminderReceiver::class.java).setAction(ACTION_FIRE).apply {
                routineId?.let { putExtra(EXTRA_ROUTINE, it) }
                day?.let { putExtra(EXTRA_DAY, it.toEpochDays()) }
            },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Shows the routine again in 30 minutes, the same day. */
    fun later(context: Context, routineId: String, day: LocalDate) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        TaskReminders.set(context, alarms, System.currentTimeMillis() + LATER_MILLIS, fireIntent(context, code(routineId, day) + 1, routineId, day, PendingIntent.FLAG_UPDATE_CURRENT)!!)
    }

    fun notificationId(routineId: String) = ("routine" to routineId).hashCode()

    fun show(context: Context, routineId: String, title: String, day: LocalDate) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Care routines", NotificationManager.IMPORTANCE_DEFAULT))
        val id = notificationId(routineId)
        fun action(action: String) = PendingIntent.getBroadcast(
            context, id + action.hashCode(),
            Intent(context, RoutineReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ROUTINE, routineId).putExtra(EXTRA_DAY, day.toEpochDays()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "CARE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = WidgetArt.item(context, "oaks-parcel", 1)?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(context, android.R.drawable.ic_popup_reminder)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText("A small thing for you, whenever you're ready.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Done", action(ACTION_DONE)).build())
            .addAction(Notification.Action.Builder(null, "In 30 min", action(ACTION_LATER)).build())
            .build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications are not allowed: nothing to show.
        }
    }
}

/** A routine reminder going off, or Done or "In 30 min" tapped on one. */
class RoutineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra(RoutineReminders.EXTRA_ROUTINE) ?: return
        val day = LocalDate.fromEpochDays(intent.getLongExtra(RoutineReminders.EXTRA_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.toInt() ?: return)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handle(app, intent.action, routineId, day)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String?, routineId: String, day: LocalDate) {
        val game = widgetStore(context)
        val care = careStore(context, game)
        val manager = context.getSystemService(NotificationManager::class.java)
        when (action) {
            RoutineReminders.ACTION_FIRE -> {
                val session = game.data.focus?.takeIf { !it.isFinished }
                val routine = care.data.routines.firstOrNull { it.id == routineId }
                if (routine != null && CareReminders.stillWanted(care.data, game.data, routineId, day) && day == care.today) {
                    // A reminder that fires during focus waits for the session's end (rescheduling below sets it there).
                    if (session == null) RoutineReminders.show(context, routineId, routine.title, day)
                }
            }
            RoutineReminders.ACTION_DONE -> {
                // Ticked like on the Care screen, and paid the same way (into the shared save); the app picks it up when it comes back.
                if (day == care.today && care.data.routines.any { it.id == routineId && !it.doneOn(day) }) care.completeRoutine(routineId)
                manager?.cancel(RoutineReminders.notificationId(routineId))
                updateWidgets(context)
            }
            RoutineReminders.ACTION_LATER -> {
                RoutineReminders.later(context, routineId, day)
                manager?.cancel(RoutineReminders.notificationId(routineId))
            }
        }
        RoutineReminders.rescheduleFromSave(context)
    }
}
