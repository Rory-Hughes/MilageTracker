package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CreateManualTripUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var calculateReimbursementUseCase: CalculateReimbursementUseCase
    private lateinit var useCase: CreateManualTripUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        auditRepository = mockk(relaxed = true)
        calculateReimbursementUseCase = mockk()
        useCase = CreateManualTripUseCase(
            driveRepository,
            auditRepository,
            calculateReimbursementUseCase
        )
    }

    private fun testDraft() = DriveEntity(
        id = 0,
        startedAt = 1000L,
        endedAt = 2000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = 10f,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED, // Should be overridden
        createdAt = 0L
    )

    @Test
    fun `overrides source and confidence on save`() = runTest {
        val draft = testDraft()
        coEvery { driveRepository.saveDrive(any()) } returns 5

        useCase(draft, "u1")

        coVerify {
            driveRepository.saveDrive(match {
                it.trackingSource == TrackingSource.MANUAL_ENTRY &&
                it.classificationConfidence == 1.0f
            })
        }
    }

    @Test
    fun `sets status to USER_CONFIRMED if category is supplied`() = runTest {
        val draft = testDraft().copy(tripCategory = TripCategory.BUSINESS)
        coEvery { driveRepository.saveDrive(any()) } returns 5
        every { driveRepository.getYearToDateBusinessDistance(any(), any(), any()) } returns flowOf(0f)
        coEvery { calculateReimbursementUseCase(any(), any(), any()) } returns mockk(relaxed = true)

        useCase(draft, "u1")

        coVerify {
            driveRepository.saveDrive(match {
                it.classificationStatus == ClassificationStatus.USER_CONFIRMED
            })
        }
    }

    @Test
    fun `records MANUAL_ENTRY audit row`() = runTest {
        val draft = testDraft()
        coEvery { driveRepository.saveDrive(any()) } returns 5

        useCase(draft, "u1")

        coVerify {
            auditRepository.recordEvent(
                tripId = 5,
                actorUid = "u1",
                eventType = AuditEventType.MANUAL_ENTRY,
                newValue = "trackingSource=MANUAL_ENTRY,confidence=1.0"
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on blank startLocation`() = runTest {
        useCase(testDraft().copy(startLocation = ""), "u1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on invalid distance`() = runTest {
        useCase(testDraft().copy(distanceKm = 0f), "u1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on end time before start time`() = runTest {
        useCase(testDraft().copy(startedAt = 2000L, endedAt = 1000L), "u1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on blank driverUid`() = runTest {
        useCase(testDraft().copy(driverUid = ""), "u1")
    }
}
