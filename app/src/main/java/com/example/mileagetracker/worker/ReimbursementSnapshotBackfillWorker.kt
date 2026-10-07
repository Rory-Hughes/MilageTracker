package com.example.mileagetracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mileagetracker.domain.usecase.drive.BackfillReimbursementSnapshotsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-shot worker that repairs CRA reimbursement snapshots on existing trips.
 *
 * Bumps to [WORK_VERSION] whenever the backfill logic changes in a way that
 * needs to re-run on devices that already ran the previous version.  The
 * versioned [WORK_NAME] combined with [ExistingWorkPolicy.KEEP] means:
 *
 * - Same version → already enqueued / done, second call is a no-op.
 * - New version  → fresh enqueue under a new name, runs once.
 */
@HiltWorker
class ReimbursementSnapshotBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backfillUseCase: BackfillReimbursementSnapshotsUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        runCatching {
            when (val result = backfillUseCase()) {
                is BackfillReimbursementSnapshotsUseCase.Result.Completed -> {
                    Log.i(
                        TAG,
                        "Backfill completed: scanned=${result.scanned}, updated=${result.updated}",
                    )
                    Result.success()
                }

                BackfillReimbursementSnapshotsUseCase.Result.Skipped -> {
                    Log.d(TAG, "Backfill skipped (no user / no region / no rate seeded).")
                    Result.success()
                }
            }
        }.getOrElse { error ->
            Log.e(TAG, "Backfill worker failed unexpectedly: ${error.message}", error)
            Result.retry()
        }

    companion object {
        private const val TAG = "ReimbursementBackfillWorker"

        /**
         * Bump this when the backfill logic changes so devices that ran an
         * earlier version pick up the new pass.
         */
        private const val WORK_VERSION = 1
        private const val WORK_NAME = "reimbursement_snapshot_backfill_v$WORK_VERSION"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReimbursementSnapshotBackfillWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "Backfill worker enqueued under \"$WORK_NAME\" (KEEP policy).")
        }
    }
}
