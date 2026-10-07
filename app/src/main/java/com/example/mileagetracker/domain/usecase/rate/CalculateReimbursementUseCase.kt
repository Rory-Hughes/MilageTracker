package com.example.mileagetracker.domain.usecase.rate

import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.seeder.CraRateCatalog
import com.example.mileagetracker.domain.model.CraRateRegionRequiredException
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.min

/**
 * Calculates the CRA mileage reimbursement for one or more drives using
 * the two-tier rate structure.
 *
 * ## CRA two-tier formula
 * The CRA threshold resets every January 1.  For a given [taxYear]:
 *
 * ```
 * firstTierKm  = min(totalYtdKm, firstTierLimitKm) - min(ytdKmBefore, firstTierLimitKm)
 * secondTierKm = newKm - firstTierKm
 *
 * reimbursementCents = (firstTierKm  × firstTierRateCents)
 *                    + (secondTierKm × additionalKmRateCents)
 * ```
 *
 * Where:
 * - `ytdKmBefore`  = total business km already driven this year *before*
 *   the drive(s) being calculated.
 * - `newKm`        = distance of the drive(s) being calculated.
 * - `totalYtdKm`   = `ytdKmBefore + newKm`.
 *
 * ## Integer arithmetic
 * All intermediate values are kept in **integer cents** to avoid
 * floating-point rounding errors in financial calculations.  Distances
 * are rounded to the nearest whole kilometre before multiplication.
 * The result [ReimbursementResult.amountCents] is in cents; divide by
 * 100 for display dollars.
 *
 * ## Not-seeded handling
 * If no rate has been seeded for [taxYear], the use case throws
 * [RateNotSeededException] so callers are forced to handle the missing
 * data explicitly rather than silently producing zero-dollar results.
 */
class CalculateReimbursementUseCase @Inject constructor(
    private val rateRepository: RateRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Calculate the reimbursement for [newDistanceKm] business kilometres
     * driven in [taxYear].
     *
     * @param taxYear       the CRA tax year (e.g. 2026).
     * @param newDistanceKm the distance of the drive(s) to calculate, in km.
     * @param ytdKmBefore   total business km already driven this year
     *                      *before* [newDistanceKm].  Defaults to 0 for
     *                      the first drive of the year.
     * @return a [ReimbursementResult] containing the amount in cents and
     *         metadata useful for display and audit snapshots.
     * @throws RateNotSeededException if no rate has been seeded for [taxYear].
     * @throws IllegalArgumentException if [newDistanceKm] or [ytdKmBefore]
     *         is negative.
     */
    suspend operator fun invoke(
        taxYear: Int,
        newDistanceKm: Float,
        ytdKmBefore: Float = 0f,
    ): ReimbursementResult {
        require(newDistanceKm >= 0f) {
            "newDistanceKm must be ≥ 0, got $newDistanceKm"
        }
        require(ytdKmBefore >= 0f) {
            "ytdKmBefore must be ≥ 0, got $ytdKmBefore"
        }

        val region = requireCraRateRegion()
        val catalogRate = requireCatalogRate(taxYear, region)
        val rate = requirePersistedRate(taxYear)

        return calculate(
            rate = rate.copy(
                firstTierRateCents = catalogRate.firstTier,
                additionalKmRateCents = catalogRate.additional,
                firstTierLimitKm = catalogRate.firstTierLimitKm,
            ),
            newDistanceKm = newDistanceKm,
            ytdKmBefore = ytdKmBefore,
        )
    }

    /**
     * Pure calculation overload that accepts an already-resolved
     * [CraRateEntity].  Useful in batch-calculation loops where the
     * same rate entity is reused across many drives to avoid redundant
     * database lookups.
     *
     * @param rate          the resolved [CraRateEntity] for the target year.
     * @param newDistanceKm the distance of the drive(s) to calculate, in km.
     * @param ytdKmBefore   total business km already driven before this drive.
     * @return a [ReimbursementResult].
     */
    fun calculate(
        rate: CraRateEntity,
        newDistanceKm: Float,
        ytdKmBefore: Float = 0f,
    ): ReimbursementResult {
        // Work in whole kilometres (round half-up) to match CRA guidance
        val newKm         = newDistanceKm.roundToWholeKm()
        val beforeKm      = ytdKmBefore.roundToWholeKm()
        val totalYtdKm    = beforeKm + newKm
        val limit         = rate.firstTierLimitKm

        // Kilometres in each tier for THIS drive
        val firstTierKm  = min(totalYtdKm, limit) - min(beforeKm, limit)
        val secondTierKm = newKm - firstTierKm

        val amountCents = (firstTierKm  * rate.firstTierRateCents) +
                          (secondTierKm * rate.additionalKmRateCents)

        return ReimbursementResult(
            taxYear              = rate.taxYear,
            newDistanceKm        = newDistanceKm,
            ytdKmBefore          = ytdKmBefore,
            firstTierKm          = firstTierKm,
            secondTierKm         = secondTierKm,
            firstTierLimitKm     = limit,
            firstTierRateCents   = rate.firstTierRateCents,
            additionalRateCents  = rate.additionalKmRateCents,
            amountCents          = amountCents,
        )
    }

    // Rounds a Float distance to the nearest whole kilometre (half-up)
    private fun Float.roundToWholeKm(): Int = (this + 0.5f).toInt()

    private suspend fun requireCraRateRegion() =
        settingsRepository.getCraRateRegion().first()
            ?: throw CraRateRegionRequiredException()

    private fun requireCatalogRate(
        taxYear: Int,
        region: com.example.mileagetracker.domain.model.CraRateRegion,
    ) = CraRateCatalog.resolve(taxYear, region)
        ?: throw RateNotSeededException(taxYear)

    private suspend fun requirePersistedRate(taxYear: Int) =
        rateRepository.getRateForYearOnce(taxYear)
            ?: throw RateNotSeededException(taxYear)
}

// ───────────────────────────────────────────────────────────────────────────
// Result type
// ───────────────────────────────────────────────────────────────────────────

/**
 * The result of a single CRA reimbursement calculation.
 *
 * Contains all inputs and intermediate values alongside the final
 * [amountCents] so that:
 * - The UI can show a transparent rate breakdown ("5 km @ $0.73 + 3 km
 *   @ $0.67 = $5.66").
 * - The value can be snapshotted directly into [DriveEntity.appliedRateSnapshot]
 *   and [DriveEntity.reimbursementAmountSnapshot] without re-querying
 *   the rate table, ensuring the stored snapshot is immutable even if
 *   the seeded rate is later corrected.
 *
 * @property amountCents total reimbursement in integer cents.
 * @property amountDollars convenience property: [amountCents] / 100.0.
 */
data class ReimbursementResult(
    val taxYear: Int,
    val newDistanceKm: Float,
    val ytdKmBefore: Float,
    val firstTierKm: Int,
    val secondTierKm: Int,
    val firstTierLimitKm: Int,
    val firstTierRateCents: Int,
    val additionalRateCents: Int,
    val amountCents: Int,
) {
    /** Total reimbursement in dollars, rounded to two decimal places for display. */
    val amountDollars: Double get() = amountCents / 100.0

    /** True when all kilometres fell within the first tier (ytd never crossed the threshold). */
    val isAllFirstTier: Boolean get() = secondTierKm == 0

    /** True when all kilometres fell in the second tier (ytd was already past the threshold). */
    val isAllSecondTier: Boolean get() = firstTierKm == 0
}

// ───────────────────────────────────────────────────────────────────────────
// Exception
// ───────────────────────────────────────────────────────────────────────────

/**
 * Thrown by [CalculateReimbursementUseCase] when no CRA rate has been
 * seeded for [taxYear].
 *
 * Callers should catch this and either:
 * - Show an error to the user prompting them to wait for rate data
 * - Trigger [CraRateSeeder] manually
 * - Fall back to a policy rate if a custom rate is available
 */
class RateNotSeededException(val taxYear: Int) :
    Exception("No CRA rate found for tax year $taxYear. Run CraRateSeeder first.")
