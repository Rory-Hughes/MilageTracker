package com.example.mileagetracker.domain.usecase.tracking

import android.util.Log
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.entity.BufferedTripEntity
import com.example.mileagetracker.domain.usecase.drive.PersistTrackedDriveUseCase
import javax.inject.Inject

/**
 * Drains the `buffered_trips` write-ahead table by re-attempting persistence
 * of each entry through the atomic tracked-drive write path.
 */
class FlushTripBufferUseCase @Inject constructor(
    private val tripBufferStore: TripBufferStore,
    private val persistTrackedDriveUseCase: PersistTrackedDriveUseCase,
) {

    suspend operator fun invoke(): FlushResult {
        tripBufferStore.pruneExhausted()

        val pending = tripBufferStore.getPending()
        if (pending.isEmpty()) return FlushResult(flushed = 0, failed = 0, remaining = 0)

        var flushed = 0
        var failed = 0

        for (entry in pending) {
            val success = tryFlush(entry)
            if (success) flushed++ else failed++
        }

        val remaining = tripBufferStore.pendingCount()
        Log.i(TAG, "Flush complete: flushed=$flushed failed=$failed remaining=$remaining")
        return FlushResult(flushed = flushed, failed = failed, remaining = remaining)
    }

    private suspend fun tryFlush(entry: BufferedTripEntity): Boolean {
        return runCatching {
            val drive = tripBufferStore.deserializeDrive(entry.tripJson)
            val actorUid = drive.driverUid?.takeIf { it.isNotBlank() } ?: SYSTEM_ACTOR_UID
            val points = tripBufferStore.deserializeRoutePoints(
                routePointsJson = entry.routePointsJson,
                tripId = 0,
            )
            val newId = persistTrackedDriveUseCase(
                drive = drive,
                routePoints = points,
                actorUid = actorUid,
            )

            tripBufferStore.remove(entry.id)
            Log.d(TAG, "Flushed buffered trip id=${entry.id} -> driveId=$newId")
            true
        }.getOrElse { error ->
            Log.w(TAG, "Failed to flush buffered trip id=${entry.id} (retry ${entry.retryCount}): ${error.message}")
            tripBufferStore.update(entry.copy(retryCount = entry.retryCount + 1))
            false
        }
    }

    companion object {
        private const val TAG = "FlushTripBufferUseCase"
        private const val SYSTEM_ACTOR_UID = "SYSTEM"
    }
}

data class FlushResult(
    val flushed: Int,
    val failed: Int,
    val remaining: Int,
)
