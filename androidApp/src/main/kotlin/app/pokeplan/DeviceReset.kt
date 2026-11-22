package app.pokeplan

import android.content.Context
import android.util.Log
import app.pokeplan.reminders.AppAlerts
import app.pokeplan.reminders.SharedReminders
import java.io.File

internal object DeviceReset {
    private const val TAG = "DeviceReset"

    fun clear(context: Context) {
        val app = context.applicationContext
        runCatching { OffPhone.forget(app) }.onFailure { Log.w(TAG, "off-phone forget failed: ${it.javaClass.simpleName}") }
        runCatching { SharedReminders.reschedule(app) }.onFailure { Log.w(TAG, "reminders reschedule failed: ${it.javaClass.simpleName}") }
        runCatching { AppAlerts.rescheduleFromSave(app) }.onFailure { Log.w(TAG, "alerts reschedule failed: ${it.javaClass.simpleName}") }
        preferenceNames(app).forEach { name -> app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
        listOf("save.json", "care.json").forEach { File(app.filesDir, it).delete() }
    }

    fun preferenceNames(context: Context): List<String> =
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles { f -> f.name.endsWith(".xml") }.orEmpty().map { it.name.removeSuffix(".xml") }
}
