package dev.arbrajab.lexiconandroid.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.arbrajab.lexiconandroid.LexiconApplication
import dev.arbrajab.lexiconandroid.data.repository.ReplayResult

/**
 * Drains the pending-query queue (oldest first) as soon as the network
 * constraint is satisfied. Enqueued with [ExistingWorkPolicy.KEEP] under a
 * fixed unique name so a query submitted while already offline doesn't spawn
 * a duplicate worker per tap — one drain pass handles everything queued so
 * far.
 */
class SyncQueueWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LexiconApplication
        val repository = app.queryRepository
        val pending = app.pendingQueryDao.getAll()
        if (pending.isEmpty()) return Result.success()

        // A permanently-failed item (attempts capped in QueryRepository) is dequeued for good and
        // must not keep this worker retrying — only an item that still has attempts left does.
        var needsRetry = false
        for (item in pending) {
            if (repository.replayPending(item) == ReplayResult.RETRYING) needsRetry = true
        }
        // Refresh corpus/document metadata too, so staleness flags settle in the same pass
        // that a user's queued questions actually got answered.
        runCatching { app.corpusRepository.refresh() }

        return if (needsRetry) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "sync_pending_queries"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(
                NetworkType.CONNECTED
            ).build()
            val request =
                OneTimeWorkRequestBuilder<SyncQueueWorker>()
                    .setConstraints(constraints)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
