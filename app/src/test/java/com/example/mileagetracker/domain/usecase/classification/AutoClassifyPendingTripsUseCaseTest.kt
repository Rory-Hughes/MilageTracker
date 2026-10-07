package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.repository.DriveRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AutoClassifyPendingTripsUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var autoClassifyTripUseCase: AutoClassifyTripUseCase
    private lateinit var useCase: AutoClassifyPendingTripsUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        autoClassifyTripUseCase = mockk()
        useCase = AutoClassifyPendingTripsUseCase(driveRepository, autoClassifyTripUseCase)
    }

    private fun testDrive(id: Int) = DriveEntity(
        id = id,
        startedAt = 1000L,
        endedAt = 2000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = 10f,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1000L
    )

    @Test
    fun `processes all pending trips and returns summary`() = runTest {
        val drives = listOf(testDrive(1), testDrive(2), testDrive(3))
        every { driveRepository.getUnclassifiedDrives("u1") } returns flowOf(drives)
        
        coEvery { autoClassifyTripUseCase(1, any(), any()) } returns
            AutoClassifyResult.Classified(TripCategory.BUSINESS, 0.9f, "Rule1")
        coEvery { autoClassifyTripUseCase(2, any(), any()) } returns AutoClassifyResult.FlaggedForReview("Rule2")
        coEvery { autoClassifyTripUseCase(3, any(), any()) } returns AutoClassifyResult.NoRuleMatched

        val result = useCase("u1")

        assertEquals(3, result.total)
        assertEquals(1, result.classified)
        assertEquals(1, result.flaggedReview)
        assertEquals(1, result.noRuleMatched)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `reports progress via callback`() = runTest {
        val drives = listOf(testDrive(1), testDrive(2))
        every { driveRepository.getUnclassifiedDrives("u1") } returns flowOf(drives)
        coEvery { autoClassifyTripUseCase(any(), any(), any()) } returns AutoClassifyResult.NoRuleMatched

        val progress = mutableListOf<Pair<Int, Int>>()
        useCase("u1") { p, t -> progress.add(p to t) }

        assertEquals(2, progress.size)
        assertEquals(1 to 2, progress[0])
        assertEquals(2 to 2, progress[1])
    }

    @Test
    fun `continues processing when a trip throws`() = runTest {
        val drives = listOf(testDrive(1), testDrive(2))
        every { driveRepository.getUnclassifiedDrives("u1") } returns flowOf(drives)
        
        coEvery { autoClassifyTripUseCase(1, any(), any()) } throws RuntimeException("DB Error")
        coEvery { autoClassifyTripUseCase(2, any(), any()) } returns AutoClassifyResult.NoRuleMatched

        val result = useCase("u1")

        assertEquals(2, result.total)
        assertEquals(1, result.errors)
        assertEquals(1, result.noRuleMatched)
    }
}
