package com.example.mileagetracker.domain.model

/**
 * Trip completeness snapshot produced by [GetReportReadinessUseCase].
 *
 * A trip is CRA-ready when it has a non-blank purpose, an assigned vehicle,
 * a non-personal category, and a non-zero distance.
 *
 * @param tripId         PK of the DriveEntity this snapshot refers to.
 * @param missingFields  Ordered list of fields the trip is still missing.
 * @param readinessScore Fraction of required fields present; range [0.0, 1.0].
 */
data class ReportReadiness(
    val tripId: Int,
    val missingFields: List<MissingField>,
    val readinessScore: Float,
) {
    val isReady: Boolean get() = readinessScore >= 1.0f
}

/**
 * CRA fields that may be absent on a trip entry.
 * Used as chip labels in [ExceptionsListScreen].
 */
enum class MissingField {
    PURPOSE,
    VEHICLE,
    CATEGORY,
    DISTANCE,
    ATTACHMENT,
}
