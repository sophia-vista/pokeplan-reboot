package app.pokeplan

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import app.pokeplan.app.CrashLedger
import app.pokeplan.app.CrashLog
import java.io.File

/**
 * Crash handling for the whole process. An uncaught exception is written down (when it happened, and a log with no exception messages) before the
 * process dies, so the next start can tell a crash loop from one bad moment: more than two crashes within a minute start the launcher in
 * safe mode ([safeMode]), a plain list of apps with the log to copy, so the phone can always be used to open its apps. The previous handler
 * still runs, so the system's own crash reporting is unchanged.
 */
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
                // commit(), not apply(): the process is about to die and an asynchronous write would be lost.
                prefs.edit().putString(TIMES, CrashLedger.encode(CrashLedger.record(CrashLedger.decode(prefs.getString(TIMES, null)), now))).commit()
                File(app.filesDir, LOG_FILE).writeText(CrashLog.format(now, BuildConfigVersion.name(app), thread.name, error))
            } catch (_: Throwable) {
                // Never let the recording of a crash hide the crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** True when the app has crashed more than twice within the last minute. */
    fun safeMode(context: Context): Boolean =
        CrashLedger.inCrashLoop(CrashLedger.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TIMES, null)), System.currentTimeMillis())

    /** Forgets the crashes, so the next start is a normal one ("Try again" in safe mode). */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TIMES).commit()
    }

    /** The last crash log, or a line saying there is none. */
    fun log(context: Context): String =
        runCatching { File(context.filesDir, LOG_FILE).takeIf { it.exists() }?.readText() }.getOrNull() ?: "No crash log was saved."

    fun copyLog(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("PokéLife crash log", log(context)))
    }.isSuccess
}

private object BuildConfigVersion {
    fun name(context: Context): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
}

/** Installs [CrashGuard] before anything else in the process starts. */
class PokeLifeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        try { BackupWorker.schedule(this); OffPhone.schedule(this) } catch (e: Exception) { /* WorkManager could not start: the app still backs up when it opens */ }
    }
}
