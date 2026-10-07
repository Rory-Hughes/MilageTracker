package com.example.mileagetracker.domain.model

/**
 * Aggregated mileage summary for a discrete time period.
 *
 * Produced by [GetTripSummaryUseCase] and consumed by [ReportsViewModel] /
 * [ReportSummaryCard].  Each instance represents one bucket (month, quarter,
 * or year) of aggregated drive data for a single user and optional vehicle.
 *
 * ## Reimbursement calculation
 * [estimatedReimbursement] is computed as:
 *
 *   `businessKm × craRatePerKm`
 *
 * where `craRatePerKm` is sourced from [RateRepository.getRateForYearOnce]
 * for the year that contains this period.  The value is an *estimate* because
 * the final CRA rate for a given tax year may not be confirmed until after
 * the year ends.  Reports label it accordingly.
 *
 * @param periodLabel            Human-readable label, e.g. "May 2026",
 *                               "Q2 2026", or "2026".
 * @param periodType             [ReportPeriod] that produced this summary.
 * @param year                   Calendar year this summary belongs to.
 * @param totalKm                Sum of [DriveEntity.distanceKm] for all
 *                               active (non-deleted) drives in the period.
 * @param businessKm             Sum of distance for [TripCategory.BUSINESS]
 *                               drives only.
 * @param personalKm             Sum of distance for [TripCategory.PERSONAL]
 *                               drives only.
 * @param tripCount              Number of active drives in the period.
 * @param estimatedReimbursement `businessKm × craRatePerKm` in CAD.
 *                               Zero when no business trips exist or no
 *                               CRA rate is seeded for the year.
 * @param vehicleId              Non-null when the summary was filtered to a
 *                               specific vehicle; null for all-vehicle summaries.
 */
data class TripSummary(
    val periodLabel: String,
    val periodType: ReportPeriod,
    val year: Int,
    val totalKm: Float,
    val businessKm: Float,
    val personalKm: Float,
    val tripCount: Int,
    val estimatedReimbursement: Float,
    val vehicleId: Int? = null,
)
