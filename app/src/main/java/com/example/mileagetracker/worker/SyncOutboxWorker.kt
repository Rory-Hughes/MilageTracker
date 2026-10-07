package com.example.mileagetracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mileagetracker.domain.auth.SignedInUserRequiredException
import com.example.mileagetracker.domain.model.CloudUploadConsentRequiredException
import com.example.mileagetracker.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncOutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        runCatching { syncRepository.push() }.fold(
            onSuccess = { Result.success() },
            onFailure = ::mapFailureToResult,
        )

    private fun mapFailureToResult(error: Throwable): Result =
        when (error) {
            is SignedInUserRequiredException -> Result.success()
            is CloudUploadConsentRequiredException -> Result.success()
            else -> {
                Log.w(TAG, "Sync push failed; retrying outbox work.", error)
                Result.retry()
            }
        }

    companion object {
        private const val TAG = "SyncOutboxWorker"
        private const val UNIQUE_WORK_NAME = "sync-outbox"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncOutboxWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
