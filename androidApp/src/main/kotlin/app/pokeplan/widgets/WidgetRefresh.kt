package app.pokeplan.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Refreshes the widgets when an alarm goes off. A pet or an event shows a still picture for a few seconds and a delayed
 * update then brings the animation back; that update used to run inside the short-lived tap handler, which the system can
 * end early and leave the buddy frozen. The alarm survives that.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updateWidgets(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Refreshes the widgets in [delayMillis]. A newer request replaces an older one. */
fun scheduleWidgetRefresh(context: Context, delayMillis: Long) {
    val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = PendingIntent.getBroadcast(
        context, 7, Intent(context, WidgetRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    try {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + delayMillis.coerceAtLeast(500), intent)
    } catch (e: Exception) {
        // Alarms are best effort: the next update brings the animation back anyway.
    }
}
