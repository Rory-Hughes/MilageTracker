package com.example.mileagetracker.domain.model

/**
 * Domain model representing the complete annual mileage and CRA
 * reimbursement summary for a single vehicle in a single tax year.
 *
 * Produced by [GetAnnualVehicleSummaryUseCase] and consumed by the
 * annual report ViewModel and the `VehicleAnnualSummaryCard` composable.
 *
 * ## Field semantics
 *
 * ### Odometer (from [OdometerCapture] records)
 * - [odometerOpenKm] is the vehicle's odometer reading at the first
 *   capture recorded in [taxYear] (Jan 1 boundary or nearest after).
 *   `null` if no odometer photo or reading was recorded this year.
 * - [odometerCloseKm] is the vehicle's odometer reading at the last
 *   capture recorded in [taxYear] (Dec 31 boundary or nearest before).
 *   `null` if no odometer photo or reading was recorded this year.
 * - [odometerTotalKm] is `closeKm - openKm` when both are non-null;
 *   `null` otherwise. It represents the vehicle's total annual distance
 *   across all uses (business + personal).
 *
 * ### Trip log (from [DriveEntity] records)
 * - [businessKm] is the sum of [DriveEntity.distanceKm] for BUSINESS trips.
 * - [personalKm] is the sum for PERSONAL trips.
 * - [totalLoggedKm] is businessKm + personalKm + other categories.
 *
 * ### Business-use percentage
 * - [businessPercent] is `businessKm / totalLoggedKm * 100`, rounded to
 *   one decimal place. `null` if [totalLoggedKm] is zero.
 *
 * ### Reimbursement
 * - [reimbursementDollars] is the total CRA reimbursement across all
 *   business trips, accounting for the two-tier threshold via
 *   [CalculateReimbursementUseCase]. `null` if no CRA rate was seeded
 *   for [taxYear].
 * - [craRateUsed] is the [CraRate] applied to the reimbursement
 *   calculation. `null` when [reimbursementDollars] is `null`.
 */
data class VehicleAnnualSummary(
    val vehicleId: Int,
    val taxYear: Int,
    val odometerOpenKm: Int?,
    val odometerCloseKm: Int?,
    val odometerTotalKm: Int?,
    val businessKm: Double,
    val personalKm: Double,
    val totalLoggedKm: Double,
    val businessPercent: Double?,
    val reimbursementDollars: Double?,
    val craRateUsed: CraRate?,
    val tripCount: Int,
    val businessTripCount: Int,
) {
    fun odometerDisplayLine(): String =
        odometerOpenKm?.let { open ->
            odometerCloseKm?.let { close ->
                "%,d km \u2192 %,d km (%,d km)".format(open, close, close - open)
            }
        } ?: "No odometer data"

    fun businessPercentDisplay(): String =
        businessPercent?.let { "%.1f %%".format(it) } ?: "\u2014"

    fun reimbursementDisplay(): String =
        reimbursementDollars?.let { "$%,.2f".format(it) } ?: "\u2014"
}
