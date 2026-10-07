package com.example.mileagetracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mileagetracker.domain.usecase.drive.BackfillTripAddressesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-shot worker that re-resolves trip addresses stored in the old verbose
 * (Nominatim `display_name`) or raw-coordinate format into the clean
 * `"Street, City, NB, Canada"` format.
 *
 * Runs only when a network is available (it makes reverse-geocode calls) and
 * retries on failure. Versioned [WORK_NAME] + [ExistingWorkPolicy.KEEP] means a
 * second enqueue is a no-op until the version is bumped.
 */
@HiltWorker
class TripAddressBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backfillUseCase: BackfillTripAddressesUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        runCatching {
            when (val result = backfillUseCase()) {
                is BackfillTripAddressesUseCase.Result.Completed -> {
                    Log.i(
                        TAG,
                        "Address backfill completed: scanned=${result.scanned}, " +
                            "updated=${result.updated}",
                    )
                    Result.success()
                }

                BackfillTripAddressesUseCase.Result.Skipped -> {
                    Log.d(TAG, "Address backfill skipped (no signed-in user).")
                    Result.success()
                }
            }
        }.getOrElse { error ->
            Log.e(TAG, "Address backfill worker failed: ${error.message}", error)
            Result.retry()
        }

    companion object {
        private const val TAG = "TripAddressBackfillWorker"

        /** Bump to force a re-run on devices that already ran an earlier pass. */
        private const val WORK_VERSION = 1
        private const val WORK_NAME = "trip_address_backfill_v$WORK_VERSION"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<TripAddressBackfillWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "Trip-address backfill enqueued under \"$WORK_NAME\" (KEEP policy).")
        }
    }
}
