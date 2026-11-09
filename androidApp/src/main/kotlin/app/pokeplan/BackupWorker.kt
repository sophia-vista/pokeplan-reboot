package app.pokeplan

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * The daily backup, on WorkManager's schedule instead of "when the app happens to be opened": it runs about once a day even if the app is not,
 * survives a restart of the phone, and waits while storage is low. It takes a backup only if the newest is old enough ([app.pokeplan.data.LiveBackup.takeIfDue]),
 * so it and the backup the app makes when it starts never double up.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = LiveStore.get(applicationContext) ?: return Result.retry()
        val backups = LiveStore.backups(applicationContext) ?: return Result.retry()
        return try {
            backups.takeIfDue(store.live, System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            // No document text in the log (privacy): only what failed.
            Log.w("BackupWorker", "backup failed: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "daily-backup"

        /** Makes sure the daily job is queued (a second call changes nothing). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
