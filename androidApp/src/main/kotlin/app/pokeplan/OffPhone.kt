package app.pokeplan

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pokeplan.data.BackupFolder
import app.pokeplan.data.DataStore
import app.pokeplan.data.KeystoreOffPhoneKey
import app.pokeplan.data.LiveDocuments
import app.pokeplan.data.OffPhoneBackup
import java.util.concurrent.TimeUnit

/**
 * Automatic backup off the phone (REP-034, `DATA_AND_MIGRATIONS.md` section 10.7): the folder the player picked once, the passphrase kept sealed with a Keystore key
 * on this phone only, when the last verified copy was made, and the last problem. The copies themselves are [OffPhoneBackup]'s.
 *
 * The passphrase is what opens a copy on any other phone; here it is kept only so the daily job can seal without asking, as `m1.` text no other phone can open.
 * Nothing in the log but what failed.
 */
object OffPhone {
    private const val TAG = "OffPhone"
    private const val PREFS = "offphone"
    private const val TREE = "tree"
    private const val PASS = "pass"
    private const val LAST_OK = "last_ok"
    private const val PROBLEM = "problem"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun treeOf(context: Context): Uri? = prefs(context).getString(TREE, null)?.let(Uri::parse)

    fun isSetUp(context: Context): Boolean = treeOf(context) != null && prefs(context).contains(PASS)

    /** What the Settings card shows. [lastMillis] is the newest verified copy this phone made; [problem] is the last thing that went wrong, cleared by the next good copy. */
    data class Status(val folderName: String, val lastMillis: Long?, val problem: String?)

    fun status(context: Context): Status? {
        val tree = treeOf(context)?.takeIf { isSetUp(context) } ?: return null
        val p = prefs(context)
        val usable = hasPermission(context, tree)
        return Status(
            SafBackupFolder.nameOf(context, tree) ?: "the folder", p.getLong(LAST_OK, 0L).takeIf { it > 0 },
            if (!usable) "The folder is not available any more. Choose it again." else p.getString(PROBLEM, null),
        )
    }

    private fun hasPermission(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }

    /** Remembers the folder and the sealed passphrase. False if the Keystore cannot seal it here, in which case nothing is kept. */
    fun configure(context: Context, tree: Uri, passphrase: String): Boolean {
        val sealed = runCatching { KeystoreOffPhoneKey.cipher().seal(passphrase) }.onFailure { Log.w(TAG, "no key to keep the passphrase: ${it.javaClass.simpleName}") }.getOrNull() ?: return false
        prefs(context).edit().putString(TREE, tree.toString()).putString(PASS, sealed).remove(LAST_OK).remove(PROBLEM).apply()
        return true
    }

    /** Stops the copies and forgets the folder and the passphrase. What is already in the folder is the player's and is left there. */
    fun forget(context: Context) {
        val tree = treeOf(context)
        prefs(context).edit().clear().apply()
        if (tree != null) runCatching { context.contentResolver.releasePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }

    private fun passphrase(context: Context): String? =
        prefs(context).getString(PASS, null)?.let { sealed -> runCatching { KeystoreOffPhoneKey.cipher().open(sealed) }.getOrNull() }

    /** The newest copy in the folder, for a restore to stage; null (with a reason) if there is none. */
    fun newestCopy(context: Context, folder: BackupFolder? = null): Pair<String?, String?> {
        val f = folder ?: treeOf(context)?.takeIf { hasPermission(context, it) }?.let { SafBackupFolder(context, it) } ?: return null to "The folder is not available. Choose it again."
        val b = OffPhoneBackup(f)
        val copy = runCatching { b.copies().firstOrNull() }.getOrNull() ?: return null to "There is no copy in the folder yet."
        val text = b.read(copy) ?: return null to "The newest copy could not be read."
        return text to null
    }

    /**
     * Makes a copy if one is due (or [force]d) and returns a short note for the player. Never throws. [folder] is for tests; the app uses the chosen folder.
     */
    @Synchronized
    fun run(context: Context, force: Boolean, now: Long = System.currentTimeMillis(), folder: BackupFolder? = null): String {
        val p = prefs(context)
        val tree = treeOf(context)
        if (tree == null || !p.contains(PASS)) return "Backup off the phone is not set up."
        val target = folder ?: if (hasPermission(context, tree)) SafBackupFolder(context, tree) else return problem(p, "The folder is not available any more. Choose it again.")
        val pass = passphrase(context) ?: return problem(p, "The passphrase could not be read on this phone. Set the backup up again.")
        val store = LiveStore.get(context) ?: return problem(p, "The data could not be opened, so no copy was made.")
        val devices = listOfNotNull(store.mintCipher?.let { LiveDocuments.MINT to it }, store.healCipher?.let { LiveDocuments.HEAL to it }).toMap()
        return try {
            val backup = OffPhoneBackup(target)
            if (!force && !backup.due(now)) return "A recent copy is already there."
            when (val r = backup.run(store.live, devices, pass, DataStore.SCHEMA_VERSION, now)) {
                is OffPhoneBackup.Result.Done -> { p.edit().putLong(LAST_OK, r.millis).remove(PROBLEM).apply(); "Copied to the folder." }
                is OffPhoneBackup.Result.Failed -> problem(p, r.why)
            }
        } catch (e: Exception) {
            Log.w(TAG, "copy failed: ${e.javaClass.simpleName}")
            problem(p, "The copy could not be made. It will be tried again.")
        }
    }

    private fun problem(p: android.content.SharedPreferences, why: String): String { p.edit().putString(PROBLEM, why).apply(); return why }

    /** The daily job. */
    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!isSetUp(applicationContext)) return Result.success()
            // A folder that is gone or a passphrase that cannot be read will not mend by retrying; the card says so. Only a store that is not open yet is retried.
            if (LiveStore.get(applicationContext) == null) return Result.retry()
            run(applicationContext, force = false)
            return Result.success()
        }
    }

    private const val WORK = "offphone-backup"

    /** Makes sure the daily job is queued (a second call changes nothing). It does nothing until the player has set the backup up. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS).setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build()).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
