package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClassifyTripUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var updateDriveUseCase: UpdateDriveUseCase
    private lateinit var calculateReimbursementUseCase: CalculateReimbursementUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var useCase: ClassifyTripUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        updateDriveUseCase = mockk()
        calculateReimbursementUseCase = mockk()
        currentUserProvider = mockk()
        useCase = ClassifyTripUseCase(
            driveRepository,
            updateDriveUseCase,
            calculateReimbursementUseCase,
            currentUserProvider
        )
    }

    private fun testDrive() = DriveEntity(
        id = 1,
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
    fun `returns false if drive not found`() = runTest {
        coEvery { driveRepository.getDriveById(1) } returns null
        val result = useCase(1, TripCategory.BUSINESS)
        assertFalse(result)
    }

    @Test
    fun `updates drive with business category and reimbursement snapshot`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { calculateReimbursementUseCase(any(), any()) } returns ReimbursementResult(
            taxYear = 1970,
            newDistanceKm = 10f,
            ytdKmBefore = 0f,
            firstTierKm = 10,
            secondTierKm = 0,
            firstTierLimitKm = 5000,
            firstTierRateCents = 70,
            additionalRateCents = 64,
            amountCents = 700
        )
        coEvery { updateDriveUseCase(any(), "u1") } returns true

        val result = useCase(1, TripCategory.BUSINESS, actorUid = "u1")

        assertTrue(result)
        coEvery { updateDriveUseCase(match {
            it.tripCategory == TripCategory.BUSINESS &&
            it.classificationStatus == ClassificationStatus.USER_CONFIRMED &&
            it.reimbursementAmountSnapshot == 7.0f &&
            it.appliedRateSnapshot == 0.70f
        }, "u1") }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws if custom category has no purpose text`() = runTest {
        useCase(1, TripCategory.CUSTOM, purposeText = null)
    }
}
