package app.pokeplan.platform

import android.content.Context
import android.content.Intent
import app.pokeplan.ui.AppLauncher
import app.pokeplan.ui.InstalledApp

/**
 * Lists the apps with a launcher entry and starts one by package (MINT.md 4.3C). The list is read from the phone's package manager each time it is asked for (the manifest
 * already holds `QUERY_ALL_PACKAGES` for the launcher, personal use) and is not kept or sent anywhere; PokéLife itself is left out. [open] starts the app's own launcher
 * activity as a new task and does nothing else.
 */
class AndroidAppLauncher(private val context: Context) : AppLauncher {
    override fun installed(): List<InstalledApp> = try {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(main, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    } catch (e: Exception) {
        emptyList()
    }

    override fun label(packageName: String): String? = try {
        val pm = context.packageManager
        if (pm.getLaunchIntentForPackage(packageName) == null) null else pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
        null
    }

    override fun open(packageName: String): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent == null) false else { context.startActivity(intent); true }
    } catch (e: Exception) {
        false
    }
}
