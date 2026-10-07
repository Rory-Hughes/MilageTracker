package com.example.mileagetracker.domain.model

/**
 * Result of a duplicate-trip check performed by [DetectDuplicateTripUseCase].
 *
 * ## Decision thresholds
 * The thresholds below are configurable constants.  All overlap percentages
 * are expressed as a value in [0.0, 1.0] (e.g. 0.8 = 80%).
 *
 * | Overlap range                             | Result              | Action |
 * |-------------------------------------------|---------------------|--------|
 * | < [FLAGGED_THRESHOLD] (< 40%)             | [Clear]             | Persist normally |
 * | ≥ [FLAGGED_THRESHOLD] and < [PROBABLE_DUPLICATE_THRESHOLD] | [Flagged] | Persist with NEEDS_REVIEW |
 * | ≥ [PROBABLE_DUPLICATE_THRESHOLD] (≥ 80%)  | [ProbableDuplicate] | Hold in buffer, show notification |
 *
 * ## Why a sealed class?
 * The three outcomes are exhaustive and mutually exclusive.  A sealed
 * class allows callers to use `when` expressions that the compiler
 * verifies are exhaustive, eliminating silent fall-through bugs.
 */
sealed class DuplicateCheckResult {

    /**
     * No significant temporal or spatial overlap found.
     * The candidate trip can be persisted without any review flag.
     */
    data object Clear : DuplicateCheckResult()

    /**
     * A meaningful overlap was detected but it is below the
     * [PROBABLE_DUPLICATE_THRESHOLD].  The trip should be persisted with
     * [com.example.mileagetracker.data.local.entity.ClassificationStatus.NEEDS_REVIEW]
     * so the user can inspect it.
     *
     * @param existingTripId  PK of the trip that overlaps with the candidate.
     * @param overlapPercent  Fraction of the candidate’s duration that overlaps
     *                        [existingTripId], in [0.0, 1.0].
     */
    data class Flagged(
        val existingTripId: Int,
        val overlapPercent: Float,
    ) : DuplicateCheckResult()

    /**
     * Overlap is ≥ [PROBABLE_DUPLICATE_THRESHOLD] — the candidate is very
     * likely a duplicate of [existingTripId].  The trip should be held in
     * [com.example.mileagetracker.data.local.TripBufferStore] and a user
     * notification surfaced rather than silently persisting.
     *
     * @param existingTripId PK of the existing trip with maximum overlap.
     */
    data class ProbableDuplicate(
        val existingTripId: Int,
        val overlapPercent: Float,
    ) : DuplicateCheckResult()

    companion object {
        /**
         * Minimum overlap fraction to trigger a [Flagged] result.
         * Configurable; defaults to 40%.
         */
        const val FLAGGED_THRESHOLD: Float = 0.40f

        /**
         * Minimum overlap fraction to trigger a [ProbableDuplicate] result.
         * Configurable; defaults to 80% (the exit-criteria threshold).
         */
        const val PROBABLE_DUPLICATE_THRESHOLD: Float = 0.80f
    }
}
