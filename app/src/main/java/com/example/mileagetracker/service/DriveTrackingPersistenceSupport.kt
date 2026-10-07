package com.example.mileagetracker.service

import android.content.Context
import android.location.Location
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.entity.ActiveRoutePointEntity
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingMetadataEmbedded
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.DetectionSource
import com.example.mileagetracker.domain.model.DuplicateCheckResult
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.repository.VehicleRepository
import com.example.mileagetracker.domain.usecase.drive.PersistTrackedDriveUseCase
import com.example.mileagetracker.domain.usecase.drive.TrackedDriveAuditEvent
import com.example.mileagetracker.domain.usecase.tracking.DetectDuplicateTripUseCase
import java.util.Calendar
import java.util.Locale

internal data class DriveTrackingCapture(
    val tripStartTime: Long,
    val detectionSource: DetectionSource,
    val batteryAtStart: Int?,
    val batterySaverSeenActive: Boolean,
    val actorUid: String,
    val isManualMode: Boolean,
    val totalDistanceKm: Float,
    val snapshot: List<RoutePoint>,
)

internal data class DriveTrackingPersistenceDeps(
    val tripBufferStore: TripBufferStore,
    val detectDuplicateTripUseCase: DetectDuplicateTripUseCase,
    val persistTrackedDriveUseCase: PersistTrackedDriveUseCase,
    val vehicleRepository: VehicleRepository,
)

private data class PersistContext(
    val candidate: DriveEntity,
    val routeEntities: List<RoutePointEntity>,
    val bufferId: Long,
    val actorUid: String,
)

private const val TAG = "DriveTrackingService"
private const val UNKNOWN_LOCATION = "Unknown"
private const val AUTO_CONFIDENCE = 0.85f
private const val MANUAL_CONFIDENCE = 1.0f
private const val METERS_PER_SECOND_TO_KPH = 3.6f
private const val PERCENT_MULTIPLIER = 100

internal suspend fun persistTrackedDriveSnapshot(
    context: Context,
    capture: DriveTrackingCapture,
    deps: DriveTrackingPersistenceDeps,
) {
    val defaultVehicleId = resolveDefaultVehicleId(deps.vehicleRepository, capture.actorUid)
    val candidate = buildTrackedDriveCandidate(context, capture, defaultVehicleId)
    val routeEntities = capture.snapshot.map(RoutePoint::toEntity)
    val bufferId = enqueueTripBuffer(deps.tripBufferStore, candidate, routeEntities) ?: return
    val persistContext = PersistContext(
        candidate = candidate,
        routeEntities = routeEntities,
        bufferId = bufferId,
        actorUid = capture.actorUid,
    )

    when (val duplicateResult = resolveDuplicateTrip(deps.detectDuplicateTripUseCase, candidate)) {
        is DuplicateCheckResult.ProbableDuplicate -> {
            showDuplicateNotification(
                context = context,
                existingTripId = duplicateResult.existingTripId,
                overlapPercent = duplicateResult.overlapPercent,
                isProbable = true,
            )
        }

        is DuplicateCheckResult.Flagged -> {
            showDuplicateNotification(
                context = context,
                existingTripId = duplicateResult.existingTripId,
                overlapPercent = duplicateResult.overlapPercent,
                isProbable = false,
            )
            persistFlagged(deps.persistTrackedDriveUseCase, deps.tripBufferStore, persistContext, duplicateResult)
        }

        is DuplicateCheckResult.Clear -> {
            persistNormally(deps.persistTrackedDriveUseCase, deps.tripBufferStore, persistContext)
        }
    }
}

internal fun Location.toTrackedRoutePoint() = RoutePoint(
    tripId = 0L,
    lat = latitude,
    lng = longitude,
    altitudeMetres = if (hasAltitude()) altitude else null,
    accuracyMetres = if (hasAccuracy()) accuracy else null,
    speedKph = if (hasSpeed()) speed * METERS_PER_SECOND_TO_KPH else null,
    timestampMs = time,
)

internal fun RoutePoint.toActiveEntity() = ActiveRoutePointEntity(
    lat = lat,
    lng = lng,
    altitudeMetres = altitudeMetres,
    accuracyMetres = accuracyMetres,
    speedKph = speedKph,
    timestampMs = timestampMs,
)

private fun buildTrackedDriveCandidate(
    context: Context,
    capture: DriveTrackingCapture,
    defaultVehicleId: Int?,
): DriveEntity {
    val metadata = TrackingMetadataEmbedded(
        detectionSource = capture.detectionSource,
        confidenceScore = if (capture.detectionSource == DetectionSource.AUTO) {
            AUTO_CONFIDENCE
        } else {
            MANUAL_CONFIDENCE
        },
        batteryLevelAtStart = capture.batteryAtStart,
        batteryLevelAtEnd = currentBatteryLevel(context),
        wasBatterySaverActive = capture.batterySaverSeenActive || isPowerSaverActive(context),
    )
    return DriveEntity(
        startedAt = capture.tripStartTime,
        endedAt = System.currentTimeMillis(),
        driverUid = capture.actorUid,
        startLocation = formatRoutePoint(capture.snapshot.firstOrNull()),
        endLocation = formatRoutePoint(capture.snapshot.lastOrNull()),
        distanceKm = capture.totalDistanceKm,
        trackingSource = if (capture.isManualMode) {
            TrackingSource.MANUAL_ENTRY
        } else {
            TrackingSource.AUTO_TRACKED
        },
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        purposeText = null,
        vehicleId = defaultVehicleId,
        appliedRateSnapshot = 0f,
        classificationConfidence = metadata.confidenceScore,
        trackingMetadata = metadata,
    )
}

private fun formatRoutePoint(point: RoutePoint?): String =
    point?.let { "%1$.5f, %2$.5f".format(Locale.US, it.lat, it.lng) } ?: UNKNOWN_LOCATION

private suspend fun resolveDefaultVehicleId(
    vehicleRepository: VehicleRepository,
    ownerUid: String,
): Int? = runCatching {
    val fiscalYear = Calendar.getInstance().get(Calendar.YEAR)
    vehicleRepository.getDefaultVehicle(ownerUid, fiscalYear)?.id
}.getOrElse { error ->
    android.util.Log.w(TAG, "Default vehicle lookup failed: ${error.message}", error)
    null
}

private fun RoutePoint.toEntity() = RoutePointEntity(
    tripId = 0L,
    lat = lat,
    lng = lng,
    altitudeMetres = altitudeMetres,
    accuracyMetres = accuracyMetres,
    speedKph = speedKph,
    timestampMs = timestampMs,
)

private suspend fun enqueueTripBuffer(
    tripBufferStore: TripBufferStore,
    candidate: DriveEntity,
    routeEntities: List<RoutePointEntity>,
): Long? {
    val enqueueResult = runCatching { tripBufferStore.enqueue(candidate, routeEntities) }
    return if (enqueueResult.isSuccess) {
        enqueueResult.getOrNull()
    } else {
        val error = enqueueResult.exceptionOrNull()
        android.util.Log.e(TAG, "Buffer write failed: ${error?.message}", error)
        null
    }
}

private suspend fun resolveDuplicateTrip(
    detectDuplicateTripUseCase: DetectDuplicateTripUseCase,
    candidate: DriveEntity,
): DuplicateCheckResult {
    val duplicateResult = runCatching { detectDuplicateTripUseCase(candidate) }
    return duplicateResult.getOrElse { error ->
        android.util.Log.w(TAG, "Dup check failed, proceeding as Clear: ${error.message}", error)
        DuplicateCheckResult.Clear
    }
}

private suspend fun persistNormally(
    persistTrackedDriveUseCase: PersistTrackedDriveUseCase,
    tripBufferStore: TripBufferStore,
    context: PersistContext,
) {
    val persistResult = runCatching {
        persistTrackedDriveUseCase(
            drive = context.candidate,
            routePoints = context.routeEntities,
            actorUid = context.actorUid,
        )
        tripBufferStore.remove(context.bufferId)
    }
    persistResult.exceptionOrNull()?.let { error ->
        android.util.Log.w(
            TAG,
            "Persist failed; bufferId=${context.bufferId} retained: ${error.message}",
            error,
        )
    }
}

private suspend fun persistFlagged(
    persistTrackedDriveUseCase: PersistTrackedDriveUseCase,
    tripBufferStore: TripBufferStore,
    context: PersistContext,
    duplicateResult: DuplicateCheckResult.Flagged,
) {
    val persistResult = runCatching {
        persistTrackedDriveUseCase(
            drive = context.candidate.copy(classificationStatus = ClassificationStatus.NEEDS_REVIEW),
            routePoints = context.routeEntities,
            actorUid = context.actorUid,
            additionalAuditEvents = listOf(
                TrackedDriveAuditEvent(
                    eventType = AuditEventType.DUPLICATE_FLAGGED,
                    fieldName = "existing_trip_id",
                    oldValue = duplicateResult.existingTripId.toString(),
                    newValue = "${(duplicateResult.overlapPercent * PERCENT_MULTIPLIER).toInt()}%",
                )
            ),
        )
        tripBufferStore.remove(context.bufferId)
    }
    persistResult.exceptionOrNull()?.let { error ->
        android.util.Log.w(
            TAG,
            "Flagged persist failed; bufferId=${context.bufferId} retained: ${error.message}",
            error,
        )
    }
}
