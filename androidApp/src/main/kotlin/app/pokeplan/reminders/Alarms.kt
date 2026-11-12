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
    const val WORLD_JOBS = "world-jobs"
    const val GEAR_TIMERS = "gear-timers"
    const val TASK_REMINDERS = "task-reminders-2"
    const val CARE_ROUTINES = "care-routines-2"
    const val HEAL_HYDRATION = "heal-hydration"
    const val MINT_BILLS = "mint-bills"
    const val HOME_UPKEEP = "home-upkeep"
    const val LINK_NOTICES = "link-notices"
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
     * Sets alarms for the first [MAX_ALARMS] of [upcoming] and cancels the ones set earlier that are no longer wanted. Each alarm is known by its [key]
     * (kept in [prefsName]); [intent] builds the broadcast for a key, with its item when setting and with null and `FLAG_NO_CREATE` when looking up a
     * stale alarm to cancel, and must make the key part of the intent's identity (its data), so two keys never share an alarm whatever their request
     * codes. [legacy] finds an alarm an older version set under a bare request code, to cancel those once.
     */
    fun <T> reschedule(
        context: Context, prefsName: String, upcoming: List<T>, key: (T) -> String, atMillis: (T) -> Long,
        intent: (key: String, item: T?, flags: Int) -> PendingIntent?,
        legacy: ((code: Int) -> PendingIntent?)? = null,
    ) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val wanted = upcoming.take(MAX_ALARMS).associateBy(key)
        val before = prefs.getStringSet(KEYS, emptySet()).orEmpty()
        for (stale in before) if (stale !in wanted) intent(stale, null, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        if (legacy != null && prefs.contains(LEGACY_CODES)) {
            prefs.getStringSet(LEGACY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.forEach { code -> legacy(code)?.let { alarms.cancel(it); it.cancel() } }
        }
        for ((k, item) in wanted) set(context, alarms, atMillis(item), intent(k, item, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        prefs.edit().putStringSet(KEYS, wanted.keys.toSet()).remove(LEGACY_CODES).apply()
    }

    private const val KEYS = "keys"
    private const val LEGACY_CODES = "codes"

    /** Whether the phone lets this app set exact alarms right now (the player can switch that off under Android 12 and 13; Android 14 grants it to alarm apps). */
    fun canSetExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    /**
     * On time where the phone allows exact alarms; otherwise as close as it lets us, which can be minutes late. Returns whether the alarm is exact,
     * and never hides the other case: it is logged, and [canSetExact] is what Settings shows with a button to the phone's permission screen.
     */
    fun set(context: Context, alarms: AlarmManager, atMillis: Long, intent: PendingIntent): Boolean {
        val exact = try {
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) false
            else { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent); true }
        } catch (e: SecurityException) {
            false
        }
        if (!exact) {
            android.util.Log.w("Alarms", "exact alarms are not allowed; a reminder is set as an inexact alarm and can be late")
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
        return exact
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
