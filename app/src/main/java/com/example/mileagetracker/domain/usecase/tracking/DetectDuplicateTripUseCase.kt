package com.example.mileagetracker.domain.usecase.tracking

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.model.DuplicateCheckResult
import com.example.mileagetracker.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Checks whether a candidate [DriveEntity] temporally overlaps any existing
 * trip for the same driver, and returns a [DuplicateCheckResult] classifying
 * the degree of overlap.
 *
 * ## Algorithm
 * 1. Compute the candidate’s time window: [[candidate.startedAt],
 *    [candidate.endedAt]] (or `now` if endedAt is null).
 * 2. Expand the window by [TIME_WINDOW_BUFFER_MS] (5 minutes) in each
 *    direction to catch crash-restart and re-trigger scenarios where the
 *    new trip starts slightly before the old one ends.
 * 3. Query [DriveRepository.getDrivesInTimeWindow] with the expanded window.
 * 4. For each returned trip, calculate the temporal overlap as a fraction of
 *    the candidate’s total duration:
 *
 *    ```
 *    overlapMs  = min(candidateEnd, existingEnd) - max(candidateStart, existingStart)
 *    overlapPct = overlapMs / candidateDurationMs
 *    ```
 * 5. Take the trip with the highest overlap fraction:
 *    - ≥ [DuplicateCheckResult.PROBABLE_DUPLICATE_THRESHOLD] → [DuplicateCheckResult.ProbableDuplicate]
 *    - ≥ [DuplicateCheckResult.FLAGGED_THRESHOLD]            → [DuplicateCheckResult.Flagged]
 *    - Otherwise                                             → [DuplicateCheckResult.Clear]
 *
 * ## Why time-only overlap?
 * Spatial bounding-box checks require geocoding or reverse-geocoding which
 * adds latency and network dependency at trip-end.  Temporal overlap is a
 * reliable proxy for duplicates caused by crash-restart or double-trigger:
 * two genuinely different trips are extremely unlikely to share >40% of
 * their duration.  Spatial analysis can be layered in a future task.
 *
 * ## Thread safety
 * Designed to be called from a [kotlinx.coroutines.CoroutineScope] on
 * [kotlinx.coroutines.Dispatchers.IO].  [DriveRepository.getDrivesInTimeWindow]
 * delegates to Room which handles its own dispatcher.
 */
class DetectDuplicateTripUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
) {

    /**
     * @param candidate  The trip to check.  May have [DriveEntity.id] == 0
     *                   (not yet persisted) or a non-zero ID (re-check after save).
     * @return [DuplicateCheckResult] indicating the outcome.
     */
    suspend operator fun invoke(candidate: DriveEntity): DuplicateCheckResult {
        val driverUid = candidate.driverUid?.takeIf { it.isNotBlank() }
        return driverUid?.let { resolvedDriverUid ->
            detectDuplicate(candidate, resolvedDriverUid)
        } ?: DuplicateCheckResult.Clear
    }

    private suspend fun detectDuplicate(
        candidate: DriveEntity,
        driverUid: String,
    ): DuplicateCheckResult {
        val candidateStart = candidate.startedAt
        val candidateEnd = candidate.endedAt ?: System.currentTimeMillis()
        val candidateDuration = (candidateEnd - candidateStart).coerceAtLeast(1L)

        val nearby = driveRepository.getDrivesInTimeWindow(
            driverUid = driverUid,
            windowStartMs = candidateStart - TIME_WINDOW_BUFFER_MS,
            windowEndMs = candidateEnd + TIME_WINDOW_BUFFER_MS,
            excludeId = candidate.id,
        )

        val bestMatch = nearby.fold(BestOverlap()) { best, existing ->
            bestOverlap(
                currentBest = best,
                candidateStart = candidateStart,
                candidateEnd = candidateEnd,
                candidateDuration = candidateDuration,
                existing = existing,
            )
        }

        return bestMatch.toResult()
    }

    private fun bestOverlap(
        currentBest: BestOverlap,
        candidateStart: Long,
        candidateEnd: Long,
        candidateDuration: Long,
        existing: DriveEntity,
    ): BestOverlap {
        val existingEnd = existing.endedAt ?: System.currentTimeMillis()
        val overlapMs = minOf(candidateEnd, existingEnd) - maxOf(candidateStart, existing.startedAt)
        val overlapFrac = overlapMs.toFloat() / candidateDuration.toFloat()
        return if (overlapMs > 0 && overlapFrac > currentBest.overlapFraction) {
            BestOverlap(existing.id, overlapFrac)
        } else {
            currentBest
        }
    }

    private data class BestOverlap(
        val tripId: Int = 0,
        val overlapFraction: Float = 0f,
    ) {
        fun toResult(): DuplicateCheckResult = when {
            overlapFraction >= DuplicateCheckResult.PROBABLE_DUPLICATE_THRESHOLD ->
                DuplicateCheckResult.ProbableDuplicate(
                    existingTripId = tripId,
                    overlapPercent = overlapFraction,
                )
            overlapFraction >= DuplicateCheckResult.FLAGGED_THRESHOLD ->
                DuplicateCheckResult.Flagged(
                    existingTripId = tripId,
                    overlapPercent = overlapFraction,
                )
            else -> DuplicateCheckResult.Clear
        }
    }

    companion object {
        /** Buffer added to each side of the candidate’s time window when querying nearby trips. */
        const val TIME_WINDOW_BUFFER_MS = 5 * 60 * 1_000L // 5 minutes
    }
}
