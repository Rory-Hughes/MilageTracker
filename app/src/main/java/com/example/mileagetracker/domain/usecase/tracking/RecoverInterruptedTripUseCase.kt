package com.example.mileagetracker.domain.usecase.tracking

import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingMetadataEmbedded
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.toDomain
import com.example.mileagetracker.domain.model.DetectionSource
import com.example.mileagetracker.service.RouteDistanceCalculator
import javax.inject.Inject

/**
 * Converts a process-killed active GPS session into a review-required buffer row.
 */
class RecoverInterruptedTripUseCase @Inject constructor(
    private val activeTripStore: ActiveTripStore,
    private val tripBufferStore: TripBufferStore,
) {
    suspend operator fun invoke(): Boolean {
        val snapshot = activeTripStore.snapshot()
        return snapshot?.let { recoveredSnapshot ->
            recoverSnapshot(recoveredSnapshot)
        } ?: false
    }

    private suspend fun recoverSnapshot(
        snapshot: ActiveTripStore.Snapshot,
    ): Boolean {
        val points = snapshot.points.map { point ->
            RoutePointEntity(
                tripId = 0,
                lat = point.lat,
                lng = point.lng,
                altitudeMetres = point.altitudeMetres,
                accuracyMetres = point.accuracyMetres,
                speedKph = point.speedKph,
                timestampMs = point.timestampMs,
            )
        }
        val distanceKm = RouteDistanceCalculator.calculateTotalDistanceKm(
            snapshot.points.map { it.toDomain() }
        )
        if (distanceKm < MIN_RECOVERABLE_DISTANCE_KM) {
            activeTripStore.clear()
            return false
        }

        val source = runCatching {
            DetectionSource.valueOf(snapshot.session.detectionSource)
        }.getOrDefault(DetectionSource.AUTO)
        val first = points.first()
        val last = points.last()
        tripBufferStore.enqueue(
            DriveEntity(
                startedAt = snapshot.session.startedAt,
                endedAt = last.timestampMs.coerceAtLeast(snapshot.session.startedAt),
                driverUid = snapshot.session.ownerUid,
                startLocation = "${first.lat.format()}, ${first.lng.format()}",
                endLocation = "${last.lat.format()}, ${last.lng.format()}",
                distanceKm = distanceKm,
                trackingSource = if (snapshot.session.isManual) {
                    TrackingSource.MANUAL_ENTRY
                } else {
                    TrackingSource.AUTO_TRACKED
                },
                tripCategory = TripCategory.UNCLASSIFIED,
                classificationStatus = ClassificationStatus.NEEDS_REVIEW,
                notes = RECOVERY_NOTE,
                trackingMetadata = TrackingMetadataEmbedded(
                    detectionSource = source,
                    batteryLevelAtStart = snapshot.session.batteryLevelAtStart,
                    wasBatterySaverActive = snapshot.session.batterySaverSeen,
                ),
            ),
            points,
        )
        activeTripStore.clear()
        return true
    }

    private fun Double.format() = "%.5f".format(this)

    private companion object {
        const val MIN_RECOVERABLE_DISTANCE_KM = 0.3f
        const val RECOVERY_NOTE = "Recovered after tracking interruption. Review before reporting."
    }
}
