package app.pokeplan

import android.content.Context
import android.util.Log
import app.pokeplan.reminders.AppAlerts
import app.pokeplan.reminders.SharedReminders
import java.io.File

/**
 * What "Erase this device" clears besides the save itself (owner, 20 November 2026): the alarms already set, then every preferences file the app has made
 * (the launcher's layout, the weather, the off-phone backup folder and its sealed passphrase, the reminder and alarm bookkeeping, the crash guard, the
 * widget's reaction) and every leftover save file. It goes through the folder rather than a list of names, so a store added later is cleared too;
 * `DeviceResetTest` fails if anything is left.
 */
internal object DeviceReset {
    private const val TAG = "DeviceReset"

    fun clear(context: Context) {
        val app = context.applicationContext
        // The off-phone setup first: it releases the folder the player chose before its preferences are wiped. What is already in the folder is theirs.
        runCatching { OffPhone.forget(app) }.onFailure { Log.w(TAG, "off-phone forget failed: ${it.javaClass.simpleName}") }
        // The save is already empty: planning again from it cancels every alarm that was set for what is gone.
        runCatching { SharedReminders.reschedule(app) }.onFailure { Log.w(TAG, "reminders reschedule failed: ${it.javaClass.simpleName}") }
        runCatching { AppAlerts.rescheduleFromSave(app) }.onFailure { Log.w(TAG, "alerts reschedule failed: ${it.javaClass.simpleName}") }
        preferenceNames(app).forEach { name -> app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
        listOf("save.json", "care.json").forEach { File(app.filesDir, it).delete() }
    }

    /** Every preferences file the app has written, by name. */
    fun preferenceNames(context: Context): List<String> =
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles { f -> f.name.endsWith(".xml") }.orEmpty().map { it.name.removeSuffix(".xml") }
}
