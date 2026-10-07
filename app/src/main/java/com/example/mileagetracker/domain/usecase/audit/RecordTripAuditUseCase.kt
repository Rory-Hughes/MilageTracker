package com.example.mileagetracker.domain.usecase.audit

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.domain.repository.AuditRepository
import javax.inject.Inject

/**
 * Central entry-point for writing trip audit rows.
 *
 * All audit writes in the app should go through this use case rather than
 * calling [AuditRepository] directly.  This keeps the audit writing
 * contract in one place, makes it easy to add cross-cutting concerns
 * (e.g. rate-limiting, redaction) later, and makes ViewModel/use-case
 * tests easy to set up with a single mock.
 *
 * ## Available operations
 *
 * | Method              | When to use |
 * |---------------------|-------------|
 * | [recordCreation]    | Drive was just inserted |
 * | [recordFieldChange] | A single field was changed by the user or system |
 * | [recordEvent]       | Any other event type that doesn’t map to a field diff |
 * | [recordBatch]       | Multiple events that must all succeed or all fail |
 *
 * ## Actor UID conventions
 * - User-initiated events → Firebase / local user UID
 * - Background service events → owner’s UID (the service acts on their behalf)
 * - Automated / system events (rule engine, ML classifier) → `"SYSTEM"`
 */
class RecordTripAuditUseCase @Inject constructor(
    private val auditRepository: AuditRepository,
) {

    /**
     * Record that a drive was created.
     *
     * Writes a [AuditEventType.DRIVE_CREATED] row with no field diff.
     * This is the first row in every drive’s audit timeline.
     *
     * @param tripId   the newly-created drive’s database ID.
     * @param actorUid UID of the user or service that created the drive.
     */
    suspend fun recordCreation(tripId: Int, actorUid: String) {
        auditRepository.recordEvent(
            tripId = tripId,
            actorUid = actorUid,
            eventType = AuditEventType.DRIVE_CREATED,
        )
    }

    /**
     * Record that a single field on a drive was changed.
     *
     * The caller is responsible for serialising [oldValue] and [newValue]
     * to human-readable strings before calling (e.g. enum `.name`,
     * float `.toString()`, nullable `?.toString() ?: "null"`).
     *
     * @param tripId    the affected drive’s database ID.
     * @param actorUid  UID of the user or service making the change.
     * @param eventType the [AuditEventType] that best describes the change.
     * @param fieldName snake_case column name of the changed field
     *                  (e.g. `"trip_category"`, `"distance_km"`).
     * @param oldValue  string-encoded previous value, or `null`.
     * @param newValue  string-encoded new value, or `null`.
     */
    suspend fun recordFieldChange(
        tripId: Int,
        actorUid: String,
        eventType: AuditEventType,
        fieldName: String,
        oldValue: String?,
        newValue: String?,
    ) {
        auditRepository.recordEvent(
            tripId = tripId,
            actorUid = actorUid,
            eventType = eventType,
            fieldName = fieldName,
            oldValue = oldValue,
            newValue = newValue,
        )
    }

    /**
     * Record any event that does not correspond to a single-field diff.
     *
     * Use this for events like [AuditEventType.ATTACHMENT_ADDED] where
     * there is no meaningful `fieldName`/`oldValue`/`newValue` triplet.
     *
     * @param tripId    the affected drive’s database ID.
     * @param actorUid  UID of the actor.
     * @param eventType the event to record.
     */
    suspend fun recordEvent(
        tripId: Int,
        actorUid: String,
        eventType: AuditEventType,
    ) {
        auditRepository.recordEvent(
            tripId = tripId,
            actorUid = actorUid,
            eventType = eventType,
        )
    }

    /**
     * Insert multiple audit rows that must all succeed or all fail.
     *
     * Useful when a single user action produces several audit events
     * simultaneously (e.g. a bulk re-classification that changes both
     * `trip_category` and `classification_status` in one operation).
     *
     * Each [TripAuditEntity] in [rows] should be constructed with
     * `changedAt = System.currentTimeMillis()` by the caller; this
     * method does not override existing timestamps so that a pre-built
     * batch retains a consistent snapshot time.
     *
     * @param rows the audit rows to insert; must be non-empty.
     * @throws IllegalArgumentException if [rows] is empty.
     */
    suspend fun recordBatch(rows: List<TripAuditEntity>) {
        require(rows.isNotEmpty()) { "recordBatch called with an empty list." }
        rows.forEach { auditRepository.insertAuditRow(it) }
    }
}
