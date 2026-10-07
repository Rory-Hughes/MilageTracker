package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.domain.repository.DriveRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Back-fills auto-classification for all unclassified (PENDING) trips
 * belonging to [driverUid].
 *
 * ## When to call
 * - On first app launch after Task 4.2 ships, to classify legacy trips.
 * - From a Settings screen “Re-classify trips” action.
 * - From a background [WorkManager] job scheduled during quiet periods.
 *
 * ## Behaviour
 * 1. Takes a one-shot snapshot of unclassified trips via
 *    [DriveRepository.getUnclassifiedDrives] collected with [Flow.first].
 * 2. Runs [AutoClassifyTripUseCase] for each trip with `force = false` so
 *    that any trip a human has already touched is skipped.
 * 3. Accumulates results into a [BatchResult] summary and returns it.
 *
 * Processing is sequential (not parallel) to avoid saturating the database
 * write queue.  A plain `for` loop is used so that each
 * [AutoClassifyTripUseCase] suspend call runs in the correct coroutine context.
 *
 * @param driverUid   Authenticated Firebase UID.
 * @param onProgress  Optional callback invoked after each trip is processed;
 *                    useful for driving a progress indicator in the UI.
 *                    Receives `(processedCount, totalCount)`.
 */
class AutoClassifyPendingTripsUseCase @Inject constructor(
    private val driveRepository:        DriveRepository,
    private val autoClassifyTripUseCase: AutoClassifyTripUseCase,
) {
    suspend operator fun invoke(
        driverUid:  String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null,
    ): BatchResult {
        // One-shot snapshot — Flow.first() is a suspend fun and legal here.
        val pending = driveRepository.getUnclassifiedDrives(driverUid).first()
        val total   = pending.size

        var classified     = 0
        var flaggedReview  = 0
        var noRuleMatched  = 0
        var skipped        = 0
        var errors         = 0

        // Plain for-loop: suspend calls are legal inside a coroutine body.
        for ((index, drive) in pending.withIndex()) {
            try {
                when (autoClassifyTripUseCase(
                    driveId  = drive.id,
                    actorUid = AutoClassifyTripUseCase.SYSTEM_ACTOR,
                    force    = false,
                )) {
                    is AutoClassifyResult.Classified       -> classified++
                    is AutoClassifyResult.FlaggedForReview -> flaggedReview++
                    AutoClassifyResult.NoRuleMatched       -> noRuleMatched++
                    is AutoClassifyResult.Skipped          -> skipped++
                    AutoClassifyResult.NotFound            -> skipped++
                }
            } catch (_: Exception) {
                errors++
            }
            onProgress?.invoke(index + 1, total)
        }

        return BatchResult(
            total         = total,
            classified    = classified,
            flaggedReview = flaggedReview,
            noRuleMatched = noRuleMatched,
            skipped       = skipped,
            errors        = errors,
        )
    }

    /**
     * Summary of a batch auto-classification run.
     *
     * @param total          Total trips processed.
     * @param classified     Trips assigned a category by a rule.
     * @param flaggedReview  Trips flagged as NEEDS_REVIEW.
     * @param noRuleMatched  Trips where no rule fired; remain PENDING.
     * @param skipped        Trips skipped (already human-reviewed or not found).
     * @param errors         Trips that threw an exception during processing.
     */
    data class BatchResult(
        val total:         Int,
        val classified:    Int,
        val flaggedReview: Int,
        val noRuleMatched: Int,
        val skipped:       Int,
        val errors:        Int,
    )
}
