package app.pokeplan.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import app.pokeplan.widgets.WidgetArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The notification channel ids, in one place so a channel is created and posted to under the same name. */
internal object Channels {
    const val FOCUS_HALFWAY = "focus-halfway"
    // The ones with the game's notification sound are "-2": a channel keeps its sound for good, so a new id gives the old installs the new one.
    const val FOCUS_END = "focus-end-2"
    const val EVENING_NUDGES = "evening-nudges"
    const val TASK_REMINDERS = "task-reminders-2"
    const val CARE_ROUTINES = "care-routines-2"
}

/** Gives a channel the game's notification sound (res/raw/pokelife_notify, the Mystery Dungeon "notice" blip). */
internal fun NotificationChannel.withGameSound(context: Context): NotificationChannel = apply {
    setSound(
        android.net.Uri.parse("android.resource://${context.packageName}/raw/pokelife_notify"),
        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    )
}

/** Removes the channels this app had before they got the game's sound. */
internal fun NotificationManager.retireOldChannels() {
    listOf("focus-end", "task-reminders", "care-routines").forEach { deleteNotificationChannel(it) }
}

/** The preferences file that holds the task reminders' alarm codes; the app also keeps its "asked for notifications" flag there. */
internal const val TASK_REMINDERS_PREFS = "task_reminders"

/** Alarm helpers shared by the reminder features. */
internal object Alarms {
    /** Android limits the alarms an app may hold, so only the next few reminders are set and the rest wait for a reschedule. */
    const val MAX_ALARMS = 24

    /**
     * Sets alarms for the first [MAX_ALARMS] of [upcoming] and cancels the ones set earlier that are no longer wanted. The
     * codes set are kept in [prefsName]. [intent] builds the broadcast for an alarm code: with its item when setting, and
     * with null and `FLAG_NO_CREATE` when looking up a stale alarm to cancel.
     */
    fun <T> reschedule(
        context: Context, prefsName: String, upcoming: List<T>, code: (T) -> Int, atMillis: (T) -> Long,
        intent: (code: Int, item: T?, flags: Int) -> PendingIntent?,
    ) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val wanted = upcoming.take(MAX_ALARMS).associateBy(code)
        val before = prefs.getStringSet("codes", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
        for (stale in before) if (stale !in wanted) intent(stale, null, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        for ((c, item) in wanted) set(context, alarms, atMillis(item), intent(c, item, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        prefs.edit().putStringSet("codes", wanted.keys.map { it.toString() }.toSet()).apply()
    }

    /** On time where the phone allows exact alarms, otherwise as close as it lets us. */
    fun set(context: Context, alarms: AlarmManager, atMillis: Long, intent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
            else alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }
}

/** Runs [block] off the main thread and keeps the receiver alive until it ends (a receiver's own time is only about ten seconds). */
internal fun BroadcastReceiver.doAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** The small icon of a reminder: the item's picture, or the system's reminder icon when the picture is missing. */
internal fun notificationIcon(context: Context, item: String): Icon =
    WidgetArt.item(context, item, 1)?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(context, android.R.drawable.ic_popup_reminder)

internal fun notifySafely(context: Context, id: Int, notification: Notification) {
    try {
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    } catch (e: SecurityException) {
        // Notifications are not allowed: nothing to show.
    }
}
