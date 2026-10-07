package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for trip audit-log operations.
 *
 * ## Append-only contract
 * This interface exposes **no update or delete methods**.  Audit rows are
 * immutable once written.  Deletion only occurs as a side-effect of a
 * parent-drive hard-delete (GDPR erasure), which is handled at the
 * database level via CASCADE.
 */
interface AuditRepository {

    // ───────────────────────────────────────────────────────────────────────────
    // Write
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Append a new audit event row.
     *
     * Prefer the typed convenience overload [recordEvent] for new callers;
     * this overload is available for cases where a fully-constructed
     * [TripAuditEntity] is already at hand (e.g. batch import).
     */
    suspend fun insertAuditRow(row: TripAuditEntity)

    /**
     * Convenience overload: construct and append an audit event from its
     * constituent parts.  The repository implementation sets [changedAt]
     * to the current time.
     *
     * @param tripId    the drive trip being audited.
     * @param actorUid  UID of the user or `"SYSTEM"` for automated events.
     * @param eventType the kind of change.
     * @param fieldName snake_case column name of the changed field, or null
     *                  for creation/existence events.
     * @param oldValue  string-encoded previous value, or null.
     * @param newValue  string-encoded new value, or null.
     */
    suspend fun recordEvent(
        tripId: Int,
        actorUid: String,
        eventType: AuditEventType,
        fieldName: String? = null,
        oldValue: String? = null,
        newValue: String? = null,
    )

    // ───────────────────────────────────────────────────────────────────────────
    // Reads
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Live stream of the full audit trail for a single trip,
     * ordered oldest-first.  Used by the trip-detail timeline screen.
     */
    fun getAuditTrailForTrip(tripId: Int): Flow<List<TripAuditEntity>>

    /**
     * One-shot fetch of the full audit trail for a trip.
     * Used by export and report flows.
     */
    suspend fun getAuditTrailForTripOnce(tripId: Int): List<TripAuditEntity>

    /**
     * One-shot fetch of all audit rows in a timestamp window.
     * Used by admin/debug screens and activity-feed features.
     */
    suspend fun getAuditRowsByDateRange(
        fromMillis: Long,
        toMillis: Long,
    ): List<TripAuditEntity>
}
