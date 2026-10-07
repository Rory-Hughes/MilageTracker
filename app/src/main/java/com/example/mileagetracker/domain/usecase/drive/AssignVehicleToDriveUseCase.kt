package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import javax.inject.Inject

/**
 * Assigns (or removes) a vehicle on a drive trip and records a
 * [AuditEventType.VEHICLE_ASSIGNED] audit row.
 *
 * ## Validation
 * - If [vehicleId] is non-null, the vehicle must exist (active or inactive)
 *   so that the FK reference is valid.  Inactive vehicles can still be
 *   assigned to historical drives.
 * - The drive must exist and not be soft-deleted.
 *
 * ## Separation from UpdateDriveUseCase
 * Vehicle assignment is surfaced as a dedicated action in the UI (a
 * dedicated picker sheet, not the general edit form) so it deserves its
 * own use case.  [UpdateDriveUseCase] would also record a VEHICLE_ASSIGNED
 * audit row if vehicleId changes; callers should use one or the other,
 * not both, for the same assignment action.
 */
class AssignVehicleToDriveUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val vehicleRepository: VehicleRepository,
    private val auditRepository: AuditRepository,
) {
    /**
     * @param driveId   the ID of the drive to update.
     * @param vehicleId the vehicle to assign, or `null` to clear the
     *                  assignment.
     * @param actorUid  UID of the user performing the assignment.
     * @return `true` if the drive was found and updated; `false` otherwise.
     * @throws IllegalArgumentException if [vehicleId] is non-null but the
     *         vehicle does not exist.
     */
    suspend operator fun invoke(
        driveId: Int,
        vehicleId: Int?,
        actorUid: String,
    ): Boolean {
        if (vehicleId != null) {
            requireNotNull(vehicleRepository.getVehicleById(vehicleId)) {
                "Cannot assign vehicle: vehicle $vehicleId not found."
            }
        }

        val current = driveRepository.getDriveById(driveId)
        return current?.let {
            if (it.vehicleId != vehicleId) {
                updateDriveVehicle(it, driveId, vehicleId, actorUid)
            }
            true
        } ?: false
    }

    private suspend fun updateDriveVehicle(
        current: com.example.mileagetracker.data.local.entity.DriveEntity,
        driveId: Int,
        vehicleId: Int?,
        actorUid: String,
    ) {
        driveRepository.updateDrive(current.copy(vehicleId = vehicleId))
        auditRepository.recordEvent(
            tripId = driveId,
            actorUid = actorUid,
            eventType = AuditEventType.VEHICLE_ASSIGNED,
            fieldName = "vehicle_id",
            oldValue = current.vehicleId?.toString(),
            newValue = vehicleId?.toString(),
        )
    }
}
