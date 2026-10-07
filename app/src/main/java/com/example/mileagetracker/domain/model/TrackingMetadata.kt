package com.example.mileagetracker.domain.model

/**
 * Immutable snapshot of how and under what conditions a trip was recorded.
 *
 * Stored as embedded columns on [DriveEntity] (prefixed `meta_`) via Room's
 * `@Embedded` annotation. Keeping it as a dedicated class makes it easy to
 * pass through the service → repository → use-case pipeline without unpacking
 * individual fields at every boundary.
 *
 * ## CRA audit relevance
 * [detectionSource], [confidenceScore], and the battery fields together form a
 * machine-readable provenance trail that supports CRA audit queries such as
 * "was this trip auto-detected or manually entered?" and
 * "was battery saver on (which degrades GPS accuracy)?".
 */
data class TrackingMetadata(

    /**
     * Whether the trip was started automatically (activity recognition / geofence trigger)
     * or manually by the user pressing "Start".
     */
    val detectionSource: DetectionSource,

    /**
     * 0.0–1.0 confidence that this is a genuine vehicle trip.
     * Set by the activity-recognition pipeline; 1.0 for MANUAL trips.
     */
    val confidenceScore: Float,

    /** Battery level (0–100) recorded when [DriveTrackingService.startTracking] ran. */
    val batteryLevelAtStart: Int?,

    /** Battery level (0–100) recorded when [DriveTrackingService.stopTracking] ran. */
    val batteryLevelAtEnd: Int?,

    /**
     * True if Android Battery Saver was active during any part of the trip.
     * Relevant because Battery Saver restricts background location updates,
     * potentially reducing GPS accuracy or sampling frequency.
     */
    val wasBatterySaverActive: Boolean,
)

/** How the trip recording session was initiated. */
enum class DetectionSource {
    /** Trip started automatically via activity recognition or geofencing. */
    AUTO,

    /** Trip started explicitly by the user. */
    MANUAL,
}
