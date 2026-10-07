package com.example.mileagetracker.domain.usecase.tracking

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.DuplicateCheckResult
import com.example.mileagetracker.domain.repository.DriveRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DetectDuplicateTripUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var useCase: DetectDuplicateTripUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        useCase = DetectDuplicateTripUseCase(driveRepository)
    }

    private fun testDrive(id: Int, start: Long, end: Long) = DriveEntity(
        id = id,
        startedAt = start,
        endedAt = end,
        startLocation = "A",
        endLocation = "B",
        distanceKm = 10f,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = start
    )

    @Test
    fun `returns Clear when no trips in window`() = runTest {
        val candidate = testDrive(0, 1000, 2000)
        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns emptyList()

        val result = useCase(candidate)

        assertEquals(DuplicateCheckResult.Clear, result)
    }

    @Test
    fun `returns ProbableDuplicate when high overlap found`() = runTest {
        val candidate = testDrive(0, 1000, 2000) // Duration 1000
        val existing = testDrive(1, 1100, 2100)  // Overlap from 1100 to 2000 = 900ms (90%)
        
        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns listOf(existing)

        val result = useCase(candidate)

        assertTrue(result is DuplicateCheckResult.ProbableDuplicate)
        assertEquals(1, (result as DuplicateCheckResult.ProbableDuplicate).existingTripId)
        assertEquals(0.9f, result.overlapPercent, 0.01f)
    }

    @Test
    fun `returns Flagged when partial overlap found`() = runTest {
        val candidate = testDrive(0, 1000, 2000) // Duration 1000
        val existing = testDrive(1, 1500, 2500)  // Overlap from 1500 to 2000 = 500ms (50%)
        
        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns listOf(existing)

        val result = useCase(candidate)

        // Threshold for FLAGGED is 0.4 usually, PROBABLE is higher (e.g. 0.8)
        assertTrue(result is DuplicateCheckResult.Flagged)
        assertEquals(1, (result as DuplicateCheckResult.Flagged).existingTripId)
        assertEquals(0.5f, result.overlapPercent, 0.01f)
    }

    @Test
    fun `returns Clear when low overlap found`() = runTest {
        val candidate = testDrive(0, 1000, 2000) // Duration 1000
        val existing = testDrive(1, 1900, 3000)  // Overlap from 1900 to 2000 = 100ms (10%)
        
        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns listOf(existing)

        val result = useCase(candidate)

        assertEquals(DuplicateCheckResult.Clear, result)
    }

    @Test
    fun `returns Clear and skips query when candidate has blank driver uid`() = runTest {
        val candidate = testDrive(0, 1000, 2000).copy(driverUid = "   ")

        val result = useCase(candidate)

        assertEquals(DuplicateCheckResult.Clear, result)
        coVerify(exactly = 0) { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) }
    }

    @Test
    fun `queries repository with buffered time window and candidate id exclusion`() = runTest {
        val candidate = testDrive(42, 10_000, 20_000)
        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns emptyList()

        useCase(candidate)

        coVerify {
            driveRepository.getDrivesInTimeWindow(
                driverUid = "u1",
                windowStartMs = 10_000 - DetectDuplicateTripUseCase.TIME_WINDOW_BUFFER_MS,
                windowEndMs = 20_000 + DetectDuplicateTripUseCase.TIME_WINDOW_BUFFER_MS,
                excludeId = 42,
            )
        }
    }

    @Test
    fun `returns highest overlap trip when multiple overlaps exist`() = runTest {
        val candidate = testDrive(0, 1_000, 2_000) // duration = 1000
        val lowerOverlap = testDrive(7, 1_600, 2_600) // overlap 400ms => 40%
        val higherOverlap = testDrive(9, 1_200, 2_100) // overlap 800ms => 80%

        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns
            listOf(lowerOverlap, higherOverlap)

        val result = useCase(candidate)

        assertTrue(result is DuplicateCheckResult.ProbableDuplicate)
        assertEquals(9, (result as DuplicateCheckResult.ProbableDuplicate).existingTripId)
        assertEquals(0.8f, result.overlapPercent, 0.01f)
    }

    @Test
    fun `returns Clear when nearby trips have no temporal overlap`() = runTest {
        val candidate = testDrive(0, 1_000, 2_000)
        val existingBefore = testDrive(5, 100, 900)
        val existingAfter = testDrive(6, 2_100, 3_000)

        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns
            listOf(existingBefore, existingAfter)

        val result = useCase(candidate)

        assertEquals(DuplicateCheckResult.Clear, result)
    }

    @Test
    fun `returns Flagged when best overlap is exactly flagged threshold`() = runTest {
        val candidate = testDrive(0, 1_000, 2_000) // duration=1000
        val existing = testDrive(3, 1_600, 2_600) // overlap=400 => 40%

        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns listOf(existing)

        val result = useCase(candidate)

        assertTrue(result is DuplicateCheckResult.Flagged)
        assertEquals(3, (result as DuplicateCheckResult.Flagged).existingTripId)
        assertEquals(DuplicateCheckResult.FLAGGED_THRESHOLD, result.overlapPercent, 0.0001f)
    }

    @Test
    fun `returns ProbableDuplicate when best overlap is exactly probable threshold`() = runTest {
        val candidate = testDrive(0, 1_000, 2_000) // duration=1000
        val existing = testDrive(4, 1_200, 2_200) // overlap=800 => 80%

        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns listOf(existing)

        val result = useCase(candidate)

        assertTrue(result is DuplicateCheckResult.ProbableDuplicate)
        assertEquals(4, (result as DuplicateCheckResult.ProbableDuplicate).existingTripId)
        assertEquals(DuplicateCheckResult.PROBABLE_DUPLICATE_THRESHOLD, result.overlapPercent, 0.0001f)
    }

    @Test
    fun `returns Clear when all overlaps are below flagged threshold and picks none`() = runTest {
        val candidate = testDrive(0, 1_000, 2_000) // duration=1000
        val overlapA = testDrive(11, 1_650, 2_200) // 350ms
        val overlapB = testDrive(12, 900, 1_200) // 200ms

        coEvery { driveRepository.getDrivesInTimeWindow(any(), any(), any(), any()) } returns
            listOf(overlapA, overlapB)

        val result = useCase(candidate)

        assertEquals(DuplicateCheckResult.Clear, result)
    }
}
