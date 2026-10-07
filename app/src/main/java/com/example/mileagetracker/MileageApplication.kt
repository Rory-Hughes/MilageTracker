package com.example.mileagetracker

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.libraries.places.api.Places
import com.example.mileagetracker.data.preferences.LocalePreferencesRepository
import com.example.mileagetracker.data.seeder.CraRateSeeder
import com.example.mileagetracker.domain.model.AppLanguage
import com.example.mileagetracker.domain.usecase.tracking.RecoverInterruptedTripUseCase
import com.example.mileagetracker.service.ActivityTransitionRegistrar
import com.example.mileagetracker.worker.ReimbursementSnapshotBackfillWorker
import com.example.mileagetracker.worker.OdometerEvidenceReminderWorker
import com.example.mileagetracker.worker.ReportHistoryCleanupWorker
import com.example.mileagetracker.worker.SyncOutboxWorker
import com.example.mileagetracker.worker.TripAddressBackfillWorker
import com.example.mileagetracker.worker.TripBufferFlushWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// MileageApplication is the entry point of the Mileage Tracker app.
// It initializes the Places SDK, restores the user's locale, seeds CRA rates,
// recovers any interrupted trips, and schedules various background workers.
@HiltAndroidApp
class MileageApplication : Application(), Configuration.Provider {

    // Injected dependencies
    @Inject lateinit var craRateSeeder: CraRateSeeder

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var localePreferencesRepository: LocalePreferencesRepository

    @Inject lateinit var recoverInterruptedTripUseCase: RecoverInterruptedTripUseCase

    // Coroutine scope for application-level tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Provide the WorkManager configuration with the custom worker factory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    // onCreate is called when the application is starting,
    // before any other application objects have been created.
    override fun onCreate() {
        super.onCreate()
        AppCheckInstaller.install()
        initPlaces()
        restoreLocale()
        seedCraRates()
        recoverInterruptedTrip()
        ActivityTransitionRegistrar(this).registerIfEligible()
        scheduleTripBufferFlush()
        SyncOutboxWorker.schedule(this)
        scheduleReportHistoryCleanup()
        scheduleOdometerEvidenceReminder()
        scheduleReimbursementSnapshotBackfill()
        scheduleTripAddressBackfill()
    }

    /**
     * Initialize the Places SDK once at process start.
     *
     * The secrets-gradle-plugin injects MAPS_API_KEY as the value of the
     * "com.google.android.geo.API_KEY" <meta-data> entry in the manifest.
     * Reading it from applicationInfo.metaData is the reliable way to
     * access it at runtime. resValue / BuildConfig approaches fail because
     * project.findProperty() runs before the secrets plugin loads
     * local.properties during the Gradle configuration phase.
     */
    private fun initPlaces() {
        runCatching {
            val appInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.GET_META_DATA
            )
            val apiKey = appInfo.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
            val hasConfiguredKey = apiKey.isNotBlank() && apiKey != "REPLACE_WITH_MAPS_API_KEY"
            if (hasConfiguredKey) {
                Places.initialize(applicationContext, apiKey)
                Log.d(TAG, "Places SDK initialized.")
            } else {
                Log.w(
                    TAG,
                    "MAPS_API_KEY is not configured; Places autocomplete will not work. " +
                        "Ensure MAPS_API_KEY is set in local.properties."
                )
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to initialize Places SDK: ${error.message}", error)
        }
    }

    // Restore the user's preferred locale from shared preferences and apply it to the app.
    private fun restoreLocale() {
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val language = localePreferencesRepository.getLanguage().firstOrNull()
                    ?: AppLanguage.SYSTEM_DEFAULT
                val locales = if (language == AppLanguage.SYSTEM_DEFAULT) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(language.tag)
                }
                withContext(Dispatchers.Main) {
                    AppCompatDelegate.setApplicationLocales(locales)
                }
                Log.d(TAG, "Locale restored: ${language.tag.ifEmpty { "system" }}")
            }.onFailure { error ->
                Log.e(TAG, "Locale restoration failed: ${error.message}", error)
            }
        }
    }

    // Seed CRA rates into the database if they haven't been seeded yet.
    private fun seedCraRates() {
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val inserted = craRateSeeder.seedAll()
                if (inserted > 0) {
                    Log.i(TAG, "CRA rates seeded: $inserted row(s) inserted.")
                } else {
                    Log.d(TAG, "CRA rates already seeded; no new rows inserted.")
                }
            }.onFailure { error ->
                Log.e(TAG, "CRA rate seeding failed: ${error.message}", error)
            }
        }
    }

    // Schedule the TripBufferFlushWorker to run periodically to flush trip data.
    private fun scheduleTripBufferFlush() {
        runCatching {
            TripBufferFlushWorker.schedule(this)
            Log.d(TAG, "TripBufferFlushWorker scheduled.")
        }.onFailure { error ->
            Log.e(TAG, "Failed to schedule TripBufferFlushWorker: ${error.message}", error)
        }
    }

    // Attempt to recover any interrupted trip
    // that was not completed due to app termination or crash.
    private fun recoverInterruptedTrip() {
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                if (recoverInterruptedTripUseCase()) {
                    Log.w(TAG, "Recovered interrupted tracking session into the trip buffer.")
                }
            }.onFailure { error ->
                Log.e(TAG, "Interrupted trip recovery failed: ${error.message}", error)
            }
        }
    }

    // Schedule the ReportHistoryCleanupWorker to run periodically to clean up old report history.
    private fun scheduleReportHistoryCleanup() {
        runCatching {
            ReportHistoryCleanupWorker.schedule(this)
            Log.d(TAG, "ReportHistoryCleanupWorker scheduled.")
        }.onFailure { error ->
            Log.e(TAG, "Failed to schedule ReportHistoryCleanupWorker: ${error.message}", error)
        }
    }

    // Schedule the ReimbursementSnapshotBackfillWorker to
    // run periodically to backfill reimbursement snapshots.
    private fun scheduleReimbursementSnapshotBackfill() {
        runCatching {
            ReimbursementSnapshotBackfillWorker.schedule(this)
            Log.d(TAG, "ReimbursementSnapshotBackfillWorker scheduled.")
        }.onFailure { error ->
            Log.e(
                TAG,
                "Failed to schedule ReimbursementSnapshotBackfillWorker: ${error.message}",
                error,
            )
        }
    }

    // Schedule the OdometerEvidenceReminderWorker to run
    // periodically to remind users to provide odometer evidence.
    private fun scheduleOdometerEvidenceReminder() {
        runCatching {
            OdometerEvidenceReminderWorker.schedule(this)
            Log.d(TAG, "OdometerEvidenceReminderWorker scheduled.")
        }.onFailure { error ->
            Log.e(TAG, "Failed to schedule OdometerEvidenceReminderWorker: ${error.message}", error)
        }
    }

    // Schedule the TripAddressBackfillWorker to run periodically to backfill trip addresses.
    private fun scheduleTripAddressBackfill() {
        runCatching {
            TripAddressBackfillWorker.schedule(this)
            Log.d(TAG, "TripAddressBackfillWorker scheduled.")
        }.onFailure { error ->
            Log.e(TAG, "Failed to schedule TripAddressBackfillWorker: ${error.message}", error)
        }
    }

    // Companion object to hold constants and static members
    private companion object {
        const val TAG = "MileageApplication"
    }
}
