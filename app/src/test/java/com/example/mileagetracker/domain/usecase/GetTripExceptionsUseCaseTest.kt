package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.ExceptionType
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetTripExceptionsUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var useCase: GetTripExceptionsUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        auditRepository = mockk()
        useCase = GetTripExceptionsUseCase(driveRepository, auditRepository)
    }

    private fun testDrive(
        id: Int,
        category: TripCategory = TripCategory.BUSINESS,
        status: ClassificationStatus = ClassificationStatus.PENDING,
        distance: Float = 10f,
        vehicleId: Int? = 1,
    ) = DriveEntity(
        id = id,
        startedAt = 1_000L,
        endedAt = 2_000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = distance,
        vehicleId = vehicleId,
        driverUid = "u1",
        tripCategory = category,
        classificationStatus = status,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1_000L,
    )

    private fun testAudit(tripId: Int, eventType: AuditEventType) = TripAuditEntity(
        tripId = tripId,
        actorUid = "SYSTEM",
        eventType = eventType,
        changedAt = 1_500L,
    )

    @Test
    fun `detects duplicate flagged when status is needs review and audit matches`() = runTest {
        val drive = testDrive(1, status = ClassificationStatus.NEEDS_REVIEW)
        every { driveRepository.getAllDrives("u1") } returns flowOf(listOf(drive))
        coEvery { auditRepository.getAuditTrailForTripOnce(1) } returns listOf(
            testAudit(1, AuditEventType.DUPLICATE_FLAGGED),
        )

        val exceptions = useCase("u1").first()

        assertEquals(1, exceptions.size)
        assertEquals(ExceptionType.DUPLICATE_FLAGGED, exceptions[0].exceptionType)
    }

    @Test
    fun `detects incomplete data for unclassified report trip`() = runTest {
        val drive = testDrive(
            1,
            category = TripCategory.UNCLASSIFIED,
            status = ClassificationStatus.PENDING,
            distance = 0f,
            vehicleId = null,
        )
        every { driveRepository.getAllDrives("u1") } returns flowOf(listOf(drive))
        coEvery { auditRepository.getAuditTrailForTripOnce(1) } returns emptyList()

        val exceptions = useCase("u1").first()

        assertEquals(1, exceptions.size)
        assertEquals(ExceptionType.INCOMPLETE_DATA, exceptions[0].exceptionType)
    }

    @Test
    fun `confirmed personal trip with blank purpose does not emit incomplete data`() = runTest {
        val drive = testDrive(
            1,
            category = TripCategory.PERSONAL,
            status = ClassificationStatus.USER_CONFIRMED,
        ).copy(purposeText = null)
        every { driveRepository.getAllDrives("u1") } returns flowOf(listOf(drive))
        coEvery { auditRepository.getAuditTrailForTripOnce(1) } returns emptyList()

        val exceptions = useCase("u1").first()

        assertTrue(exceptions.isEmpty())
    }

    @Test
    fun `detects impossible route when endedAt before startedAt`() = runTest {
        val drive = testDrive(1).copy(startedAt = 2_000L, endedAt = 1_000L)
        every { driveRepository.getAllDrives("u1") } returns flowOf(listOf(drive))
        coEvery { auditRepository.getAuditTrailForTripOnce(1) } returns emptyList()

        val exceptions = useCase("u1").first()

        assertEquals(1, exceptions.size)
        assertEquals(ExceptionType.IMPOSSIBLE_ROUTE, exceptions[0].exceptionType)
    }

    @Test
    fun `detects manual override when status is user corrected and no repair audit`() = runTest {
        val drive = testDrive(1, status = ClassificationStatus.USER_CORRECTED)
        every { driveRepository.getAllDrives("u1") } returns flowOf(listOf(drive))
        coEvery { auditRepository.getAuditTrailForTripOnce(1) } returns emptyList()

        val exceptions = useCase("u1").first()

        assertEquals(1, exceptions.size)
        assertEquals(ExceptionType.MANUAL_OVERRIDE, exceptions[0].exceptionType)
    }

    @Test
    fun `ignores user corrected when trip repaired audit exists`() = runTest {
        val drive = testDrive(1, status = ClassificationStatus.USER_CORRECTED)
        every { driveRepository.getAllDrives("u1") } returns flowOf(listOf(drive))
        coEvery { auditRepository.getAuditTrailForTripOnce(1) } returns listOf(
            testAudit(1, AuditEventType.TRIP_REPAIRED),
        )

        val exceptions = useCase("u1").first()

        assertTrue(exceptions.isEmpty())
    }
}
