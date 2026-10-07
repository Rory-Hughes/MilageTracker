package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CreateDriveUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase
    private lateinit var useCase: CreateDriveUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        auditRepository = mockk(relaxed = true)
        resolveDriveLocationsUseCase = mockk()
        coEvery { resolveDriveLocationsUseCase(any()) } answers { firstArg() }
        useCase = CreateDriveUseCase(
            driveRepository = driveRepository,
            auditRepository = auditRepository,
            resolveDriveLocationsUseCase = resolveDriveLocationsUseCase,
        )
    }

    private fun testDrive() = DriveEntity(
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
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 0L,
    )

    @Test
    fun `saves drive and records audit`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.saveDrive(drive) } returns 10

        val newId = useCase(drive, "u1")

        assertEquals(10, newId)
        coVerify { auditRepository.recordEvent(10, "u1", AuditEventType.DRIVE_CREATED) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on blank driverUid`() = runTest {
        useCase(testDrive().copy(driverUid = ""), "u1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on invalid startedAt`() = runTest {
        useCase(testDrive().copy(startedAt = 0L), "u1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on negative distance`() = runTest {
        useCase(testDrive().copy(distanceKm = -0.1f), "u1")
    }

    @Test
    fun `allows zero distance as a boundary value`() = runTest {
        val drive = testDrive().copy(distanceKm = 0f)
        coEvery { driveRepository.saveDrive(drive) } returns 5

        val newId = useCase(drive, "u1")

        assertEquals(5, newId)
        coVerify { auditRepository.recordEvent(5, "u1", AuditEventType.DRIVE_CREATED) }
    }

    @Test
    fun `does not resolve persist or audit when validation fails`() = runTest {
        try {
            useCase(testDrive().copy(driverUid = "  "), "u1")
            fail("Expected IllegalArgumentException for blank driverUid")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        coVerify(exactly = 0) { resolveDriveLocationsUseCase(any()) }
        coVerify(exactly = 0) { driveRepository.saveDrive(any()) }
        coVerify(exactly = 0) { auditRepository.recordEvent(any(), any(), any()) }
    }

    @Test
    fun `persists resolved locations when resolver returns a richer drive`() = runTest {
        val drive = testDrive().copy(
            startLocation = "44.6488, -63.5752",
            endLocation = "44.6713, -63.6103",
        )
        val resolvedDrive = drive.copy(
            startLocation = "123 Main St, Halifax, NS B3H 1A1",
            endLocation = "456 Spring Garden Rd, Halifax, NS B3J 1E9",
        )
        coEvery { resolveDriveLocationsUseCase(drive) } returns resolvedDrive
        coEvery { driveRepository.saveDrive(resolvedDrive) } returns 11

        val newId = useCase(drive, "u1")

        assertEquals(11, newId)
        coVerify { driveRepository.saveDrive(resolvedDrive) }
        coVerify { auditRepository.recordEvent(11, "u1", AuditEventType.DRIVE_CREATED) }
    }
}
