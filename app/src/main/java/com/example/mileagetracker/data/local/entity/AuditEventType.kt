package com.example.mileagetracker.data.local.entity

/**
 * Describes the kind of change recorded in a trip audit entry.
 *
 * Stored as its [name] string via a Room @TypeConverter so that reordering
 * entries never causes silent data corruption.
 *
 * - [DRIVE_CREATED]         – the trip record was created by auto-tracking.
 * - [MANUAL_ENTRY]          – the trip was created manually by the user via the
 *                             form-based entry flow (Task 3.2).  The audit detail
 *                             field stores the form’s `detectionSource` value.
 * - [TRIP_REPAIRED]         – an existing trip with [ClassificationStatus.NEEDS_REVIEW]
 *                             or [ClassificationStatus.INCOMPLETE] status was corrected
 *                             by the user via the repair flow (Task 3.2).  The audit
 *                             detail field stores a comma-separated list of the fields
 *                             that were changed.
 * - [CATEGORY_CHANGED]      – the trip category was changed.
 * - [PURPOSE_UPDATED]       – the trip purpose text was updated.
 * - [DISTANCE_UPDATED]      – the trip distance was updated.
 * - [LOCATION_UPDATED]      – the trip start or end location was edited by hand.
 * - [VEHICLE_ASSIGNED]      – a vehicle was assigned to the trip.
 * - [NOTE_UPDATED]          – a note field was updated.
 * - [ATTACHMENT_ADDED]      – an attachment was added to the trip.
 * - [DUPLICATE_FLAGGED]     – the trip was flagged as a probable or possible duplicate
 *                             by [DetectDuplicateTripUseCase]. The audit detail field
 *                             stores the conflicting trip’s ID and overlap percentage.
 * - [TRIP_MERGED]           – two trips were merged by [TripMergeStrategy]. The audit
 *                             detail field stores the ID of the trip that was consumed.
 * - [SUGGESTION_ACCEPTED]   – a frequent-trip suggestion was accepted by the user
 *                             (Task 4.1).  The [newValue] field stores
 *                             `confidence=<float>,basedOn=<id1|id2|…>`.
 * - [AUTO_CLASSIFIED]       – a rule-based engine assigned a category automatically
 *                             (Task 4.2).  The [newValue] field stores
 *                             `rule=<RuleName>,confidence=<float>`.
 */
enum class AuditEventType {
    DRIVE_CREATED,
    /** Trip was created manually via the form-based entry UI. */
    MANUAL_ENTRY,
    /** An existing incomplete/needs-review trip was repaired by the user. */
    TRIP_REPAIRED,
    CATEGORY_CHANGED,
    PURPOSE_UPDATED,
    DISTANCE_UPDATED,
    /** The trip start or end location string was edited manually by the user. */
    LOCATION_UPDATED,
    VEHICLE_ASSIGNED,
    NOTE_UPDATED,
    ATTACHMENT_ADDED,
    /** Trip was flagged as a probable/possible duplicate. */
    DUPLICATE_FLAGGED,
    /** Two overlapping trips were merged into one canonical record. */
    TRIP_MERGED,
    /** A frequent-trip suggestion was accepted by the user (Task 4.1). */
    SUGGESTION_ACCEPTED,
    /** Category was assigned automatically by the rule engine (Task 4.2). */
    AUTO_CLASSIFIED,
}
