package com.example.mileagetracker.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.mileagetracker.domain.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralizes the foreground-service launch contract for trip tracking.
 */
@Singleton
class TrackingCommandDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun startManual() = start(DriveTrackingService.ACTION_MANUAL_START, DetectionSource.MANUAL)

    fun startAutomatic() = start(DriveTrackingService.ACTION_AUTO_START, DetectionSource.AUTO)

    fun stop() = stop(DriveTrackingService.ACTION_AUTO_STOP)

    fun stopManual() = stop(DriveTrackingService.ACTION_MANUAL_STOP)

    private fun start(actionName: String, source: DetectionSource) {
        context.startForegroundService(
            Intent(context, DriveTrackingService::class.java).apply {
                action = actionName
                putExtra(DriveTrackingService.EXTRA_DETECTION_SOURCE, source.name)
            }
        )
    }

    private fun stop(actionName: String) {
        runCatching {
            context.startService(
                Intent(context, DriveTrackingService::class.java).apply {
                    action = actionName
                }
            )
        }.onFailure { error ->
            Log.i(TAG, "Ignoring stop command because tracking is not running.", error)
        }
    }

    private companion object {
        const val TAG = "TrackingDispatcher"
    }
}
