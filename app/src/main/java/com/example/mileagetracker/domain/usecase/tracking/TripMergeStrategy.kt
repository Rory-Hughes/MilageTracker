package com.example.mileagetracker.domain.usecase.tracking

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingMetadataEmbedded
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.repository.AuditRepository

/**
 * Utility object that merges two overlapping [DriveEntity] records into one
 * canonical trip.
 *
 * ## Merge rules
 * The merge is intentionally conservative — no data is silently discarded:
 *
 * | Field                  | Strategy |
 * |------------------------|----------|
 * | `id`                   | Keep [existing].id (the already-persisted row is updated in-place) |
 * | `startedAt`            | `min(candidate.startedAt, existing.startedAt)` — earliest start |
 * | `endedAt`              | `max(candidate.endedAt, existing.endedAt)` — latest end |
 * | `distanceKm`           | Keep the larger of the two (longer route = more data captured) |
 * | `startLocation`        | Keep whichever trip has the earlier `startedAt` |
 * | `endLocation`          | Keep whichever trip has the later `endedAt` |
 * | `tripCategory`         | Prefer [existing] if already classified; otherwise [candidate] |
 * | `classificationStatus` | Prefer [existing] if already classified; otherwise [candidate] |
 * | `purposeText`          | Prefer non-null, prefer [existing] on tie |
 * | `notes`                | Concatenate with `" | "` separator if both non-null |
 * | `vehicleId`            | Prefer non-null, prefer [existing] on tie |
 * | `appliedRateSnapshot`  | Keep [existing] if non-zero; otherwise [candidate] |
 * | `trackingMetadata`     | Merge: keep earliest battery start, latest battery end |
 * | `driverUid`            | Prefer [existing]; fall back to [candidate] |
 * | `updatedAt`            | Set to `System.currentTimeMillis()` |
 *
 * ## Audit row
 * After merging, callers **must** invoke [recordMergeAudit] to write a
 * [AuditEventType.TRIP_MERGED] audit entry.  This is not done inside
 * [merge] itself to keep the function pure and testable without a database.
 *
 * ## Caller flow
 * ```kotlin
 * val merged = TripMergeStrategy.merge(candidate, existing)
 * driveRepository.updateDrive(merged)
 * TripMergeStrategy.recordMergeAudit(
 *     survivorId      = merged.id,
 *     consumedTripId  = candidate.id,
 *     actorUid        = actorUid,
 *     auditRepository = auditRepository,
 * )
 * ```
 */
object TripMergeStrategy {

    /**
     * Produces a merged [DriveEntity] from [candidate] and [existing].
     *
     * The returned entity always carries [existing].id so that the caller
     * can issue a single [DriveRepository.updateDrive] on the already-saved
     * row rather than a delete + insert.
     *
     * @param candidate  The newly captured trip (may not be persisted yet).
     * @param existing   The already-persisted trip it overlaps with.
     * @return A new [DriveEntity] with merged fields, ready to be
     *                   passed to [DriveRepository.updateDrive].
     */
    fun merge(candidate: DriveEntity, existing: DriveEntity): DriveEntity {
        val earlierTrip = earlierTrip(candidate, existing)
        val laterTrip = laterTrip(candidate, existing)
        val classification = mergeClassification(candidate, existing)

        return existing.copy(
            startedAt = minOf(candidate.startedAt, existing.startedAt),
            endedAt = mergedEndedAt(candidate, existing),
            distanceKm = maxOf(candidate.distanceKm, existing.distanceKm),
            startLocation = earlierTrip.startLocation,
            endLocation = laterTrip.endLocation,
            tripCategory = classification.tripCategory,
            classificationStatus = classification.classificationStatus,
            purposeText = existing.purposeText ?: candidate.purposeText,
            notes = mergeNotes(candidate.notes, existing.notes),
            vehicleId = existing.vehicleId ?: candidate.vehicleId,
            appliedRateSnapshot = mergeAppliedRateSnapshot(candidate, existing),
            driverUid = existing.driverUid ?: candidate.driverUid,
            trackingMetadata = mergeMetadata(candidate.trackingMetadata, existing.trackingMetadata),
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Writes a [AuditEventType.TRIP_MERGED] audit row recording which trip
     * was consumed into [survivorId].
     *
     * @param survivorId     The PK of the merged row that was updated in-place.
     * @param consumedTripId The PK of the trip whose data was absorbed.
     * @param actorUid       UID of the user or system performing the merge.
     * @param auditRepository Injected dependency for writing the audit entry.
     */
    suspend fun recordMergeAudit(
        survivorId: Int,
        consumedTripId: Int,
        actorUid: String,
        auditRepository: AuditRepository,
    ) {
        auditRepository.recordEvent(
            tripId = survivorId,
            actorUid = actorUid,
            eventType = AuditEventType.TRIP_MERGED,
            fieldName = "merged_trip_id",
            oldValue = consumedTripId.toString(),
            newValue = survivorId.toString(),
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    private fun mergeMetadata(
        a: TrackingMetadataEmbedded?,
        b: TrackingMetadataEmbedded?,
    ): TrackingMetadataEmbedded? =
        when {
            a == null -> b
            b == null -> a
            else -> b.copy(
                batteryLevelAtStart = mergeMaxNullable(a.batteryLevelAtStart, b.batteryLevelAtStart),
                batteryLevelAtEnd = mergeMinNullable(a.batteryLevelAtEnd, b.batteryLevelAtEnd),
                wasBatterySaverActive = a.wasBatterySaverActive || b.wasBatterySaverActive,
                confidenceScore = mergeMaxNullable(a.confidenceScore, b.confidenceScore),
                detectionSource = b.detectionSource ?: a.detectionSource,
            )
        }
}

private fun earlierTrip(candidate: DriveEntity, existing: DriveEntity): DriveEntity =
    if (candidate.startedAt <= existing.startedAt) candidate else existing

private fun laterTrip(candidate: DriveEntity, existing: DriveEntity): DriveEntity =
    if (tripEndOrMinValue(candidate) >= tripEndOrMinValue(existing)) candidate else existing

private fun mergedEndedAt(candidate: DriveEntity, existing: DriveEntity): Long? =
    maxOf(
        candidate.endedAt ?: candidate.startedAt,
        existing.endedAt ?: existing.startedAt,
    ).takeIf { it > 0 }

private fun mergeAppliedRateSnapshot(candidate: DriveEntity, existing: DriveEntity): Float =
    existing.appliedRateSnapshot.takeIf { it != 0f } ?: candidate.appliedRateSnapshot

private fun mergeNotes(candidateNotes: String?, existingNotes: String?): String? =
    when {
        candidateNotes != null && existingNotes != null -> "$existingNotes | $candidateNotes"
        else -> existingNotes ?: candidateNotes
    }

private fun mergeClassification(
    candidate: DriveEntity,
    existing: DriveEntity,
): MergedClassification {
    val baseTrip = if (isClassified(existing)) existing else candidate
    return MergedClassification(
        tripCategory = baseTrip.tripCategory,
        classificationStatus = baseTrip.classificationStatus,
    )
}

private fun isClassified(drive: DriveEntity): Boolean =
    drive.classificationStatus.name != "PENDING" &&
        drive.classificationStatus.name != "NEEDS_REVIEW"

private fun tripEndOrMinValue(drive: DriveEntity): Long = drive.endedAt ?: Long.MIN_VALUE

private fun <T : Comparable<T>> mergeMaxNullable(first: T?, second: T?): T? = when {
    first != null && second != null -> maxOf(first, second)
    else -> second ?: first
}

private fun <T : Comparable<T>> mergeMinNullable(first: T?, second: T?): T? = when {
    first != null && second != null -> minOf(first, second)
    else -> second ?: first
}

private data class MergedClassification(
    val tripCategory: TripCategory,
    val classificationStatus: ClassificationStatus,
)
