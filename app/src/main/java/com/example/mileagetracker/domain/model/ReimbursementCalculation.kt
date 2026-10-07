package com.example.mileagetracker.domain.model

import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult

/**
 * Rich domain model representing the full output of a CRA two-tier
 * reimbursement calculation.
 *
 * This is the Phase 5 plan's specified result type, designed to drive
 * the `ReportSummaryCard` tier-breakdown UI:
 *
 * ```
 * Tier 1:  5,000 km × $0.73 = $3,650.00
 * Tier 2:  1,000 km × $0.67 =   $670.00
 * ─────────────────────────────────────
 * Total:                       $4,380.00
 * ```
 *
 * ## Relationship to ReimbursementResult
 * [ReimbursementResult] (produced by [CalculateReimbursementUseCase]) is
 * the low-level, integer-cent calculation type used in use-case logic.
 * [ReimbursementCalculation] is the richer domain view used by ViewModels
 * and the UI — it carries the resolved [CraRate] and pre-computed dollar
 * amounts so callers never have to do their own cents → dollars conversion.
 *
 * Convert via [ReimbursementCalculation.fromResult].
 *
 * ## Precision
 * Dollar amounts are [Double] rounded to 2 decimal places for display.
 * All intermediate arithmetic is done in integer cents by
 * [CalculateReimbursementUseCase]; the doubles here are display-only.
 *
 * @property inputKm          The total business km passed into the calculation.
 * @property year             The CRA tax year the calculation applies to.
 * @property tier1Km          Km that fell within the first tier.
 * @property tier2Km          Km that fell beyond the tier threshold.
 * @property tier1Amount      Dollar reimbursement for [tier1Km].
 * @property tier2Amount      Dollar reimbursement for [tier2Km].
 * @property totalAmount      [tier1Amount] + [tier2Amount].
 * @property rateUsed         The [CraRate] that was applied.
 * @property ytdKmBefore      YTD business km before this calculation (for context).
 */
data class ReimbursementCalculation(
    val inputKm: Double,
    val year: Int,
    val tier1Km: Double,
    val tier2Km: Double,
    val tier1Amount: Double,
    val tier2Amount: Double,
    val totalAmount: Double,
    val rateUsed: CraRate,
    val ytdKmBefore: Double = 0.0,
) {
    // ── Display helpers ────────────────────────────────────────────────────────

    /** True when all km fell within the first tier (common for low-mileage users). */
    val isAllFirstTier: Boolean get() = tier2Km == 0.0

    /** True when all km fell beyond the threshold (common for high-mileage YTD offsets). */
    val isAllSecondTier: Boolean get() = tier1Km == 0.0

    /**
     * Returns the tier-breakdown lines suitable for display in
     * `ReportSummaryCard`, e.g.:
     * ```
     * ["5,000 km × $0.73 = $3,650.00", "1,000 km × $0.67 = $670.00"]
     * ```
     * Single-tier results return a one-element list.
     */
    fun breakdownLines(): List<String> = buildList {
        if (tier1Km > 0.0) {
            add(
                "%,.0f km × $%.2f = $%,.2f".format(
                    tier1Km,
                    rateUsed.tier1RateDollars,
                    tier1Amount,
                )
            )
        }
        if (tier2Km > 0.0) {
            add(
                "%,.0f km × $%.2f = $%,.2f".format(
                    tier2Km,
                    rateUsed.tier2RateDollars,
                    tier2Amount,
                )
            )
        }
    }

    /**
     * Single-line total string for compact display:
     * `"$4,380.00 (6,000 km)"`.
     */
    fun totalLine(): String = "$%,.2f (%,.0f km)".format(totalAmount, inputKm)

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        /**
         * Constructs a [ReimbursementCalculation] from a [ReimbursementResult]
         * (the low-level use-case output) and the [CraRate] that was applied.
         *
         * This is the canonical bridge between the calculation layer and the
         * UI/domain layer.  Call this immediately after
         * [CalculateReimbursementUseCase.invoke] or [CalculateReimbursementUseCase.calculate].
         *
         * ```kotlin
         * val result = calculateReimbursementUseCase(taxYear, distanceKm, ytdKmBefore)
         * val rate   = rateRepository.getRateForYearOnce(taxYear)!!.toDomain()
         * val calc   = ReimbursementCalculation.fromResult(result, rate)
         * ```
         *
         * @param result The [ReimbursementResult] from [CalculateReimbursementUseCase].
         * @param rate   The [CraRate] applied — obtained via [CraRateEntity.toDomain].
         */
        fun fromResult(
            result: ReimbursementResult,
            rate: CraRate,
        ): ReimbursementCalculation = ReimbursementCalculation(
            inputKm = result.newDistanceKm.toDouble(),
            year = result.taxYear,
            tier1Km = result.firstTierKm.toDouble(),
            tier2Km = result.secondTierKm.toDouble(),
            tier1Amount = (result.firstTierKm * rate.tier1RateCentsPerKm) / 100.0,
            tier2Amount = (result.secondTierKm * rate.tier2RateCentsPerKm) / 100.0,
            totalAmount = result.amountDollars,
            rateUsed = rate,
            ytdKmBefore = result.ytdKmBefore.toDouble(),
        )
    }
}
