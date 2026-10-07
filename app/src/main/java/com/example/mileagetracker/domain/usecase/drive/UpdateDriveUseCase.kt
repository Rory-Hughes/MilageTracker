package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Updates an existing drive and records a per-field audit row for every
 * changed column.
 *
 * ## Diff strategy
 * The use case loads the current row from the database, compares it
 * field-by-field against [updated], and writes one [TripAuditEntity] per
 * changed auditable field.  Only the fields listed in [auditableFields]
 * produce audit rows; internal bookkeeping columns (`updatedAt`,
 * `deletedAt`) are intentionally excluded.
 *
 * If no auditable fields changed, the drive is still persisted (to capture
 * any non-auditable field changes such as `notes`) but no audit rows are
 * written.
 *
 * ## Not-found handling
 * Returns `false` if the drive does not exist or has been soft-deleted,
 * so the ViewModel can show an appropriate error without crashing.
 */
class UpdateDriveUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val auditRepository: AuditRepository,
) {
    /**
     * @param updated   the entity with updated field values; `id` must
     *                  match an existing active drive.
     * @param actorUid  UID of the user performing the update.
     * @return `true` if the drive was found and updated; `false` otherwise.
     */
    suspend operator fun invoke(updated: DriveEntity, actorUid: String): Boolean {
        val current = driveRepository.getDriveById(updated.id)
            ?: return false

        driveRepository.updateDrive(updated)

        recordAuditDiff(current, updated, actorUid)

        return true
    }

    private suspend fun recordAuditDiff(
        before: DriveEntity,
        after: DriveEntity,
        actorUid: String,
    ) {
        auditableFields.forEach { field ->
            val old = field.get(before)
            val new = field.get(after)
            if (old != new) {
                auditRepository.recordEvent(
                    tripId = after.id,
                    actorUid = actorUid,
                    eventType = field.eventType,
                    fieldName = field.columnName,
                    oldValue = old?.toString(),
                    newValue = new?.toString(),
                )
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Auditable field registry
    // ───────────────────────────────────────────────────────────────────────────

    private data class AuditableField(
        val columnName: String,
        val eventType: AuditEventType,
        val get: (DriveEntity) -> Any?,
    )

    private val auditableFields = listOf(
        AuditableField(
            columnName = "trip_category",
            eventType = AuditEventType.CATEGORY_CHANGED,
            get = { it.tripCategory },
        ),
        AuditableField(
            columnName = "purpose_text",
            eventType = AuditEventType.PURPOSE_UPDATED,
            get = { it.purposeText },
        ),
        AuditableField(
            columnName = "distance_km",
            eventType = AuditEventType.DISTANCE_UPDATED,
            get = { it.distanceKm },
        ),
        AuditableField(
            columnName = "start_location",
            eventType = AuditEventType.LOCATION_UPDATED,
            get = { it.startLocation },
        ),
        AuditableField(
            columnName = "end_location",
            eventType = AuditEventType.LOCATION_UPDATED,
            get = { it.endLocation },
        ),
        AuditableField(
            columnName = "vehicle_id",
            eventType = AuditEventType.VEHICLE_ASSIGNED,
            get = { it.vehicleId },
        ),
        AuditableField(
            columnName = "notes",
            eventType = AuditEventType.NOTE_UPDATED,
            get = { it.notes },
        ),
    )
}
