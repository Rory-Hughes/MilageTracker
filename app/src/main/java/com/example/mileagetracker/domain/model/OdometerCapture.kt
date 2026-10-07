package com.example.mileagetracker.domain.model

/**
 * Domain model representing a single odometer reading for a vehicle.
 *
 * ## CRA purpose
 * The CRA requires that logbook entries be supported by opening and closing
 * odometer readings for each vehicle for each fiscal year.  [OdometerCapture]
 * is the in-memory representation of those readings after mapping from
 * [OdometerCaptureEntity].
 *
 * @param id           Auto-generated primary key (0 = unsaved).
 * @param vehicleId    FK to [VehicleEntity.id] — the vehicle this reading belongs to.
 * @param captureDate  Unix epoch millis of the calendar date the reading was taken.
 * @param readingKm    Odometer value in whole kilometres.
 * @param photoUri     Content URI of the odometer photo, or `null` if no photo was taken.
 *                     After [SaveOdometerCaptureUseCase] runs, this is the **internal**
 *                     app-storage URI (not the camera temp URI).
 * @param captureType  How the reading was obtained — see [OdometerCaptureType].
 * @param notes        Optional free-form note (e.g. "Year-end reading", "Odometer replaced").
 * @param createdAt    Unix epoch millis when the row was inserted.
 */
data class OdometerCapture(
    val id: Int = 0,
    val vehicleId: Int,
    val captureDate: Long,
    val readingKm: Int,
    val taxYear: Int = calendarYear(captureDate),
    val photoUri: String? = null,
    val captureType: OdometerCaptureType = OdometerCaptureType.MANUAL,
    val boundaryType: OdometerBoundaryType = OdometerBoundaryType.OTHER,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

private fun calendarYear(epochMillis: Long): Int =
    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = epochMillis
    }.get(java.util.Calendar.YEAR)
