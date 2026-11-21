package app.pokeplan

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import app.pokeplan.app.CrashLedger
import app.pokeplan.app.CrashLog
import java.io.File

object CrashGuard {
    private const val PREFS = "crash_guard"
    private const val TIMES = "times"
    private const val LOG_FILE = "crash.log"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val now = System.currentTimeMillis()
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().putString(TIMES, CrashLedger.encode(CrashLedger.record(CrashLedger.decode(prefs.getString(TIMES, null)), now))).commit()
                File(app.filesDir, LOG_FILE).writeText(CrashLog.format(now, BuildConfigVersion.name(app), thread.name, error))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun safeMode(context: Context): Boolean =
        CrashLedger.inCrashLoop(CrashLedger.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TIMES, null)), System.currentTimeMillis())

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TIMES).commit()
    }

    fun log(context: Context): String =
        runCatching { File(context.filesDir, LOG_FILE).takeIf { it.exists() }?.readText() }.getOrNull() ?: "No crash log was saved."

    fun copyLog(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PokéLife crash log", log(context)))
    }.isSuccess
}

private object BuildConfigVersion {
    fun name(context: Context): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
}

class PokeLifeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        try { BackupWorker.schedule(this); OffPhone.schedule(this) } catch (e: Exception) {  }
    }
}
