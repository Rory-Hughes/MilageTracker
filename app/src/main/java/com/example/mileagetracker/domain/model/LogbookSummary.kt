package com.example.mileagetracker.domain.model

/**
 * Aggregated footer totals for the CRA logbook view.
 *
 * Displayed in the sticky summary bar at the bottom of [CraLogbookScreen].
 *
 * ## Business-use percentage
 * [businessUsePercent] is calculated as:
 *
 *   `(businessKm / totalKm) * 100`
 *
 * and is clamped to the range [0, 100].  When [totalKm] is zero the value
 * is 0.0 to avoid a division-by-zero error.
 *
 * @param totalKm             Total km driven by the selected vehicle in [year],
 *                            across all trip categories, used as the denominator
 *                            for the business-use percentage.
 * @param businessKm          Total km of BUSINESS-category trips matching the
 *                            current filter (year + optional vehicleId).
 * @param businessUsePercent  Business use as a percentage of total km (0–100).
 * @param vehicleId           The vehicle filter applied; null means all vehicles.
 * @param year                The tax year these totals apply to.
 */
data class LogbookSummary(
    val totalKm: Float,
    val businessKm: Float,
    val businessUsePercent: Float,
    val vehicleId: Int? = null,
    val year: Int,
)
