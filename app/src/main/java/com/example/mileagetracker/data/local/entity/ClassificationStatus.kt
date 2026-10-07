package com.example.mileagetracker.data.local.entity

/**
 * Tracks the review/classification lifecycle of a recorded trip.
 *
 * Stored as its [name] string via a Room @TypeConverter so that reordering
 * entries never causes silent data corruption.
 *
 * ## Values
 * - [PENDING]           – trip has been recorded but not yet reviewed or classified.
 * - [AUTO_CLASSIFIED]   – category was assigned automatically (e.g. by rule engine or ML).
 * - [USER_CONFIRMED]    – a human explicitly confirmed or assigned the category.
 * - [USER_CORRECTED]    – a human overrode a prior auto-classification.
 * - [NEEDS_REVIEW]      – trip has been flagged for manual review before finalisation.
 *                         Set by [DetectDuplicateTripUseCase] when a candidate trip
 *                         overlaps an existing trip by more than [DuplicateCheckResult.FLAGGED_THRESHOLD]
 *                         but less than [DuplicateCheckResult.PROBABLE_DUPLICATE_THRESHOLD].
 *                         Also used for any other automated system that identifies a
 *                         trip requiring human attention (e.g. implausible speed, GPS
 *                         jitter above threshold).
 *                         Trips in this status are persisted to the `drives` table but
 *                         excluded from reimbursement calculations until a user resolves
 *                         them via the review screen.
 * - [INCOMPLETE]        – trip record is missing one or more required fields
 *                         (e.g. start location, end location, or distance) and cannot
 *                         be used for CRA reporting until the user repairs it via
 *                         [ManualTripEntryScreen] in repair mode (Task 3.2).
 *                         Typical causes: GPS signal lost at trip start/end, or a
 *                         legacy record that was partially migrated.
 *                         Like [NEEDS_REVIEW], these trips are excluded from
 *                         reimbursement calculations until resolved.
 */
enum class ClassificationStatus {
    PENDING,
    AUTO_CLASSIFIED,
    USER_CONFIRMED,
    USER_CORRECTED,

    /** Trip requires human review before it can be classified or reimbursed. */
    NEEDS_REVIEW,

    /**
     * Trip is missing required fields and cannot be used for CRA reporting
     * until repaired via [ManualTripEntryScreen] in repair mode.
     */
    INCOMPLETE,
}
