package app.pokeplan

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The daily backup, on WorkManager's schedule instead of "when the app happens to be opened": it runs about once a day even if the app is not,
 * survives a restart of the phone, and waits while storage is low. It takes a backup only if the newest is old enough ([app.pokeplan.data.LiveBackup.takeIfDue]).
 * Opening the store no longer takes one (that put AES sealing and file writes on the launching thread); [runSoon] asks for it at start.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = LiveStore.get(applicationContext) ?: return transientFailure()
        val backups = LiveStore.backups(applicationContext) ?: return transientFailure()
        return try {
            withContext(Dispatchers.IO) { backups.takeIfDue(store.live, System.currentTimeMillis()) }
            Result.success()
        } catch (e: Exception) {
            Log.w("BackupWorker", "backup failed: ${e.javaClass.simpleName}")
            transientFailure()
        }
    }

    /** A database that cannot be opened will not mend by trying for ever; retry twice, then stop this run. */
    private fun transientFailure(): Result = if (runAttemptCount >= 2) Result.failure() else Result.retry()

    companion object {
        private const val NAME = "daily-backup"
        private const val NOW = "daily-backup-now"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /**
         * Takes a due backup shortly after the app starts, off the main thread: the store open no longer does it, because
         * that could block the first frame with AES sealing and file writes. KEEP means one launch a day is enough.
         */
        fun runSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }
    }
}
