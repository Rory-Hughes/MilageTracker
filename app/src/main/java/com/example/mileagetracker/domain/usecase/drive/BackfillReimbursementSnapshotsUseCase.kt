package com.example.mileagetracker.domain.usecase.drive

import android.util.Log
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.CraRateRegionRequiredException
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.RateNotSeededException
import java.util.Calendar
import javax.inject.Inject

/**
 * Walks every CRA-eligible drive (BUSINESS / MEDICAL / CHARITY) in the
 * current tax year for the signed-in user and rewrites
 * [DriveEntity.appliedRateSnapshot] + [DriveEntity.reimbursementAmountSnapshot]
 * to match the values the current code paths would produce.
 *
 * ## Why this exists
 * The swipe-to-classify path in `DriveViewModel` historically wrote a wrong
 * value into `appliedRateSnapshot` (the *total* dollars instead of dollars
 * per km) and never set `reimbursementAmountSnapshot` at all. The dashboard
 * sums the latter, so trips classified through that buggy path contribute
 * $0 to the YTD total.  The fix to the VM only helps *future* trips —
 * this backfill repairs the rows already on disk.
 *
 * ## Behaviour
 * - Loads every drive in the current calendar year for the current driver.
 * - Filters to BUSINESS / MEDICAL / CHARITY, non-deleted.
 * - Sorts chronologically and recomputes the BUSINESS reimbursement using
 *   running YTD km so the two-tier formula stays accurate.
 * - For MEDICAL / CHARITY, snapshot values are zeroed (matching what
 *   [ClassifyTripUseCase] / [CreateManualTripUseCase] write today).
 * - Writes back only when the recomputed values differ from what's on disk,
 *   so re-runs are cheap no-ops.
 *
 * ## Failure handling
 * - No signed-in user → returns [Result.Skipped] (cannot fetch drives).
 * - CRA region not configured → returns [Result.Skipped] (would throw on
 *   every drive otherwise).
 * - Rate not seeded for the year → returns [Result.Skipped].
 * - A single drive failing to recompute is logged and skipped; the loop
 *   continues so partial progress lands.
 */
class BackfillReimbursementSnapshotsUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val calculateReimbursementUseCase: CalculateReimbursementUseCase,
    private val currentUserProvider: CurrentUserProvider,
) {

    // Three exits are inherent here: skip when there's no user, abort when the
    // CRA rate/region isn't configured (a whole-run precondition discovered
    // mid-loop), and the normal completion. They read more clearly as guards
    // than as nested state.
    @Suppress("ReturnCount")
    suspend operator fun invoke(): Result {
        val driverUid = runCatching { currentUserProvider.requireUid() }.getOrNull()
            ?: run {
                Log.d(TAG, "Skipped: no signed-in user.")
                return Result.Skipped
            }

        val taxYear = Calendar.getInstance().get(Calendar.YEAR)
        val (yearStart, yearEnd) = yearBounds(taxYear)

        val eligibleDrives = driveRepository
            .getDrivesForAnnualSummary(driverUid, yearStart, yearEnd)
            .filter { it.deletedAt == null && it.tripCategory in CRA_ELIGIBLE_CATEGORIES }
            .sortedBy { it.startedAt }

        var ytdBusinessKm = 0f
        var updated = 0

        for (drive in eligibleDrives) {
            val recomputed = runCatching {
                recomputeSnapshot(drive, taxYear, ytdBusinessKm)
            }.getOrElse { error ->
                when (error) {
                    is CraRateRegionRequiredException,
                    is RateNotSeededException,
                    -> {
                        Log.w(TAG, "Skipped backfill — ${error::class.simpleName}: ${error.message}")
                        return Result.Skipped
                    }
                    else -> {
                        Log.w(
                            TAG,
                            "Drive id=${drive.id} recompute failed (${error::class.simpleName}): " +
                                error.message,
                            error,
                        )
                        null
                    }
                }
            } ?: continue

            if (drive.tripCategory == TripCategory.BUSINESS) {
                ytdBusinessKm += drive.distanceKm
            }

            if (needsRewrite(drive, recomputed)) {
                runCatching { driveRepository.updateDrive(recomputed) }
                    .onSuccess { updated++ }
                    .onFailure { error ->
                        Log.w(
                            TAG,
                            "Drive id=${drive.id} write-back failed: ${error.message}",
                            error,
                        )
                    }
            }
        }

        Log.i(TAG, "Backfill complete: scanned=${eligibleDrives.size}, updated=$updated")
        return Result.Completed(scanned = eligibleDrives.size, updated = updated)
    }

    private suspend fun recomputeSnapshot(
        drive: DriveEntity,
        taxYear: Int,
        ytdBusinessKmBefore: Float,
    ): DriveEntity {
        // Only BUSINESS earns a CRA reimbursement today; MEDICAL / CHARITY
        // mirror what ClassifyTripUseCase writes for those categories (zero).
        val (ratePerKm, totalDollars) = if (drive.tripCategory == TripCategory.BUSINESS) {
            val result = calculateReimbursementUseCase(
                taxYear = taxYear,
                newDistanceKm = drive.distanceKm,
                ytdKmBefore = ytdBusinessKmBefore,
            )
            (result.firstTierRateCents / CENTS_PER_DOLLAR) to result.amountDollars.toFloat()
        } else {
            0f to null
        }

        return drive.copy(
            appliedRateSnapshot = ratePerKm,
            reimbursementAmountSnapshot = totalDollars,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun needsRewrite(current: DriveEntity, recomputed: DriveEntity): Boolean =
        current.appliedRateSnapshot != recomputed.appliedRateSnapshot ||
            current.reimbursementAmountSnapshot != recomputed.reimbursementAmountSnapshot

    private fun yearBounds(year: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return start to end
    }

    sealed interface Result {
        data class Completed(val scanned: Int, val updated: Int) : Result
        data object Skipped : Result
    }

    private companion object {
        const val TAG = "ReimbursementBackfill"
        const val CENTS_PER_DOLLAR = 100f

        val CRA_ELIGIBLE_CATEGORIES = setOf(
            TripCategory.BUSINESS,
            TripCategory.MEDICAL,
            TripCategory.CHARITY,
        )
    }
}
