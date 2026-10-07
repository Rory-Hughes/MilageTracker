package com.example.mileagetracker.domain.model

/**
 * A reimbursement forecast for a single driver over a given pay period.
 *
 * Produced by [GetReimbursementForecastUseCase] and consumed by
 * [TeamDashboardViewModel] and [ManagerAnalyticsViewModel].
 *
 * ## How the projection works
 * The use case takes the driver's actual business km logged *so far* in the
 * current period and extrapolates linearly to the end of the period based on
 * days elapsed vs. days remaining.  The resulting [projectedBusinessKm] is
 * then multiplied by the applicable rate to produce [projectedReimbursement].
 *
 * @property driverUid            Firebase UID of the driver.
 * @property driverDisplayName    Human-readable name for UI display.
 * @property periodLabel          Human-readable period, e.g. "May 2026".
 * @property actualBusinessKm     Business km driven so far in this period.
 * @property projectedBusinessKm  Linear extrapolation of [actualBusinessKm]
 *                                to the end of the period based on the
 *                                fraction of period elapsed.
 * @property actualReimbursement  Reimbursement earned so far (dollars).
 * @property projectedReimbursement Projected total reimbursement at period
 *                                  end (dollars).
 * @property pendingApprovalCount Number of [ApprovalRequest]s in [ApprovalStatus.PENDING]
 *                                for this driver.
 * @property rateLabel            Human-readable rate string, e.g. "$0.73 / km"
 *                                or "Custom: $0.55 / km".
 */
data class ReimbursementForecast(
    val driverUid: String,
    val driverDisplayName: String,
    val periodLabel: String,
    val actualBusinessKm: Float,
    val projectedBusinessKm: Float,
    val actualReimbursement: Float,
    val projectedReimbursement: Float,
    val pendingApprovalCount: Int,
    val rateLabel: String,
)
