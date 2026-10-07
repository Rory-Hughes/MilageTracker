package com.example.mileagetracker.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mileagetracker.MainActivity
import com.example.mileagetracker.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@HiltWorker
class OdometerEvidenceReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val checker: OdometerEvidenceReminderChecker,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        runCatching {
            if (!settingsRepository.getOdometerPhotoReminder().first()) {
                Log.d(TAG, "Odometer photo reminder disabled.")
                return@runCatching Result.success()
            }

            val missingCount = checker.countVehiclesMissingOpeningEvidence(LocalDate.now())
            if (missingCount > 0) {
                showReminder(missingCount)
            }
            Result.success()
        }.getOrElse { error ->
            Log.e(TAG, "Odometer evidence reminder failed: ${error.message}", error)
            Result.retry()
        }

    private fun showReminder(missingCount: Int) {
        ensureChannel()
        if (!hasNotificationPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS permission missing; skipping odometer reminder.")
            return
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Take odometer photos")
            .setContentText("Take odometer photos for your active vehicles.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$missingCount active vehicle(s) need opening odometer evidence for this tax year.",
                ),
            )
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    REQUEST_CONTENT,
                    Intent(applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification permission denied: ${error.message}", error)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Odometer Evidence",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Reminds you to capture CRA odometer photo evidence"
        }
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "OdometerEvidenceReminder"
        private const val WORK_NAME = "odometer_evidence_reminder_periodic"
        private const val CHANNEL_ID = "odometer_evidence"
        private const val NOTIFICATION_ID = 2401
        private const val REQUEST_CONTENT = 2402
        private const val REPEAT_INTERVAL_DAYS = 1L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<OdometerEvidenceReminderWorker>(
                REPEAT_INTERVAL_DAYS,
                TimeUnit.DAYS,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )

            Log.d(TAG, "Periodic odometer evidence reminder scheduled.")
        }
    }
}
