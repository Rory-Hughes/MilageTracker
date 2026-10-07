package com.example.mileagetracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mileagetracker.domain.repository.ReportHistoryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker that prunes report-history rows and cached files
 * once they are older than 7 years.
 */
@HiltWorker
class ReportHistoryCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ReportHistoryRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        runCatching {
            val threshold = System.currentTimeMillis() - SEVEN_YEAR_MS
            repository.deleteOlderThan(threshold)
            Log.i(TAG, "Cleanup complete: pruned records older than 7 years (threshold=$threshold).")
            Result.success()
        }.fold(
            onSuccess = { it },
            onFailure = { error ->
                if (error is IllegalArgumentException) {
                    Log.e(TAG, "Cleanup rejected by retention guard: ${error.message}", error)
                    Result.failure()
                } else {
                    Log.e(TAG, "Cleanup failed unexpectedly: ${error.message}", error)
                    Result.retry()
                }
            },
        )

    companion object {
        private const val TAG = "ReportHistoryCleanupWorker"
        private const val WORK_NAME = "report_history_cleanup_periodic"
        private const val REPEAT_INTERVAL_DAYS = 1L

        internal const val SEVEN_YEAR_MS = 7L * 365 * 24 * 60 * 60 * 1_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReportHistoryCleanupWorker>(
                REPEAT_INTERVAL_DAYS,
                TimeUnit.DAYS,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )

            Log.d(TAG, "Periodic cleanup worker scheduled (KEEP policy).")
        }
    }
}
