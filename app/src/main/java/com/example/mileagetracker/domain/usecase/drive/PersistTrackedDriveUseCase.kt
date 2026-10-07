package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.repository.TrackedDrivePersistenceRepository
import javax.inject.Inject

class PersistTrackedDriveUseCase @Inject constructor(
    private val trackedDrivePersistenceRepository: TrackedDrivePersistenceRepository,
    private val resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase,
) {
    suspend operator fun invoke(
        drive: DriveEntity,
        routePoints: List<RoutePointEntity>,
        actorUid: String,
        additionalAuditEvents: List<TrackedDriveAuditEvent> = emptyList(),
    ): Int {
        validate(drive)
        val resolvedDrive = resolveDriveLocationsUseCase(drive)
        return trackedDrivePersistenceRepository.persistTrackedDrive(
            drive = resolvedDrive,
            routePoints = routePoints,
            actorUid = actorUid,
            additionalAuditEvents = additionalAuditEvents,
        )
    }

    private fun validate(drive: DriveEntity) {
        require(drive.distanceKm >= 0f) {
            "Drive distance must be >= 0, got ${drive.distanceKm}"
        }
        require(drive.startedAt > 0L) {
            "Drive startedAt must be a valid epoch millis timestamp."
        }
        require(!drive.driverUid.isNullOrBlank()) {
            "Drive must have a non-blank driverUid."
        }
    }
}

data class TrackedDriveAuditEvent(
    val eventType: AuditEventType,
    val fieldName: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
)
