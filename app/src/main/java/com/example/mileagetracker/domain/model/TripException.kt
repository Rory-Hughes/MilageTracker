package com.example.mileagetracker.domain.model

/**
 * Represents a single flagged exception on a trip that requires user attention
 * before the trip can be included in a CRA-ready report.
 *
 * Exceptions are derived at read-time from the trip's [ClassificationStatus],
 * [ReportReadiness] score, and audit history — they are not stored as a
 * separate database table.  [resolvedAt] is non-null once the user has
 * acknowledged or fixed the underlying issue.
 *
 * @param tripId       The [DriveEntity.id] this exception belongs to.
 * @param exceptionType The category of problem detected.
 * @param detectedAt   Epoch millis when this exception was first identified.
 * @param resolvedAt   Epoch millis when the exception was resolved, or null
 *                     if still active.
 */
data class TripException(
    val tripId: Int,
    val exceptionType: ExceptionType,
    val detectedAt: Long,
    val resolvedAt: Long? = null,
) {
    /** True while no resolution timestamp has been recorded. */
    val isActive: Boolean get() = resolvedAt == null
}

/**
 * Describes the kind of problem that raised a [TripException].
 *
 * Displayed in plain language via [ExceptionFlagBanner] on TripDetailScreen
 * and as filter tabs in [ExceptionsListScreen].
 */
enum class ExceptionType {

    /**
     * The trip was flagged as a probable or possible duplicate by
     * [DetectDuplicateTripUseCase].  The user must confirm or dismiss
     * before the trip contributes to reimbursement totals.
     */
    DUPLICATE_FLAGGED,

    /**
     * The trip is missing one or more fields required for CRA reporting
     * (purpose, vehicle, category, or non-zero distance).
     * Corresponds to [ClassificationStatus.INCOMPLETE].
     */
    INCOMPLETE_DATA,

    /**
     * The recorded route is geometrically or temporally implausible
     * (e.g. average speed > 200 km/h, GPS jitter producing a negative
     * distance, or start time after end time).
     */
    IMPOSSIBLE_ROUTE,

    /**
     * A previous auto-classification was manually overridden by the user
     * but the new values have not yet been confirmed.  Raised when
     * [ClassificationStatus.USER_CORRECTED] is set without a subsequent
     * [AuditEventType.TRIP_REPAIRED] event.
     */
    MANUAL_OVERRIDE,

    /**
     * The trip was explicitly placed in [ClassificationStatus.NEEDS_REVIEW]
     * by the system (e.g. duplicate detector, speed anomaly checker).
     * Catch-all for any automated flagging that does not fit a more
     * specific [ExceptionType].
     */
    NEEDS_REVIEW,
}
