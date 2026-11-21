package app.pokeplan.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pokeplan.reminders.doAsync

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        doAsync { updateWidgets(context.applicationContext) }
    }
}

fun scheduleWidgetRefresh(context: Context, delayMillis: Long) {
    val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = PendingIntent.getBroadcast(
        context, 7, Intent(context, WidgetRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    try {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + delayMillis.coerceAtLeast(500), intent)
    } catch (e: Exception) {
    }
}
