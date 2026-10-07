package com.example.mileagetracker.domain.usecase.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.domain.model.DuplicateCheckResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [DetectDuplicateTripUseCase] against a real
 * (in-memory) SQLite database.
 *
 * The use case queries [DriveRepository.getDrivesInTimeWindow], which expands
 * the candidate's `[startedAt, endedAt]` window by
 * [DetectDuplicateTripUseCase.TIME_WINDOW_BUFFER_MS] (5 minutes) on each side
 * before querying.  The overlap fraction is computed as:
 * ```
 * overlapMs  = min(candidateEnd, existingEnd) − max(candidateStart, existingStart)
 * overlapFrac = overlapMs / candidateDurationMs
 * ```
 *
 * Decision thresholds:
 * - < 40%  → [DuplicateCheckResult.Clear]
 * - 40–79% → [DuplicateCheckResult.Flagged]
 * - ≥ 80%  → [DuplicateCheckResult.ProbableDuplicate]
 *
 * The candidate used across tests has a fixed time window of
 * `[1_000_000, 5_000_000]` (duration = 4 000 000 ms) so overlap fractions
 * can be computed by inspection.
 */
@RunWith(AndroidJUnit4::class)
class DetectDuplicateTripUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var useCase: DetectDuplicateTripUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        val driveRepository = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        useCase = DetectDuplicateTripUseCase(driveRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    /** Candidate trip: startedAt=1_000_000, endedAt=5_000_000 → duration=4_000_000ms. */
    private val candidate = DriveEntity(
        startedAt            = 1_000_000L,
        endedAt              = 5_000_000L,
        startLocation        = "A",
        endLocation          = "B",
        distanceKm           = 10f,
        driverUid            = "u1",
        tripCategory         = TripCategory.BUSINESS,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource       = TrackingSource.AUTO_TRACKED,
    )

    private suspend fun insertDrive(
        startedAt: Long,
        endedAt: Long,
        driverUid: String = "u1",
    ): Int {
        return db.driveDao().insertDrive(
            DriveEntity(
                startedAt            = startedAt,
                endedAt              = endedAt,
                startLocation        = "X",
                endLocation          = "Y",
                distanceKm           = 5f,
                driverUid            = driverUid,
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.PENDING,
                trackingSource       = TrackingSource.AUTO_TRACKED,
            )
        ).toInt()
    }

    // ── Clear: no existing trips ───────────────────────────────────────────────

    @Test
    fun invoke_noExistingTrips_returnsClear() = runTest {
        val result = useCase(candidate)
        assertEquals(DuplicateCheckResult.Clear, result)
    }

    // ── Clear: no driverUid on candidate ──────────────────────────────────────

    @Test
    fun invoke_candidateHasNullDriverUid_returnsClearImmediately() = runTest {
        insertDrive(startedAt = 1_000_000L, endedAt = 5_000_000L) // a full overlap exists

        val noUidCandidate = candidate.copy(driverUid = null)
        val result = useCase(noUidCandidate)
        assertEquals(DuplicateCheckResult.Clear, result)
    }

    @Test
    fun invoke_candidateHasBlankDriverUid_returnsClearImmediately() = runTest {
        insertDrive(startedAt = 1_000_000L, endedAt = 5_000_000L)

        val blankUidCandidate = candidate.copy(driverUid = "")
        val result = useCase(blankUidCandidate)
        assertEquals(DuplicateCheckResult.Clear, result)
    }

    // ── Clear: different driver ────────────────────────────────────────────────

    @Test
    fun invoke_existingTripBelongsToDifferentDriver_returnsClear() = runTest {
        // Fully overlapping window, but different driver — should not match.
        insertDrive(startedAt = 1_000_000L, endedAt = 5_000_000L, driverUid = "other-user")

        val result = useCase(candidate)
        assertEquals(DuplicateCheckResult.Clear, result)
    }

    // ── Clear: zero / minimal overlap ─────────────────────────────────────────

    @Test
    fun invoke_20PercentOverlap_returnsClear() = runTest {
        // Existing: startedAt=4_200_000, endedAt=9_000_000
        // overlap = min(5M, 9M) − max(1M, 4.2M) = 5M − 4.2M = 0.8M
        // overlapFrac = 0.8M / 4M = 20% < 40% → Clear
        insertDrive(startedAt = 4_200_000L, endedAt = 9_000_000L)

        val result = useCase(candidate)
        assertEquals(DuplicateCheckResult.Clear, result)
    }

    @Test
    fun invoke_nonOverlappingTrip_farInFuture_returnsClear() = runTest {
        // Completely outside the buffer window.
        insertDrive(startedAt = 50_000_000L, endedAt = 60_000_000L)

        val result = useCase(candidate)
        assertEquals(DuplicateCheckResult.Clear, result)
    }

    @Test
    fun invoke_existingTripEndsBeforeCandidateStarts_returnsClear() = runTest {
        // Existing ends 100ms before candidate starts — no overlap, even with buffer
        // if the gap is large enough. Since buffer=300s=300_000ms:
        // Existing: endedAt=700_000 which is 300_000ms before candidateStart=1_000_000
        // windowStart = 1_000_000 − 300_000 = 700_000
        // existing.endedAt (700_000) >= windowStart (700_000) → included in window
        // overlap = min(5_000_000, 700_000) − max(1_000_000, existing.startedAt)
        //         = 700_000 − max(1_000_000, ...) → negative → filtered
        insertDrive(startedAt = 100_000L, endedAt = 700_000L)

        val result = useCase(candidate)
        assertEquals(DuplicateCheckResult.Clear, result)
    }

    // ── Flagged: moderate overlap ──────────────────────────────────────────────

    @Test
    fun invoke_50PercentOverlap_returnsFlagged() = runTest {
        // Existing: startedAt=3_000_000, endedAt=7_000_000
        // overlap = min(5M, 7M) − max(1M, 3M) = 5M − 3M = 2M
        // overlapFrac = 2M / 4M = 50% → 40% ≤ x < 80% → Flagged
        val existingId = insertDrive(startedAt = 3_000_000L, endedAt = 7_000_000L)

        val result = useCase(candidate)
        assertTrue(result is DuplicateCheckResult.Flagged)
        val flagged = result as DuplicateCheckResult.Flagged
        assertEquals(existingId, flagged.existingTripId)
        assertEquals(0.5f, flagged.overlapPercent, 0.01f)
    }

    @Test
    fun invoke_exactlyAtFlaggedThreshold_returnsFlagged() = runTest {
        // 40% of 4_000_000ms = 1_600_000ms overlap
        // Existing starts at candidateEnd − 1_600_000 = 5_000_000 − 1_600_000 = 3_400_000
        // overlap = min(5M, 9M) − max(1M, 3.4M) = 5M − 3.4M = 1.6M → 40%
        insertDrive(startedAt = 3_400_000L, endedAt = 9_000_000L)

        val result = useCase(candidate)
        assertTrue(result is DuplicateCheckResult.Flagged)
    }

    // ── ProbableDuplicate: heavy overlap ──────────────────────────────────────

    @Test
    fun invoke_100PercentOverlap_returnsProbableDuplicate() = runTest {
        // Exact same window → 100% overlap.
        val existingId = insertDrive(startedAt = 1_000_000L, endedAt = 5_000_000L)

        val result = useCase(candidate)
        assertTrue(result is DuplicateCheckResult.ProbableDuplicate)
        val dup = result as DuplicateCheckResult.ProbableDuplicate
        assertEquals(existingId, dup.existingTripId)
        assertEquals(1.0f, dup.overlapPercent, 0.001f)
    }

    @Test
    fun invoke_existingContainsCandidate_returns100PercentOverlap() = runTest {
        // Existing spans wider than candidate — candidate is fully inside it.
        val existingId = insertDrive(startedAt = 500_000L, endedAt = 8_000_000L)

        val result = useCase(candidate)
        assertTrue(result is DuplicateCheckResult.ProbableDuplicate)
        val dup = result as DuplicateCheckResult.ProbableDuplicate
        assertEquals(existingId, dup.existingTripId)
    }

    @Test
    fun invoke_80PercentOverlap_returnsProbableDuplicate() = runTest {
        // 80% of 4M = 3_200_000ms overlap
        // Existing: startedAt=1_000_000, endedAt=4_200_000
        // overlap = min(5M, 4.2M) − max(1M, 1M) = 4.2M − 1M = 3.2M → 80%
        insertDrive(startedAt = 1_000_000L, endedAt = 4_200_000L)

        val result = useCase(candidate)
        assertTrue(result is DuplicateCheckResult.ProbableDuplicate)
    }

    // ── Multiple existing trips — highest overlap wins ─────────────────────────

    @Test
    fun invoke_multipleExisting_returnsResultForHighestOverlap() = runTest {
        // Trip A: 30% overlap → Clear-level (won't win)
        // Trip B: 60% overlap → Flagged-level (wins)
        insertDrive(startedAt = 4_000_000L, endedAt = 9_000_000L)       // overlap=1M → 25%
        val tripB = insertDrive(startedAt = 2_600_000L, endedAt = 7_000_000L) // overlap=2.4M → 60%

        val result = useCase(candidate)
        assertTrue(result is DuplicateCheckResult.Flagged)
        assertEquals(tripB, (result as DuplicateCheckResult.Flagged).existingTripId)
    }

    // ── excludeId: candidate excludes itself after being saved ────────────────

    @Test
    fun invoke_savedCandidateExcludesItself() = runTest {
        // Save the candidate first, then check it — it must not match itself.
        val savedId = db.driveDao().insertDrive(
            candidate.copy(
                startedAt = 1_000_000L,
                endedAt   = 5_000_000L,
            )
        ).toInt()

        val savedCandidate = candidate.copy(id = savedId)
        val result = useCase(savedCandidate)
        // No other trips exist → Clear.
        assertEquals(DuplicateCheckResult.Clear, result)
    }
}
