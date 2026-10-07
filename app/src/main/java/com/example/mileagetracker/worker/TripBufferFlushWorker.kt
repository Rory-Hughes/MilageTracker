package com.example.mileagetracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mileagetracker.domain.usecase.tracking.FlushTripBufferUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager periodic worker that drains the `buffered_trips` write-ahead
 * table by delegating to [FlushTripBufferUseCase].
 *
 * ## Scheduling
 * Enqueued with [ExistingPeriodicWorkPolicy.KEEP] so that if the worker is
 * already scheduled a second call to [schedule] is a no-op.  The repeat
 * interval is [REPEAT_INTERVAL_MINUTES] (15 minutes — the WorkManager
 * minimum).
 *
 * ## Constraints
 * No network constraints are applied because this worker flushes buffered trips
 * to the local Room database only. This keeps retries running offline and avoids
 * delaying local durability when connectivity is unavailable.
 *
 * ## Hilt injection
 * [@HiltWorker][HiltWorker] + [@AssistedInject][AssistedInject] is the
 * standard pattern for injecting Hilt-managed dependencies into a Worker.
 * The [androidx.hilt:hilt-work] artifact (added in Task 2.2) provides the
 * [HiltWorkerFactory] that WorkManager must use — wire it in [MileageTrackerApp]
 * by implementing [androidx.work.Configuration.Provider].
 *
 * ## Result policy
 * - [Result.success] if [FlushTripBufferUseCase] runs without throwing.
 * - [Result.retry] if an unexpected exception escapes the use case (the
 *   use case itself handles per-entry failures internally).
 */
@HiltWorker
class TripBufferFlushWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val flushTripBufferUseCase: FlushTripBufferUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        runCatching {
            val result = flushTripBufferUseCase()
            Log.i(
                TAG,
                "Worker completed: flushed=${result.flushed} failed=${result.failed} remaining=${result.remaining}"
            )
            Result.success()
        }.getOrElse { error ->
            Log.e(TAG, "Worker failed unexpectedly: ${error.message}", error)
            Result.retry()
        }

    companion object {
        private const val TAG = "TripBufferFlushWorker"
        private const val WORK_NAME = "trip_buffer_flush_periodic"
        private const val REPEAT_INTERVAL_MINUTES = 15L

        /**
         * Enqueues the periodic flush worker if it is not already scheduled.
         *
         * Call this from [MileageApplication.onCreate] or from the Activity
         * on first launch.  Safe to call multiple times —
         * [ExistingPeriodicWorkPolicy.KEEP] prevents duplicate enqueues.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TripBufferFlushWorker>(
                REPEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )

            Log.d(TAG, "Periodic flush worker scheduled (KEEP policy)")
        }
    }
}
