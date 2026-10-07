package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Creates a new drive trip and records a [AuditEventType.DRIVE_CREATED]
 * audit event in a single logical operation.
 *
 * ## Why a use case?
 * A bare [DriveRepository.saveDrive] call would persist the row but leave
 * no audit trail.  Bundling both operations here guarantees that every
 * drive creation in the app is audited, regardless of which screen or
 * service triggers it.
 *
 * ## Actor UID
 * The caller is responsible for supplying [actorUid].  For user-initiated
 * drives this is the Firebase UID; for auto-tracked drives (background
 * service) it is also the owner’s UID.  The sentinel `"SYSTEM"` should
 * only be used for purely automated events with no associated user session.
 *
 * ## Validation
 * Basic field validation is performed before the insert:
 * - [DriveEntity.distanceKm] must be ≥ 0
 * - [DriveEntity.startedAt] must be > 0
 * - [DriveEntity.driverUid] must not be blank
 *
 * Throws [IllegalArgumentException] on validation failure so the
 * ViewModel can surface the message directly in the UI.
 */
class CreateDriveUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val auditRepository: AuditRepository,
    private val resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase,
) {
    /**
     * @param drive     the fully-constructed entity to persist (id = 0).
     * @param actorUid  UID of the user or service creating the drive.
     * @return the generated database ID of the new drive.
     * @throws IllegalArgumentException if validation fails.
     */
    suspend operator fun invoke(drive: DriveEntity, actorUid: String): Int {
        validate(drive)
        val resolvedDrive = resolveDriveLocationsUseCase(drive)

        val newId = driveRepository.saveDrive(resolvedDrive)

        auditRepository.recordEvent(
            tripId = newId,
            actorUid = actorUid,
            eventType = AuditEventType.DRIVE_CREATED,
        )

        return newId
    }

    private fun validate(drive: DriveEntity) {
        require(drive.distanceKm >= 0f) {
            "Drive distance must be ≥ 0, got ${drive.distanceKm}"
        }
        require(drive.startedAt > 0L) {
            "Drive startedAt must be a valid epoch millis timestamp."
        }
        require(!drive.driverUid.isNullOrBlank()) {
            "Drive must have a non-blank driverUid."
        }
    }
}
