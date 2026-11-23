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

internal object Channels {
    const val FOCUS_HALFWAY = "focus-halfway"
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
    const val APP_UPDATES = "app-updates"
}

internal fun NotificationChannel.withGameSound(context: Context): NotificationChannel = apply {
    setSound(
        android.net.Uri.parse("android.resource://${context.packageName}/raw/pokelife_notify"),
        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
    )
}

internal fun NotificationManager.retireOldChannels() {
    listOf("focus-end", "task-reminders", "care-routines").forEach { deleteNotificationChannel(it) }
}

internal const val TASK_REMINDERS_PREFS = "task_reminders"

internal object Alarms {
    const val MAX_ALARMS = 24

    fun <T> reschedule(
        context: Context, prefsName: String, upcoming: List<T>, key: (T) -> String, atMillis: (T) -> Long,
        intent: (key: String, item: T?, flags: Int) -> PendingIntent?,
        legacy: ((code: Int) -> PendingIntent?)? = null,
        /** Set after a reboot or a clock change: the phone no longer holds the alarms, though their times look unchanged. */
        force: Boolean = false,
    ) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val wanted = upcoming.take(MAX_ALARMS).associateBy(key)
        val times = wanted.mapValues { (_, item) -> atMillis(item) }
        val before = prefs.getStringSet(KEYS, emptySet()).orEmpty()
        val beforeTimes = prefs.getStringSet(TIMES, emptySet()).orEmpty().mapNotNull { entry ->
            val at = entry.lastIndexOf('@')
            if (at <= 0) null else entry.substring(0, at) to entry.substring(at + 1).toLongOrNull()
        }.toMap()
        for (stale in before) if (stale !in wanted) intent(stale, null, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        if (legacy != null && prefs.contains(LEGACY_CODES)) {
            prefs.getStringSet(LEGACY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.forEach { code -> legacy(code)?.let { alarms.cancel(it); it.cancel() } }
        }
        for ((k, item) in wanted) {
            // Re-arming an alarm at the time it already has is what Doze throttles; only a changed one is set. A lookup with
            // FLAG_NO_CREATE catches one the system lost (a force-stop, a reboot whose broadcast was missed) and sets it again.
            val exists = intent(k, item, PendingIntent.FLAG_NO_CREATE) != null
            if (force || beforeTimes[k] != times[k] || !exists) set(context, alarms, times.getValue(k), intent(k, item, PendingIntent.FLAG_UPDATE_CURRENT)!!)
        }
        prefs.edit()
            .putStringSet(KEYS, wanted.keys.toSet())
            .putStringSet(TIMES, wanted.map { (k, item) -> "$k@${times.getValue(k)}" }.toSet())
            .remove(LEGACY_CODES).apply()
    }

    private const val KEYS = "keys"
    private const val TIMES = "times"
    private const val LEGACY_CODES = "codes"

    fun canSetExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

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

internal fun notificationIcon(context: Context, item: String): Icon =
    WidgetArt.item(context, item, 1)?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(context, android.R.drawable.ic_popup_reminder)

internal fun notifySafely(context: Context, id: Int, notification: Notification) {
    try {
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    } catch (e: SecurityException) {
    }
}
