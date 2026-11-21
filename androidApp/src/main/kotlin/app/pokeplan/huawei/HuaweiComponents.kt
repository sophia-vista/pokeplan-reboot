package app.pokeplan.huawei

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

internal object HuaweiComponents {
    fun rebind(context: Context): Boolean {
        if (context.packageName !in NotificationManagerCompat.getEnabledListenerPackages(context)) return false
        return runCatching { NotificationListenerService.requestRebind(ComponentName(context, HuaweiNotificationService::class.java)) }.isSuccess
    }
}
