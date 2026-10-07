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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateDriveUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var useCase: UpdateDriveUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        auditRepository = mockk(relaxed = true)
        useCase = UpdateDriveUseCase(driveRepository, auditRepository)
    }

    private fun testDrive(id: Int = 1) = DriveEntity(
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
    fun `returns false if drive does not exist`() = runTest {
        coEvery { driveRepository.getDriveById(1) } returns null
        val result = useCase(testDrive(1), "u1")
        assertFalse(result)
    }

    @Test
    fun `updates drive and records audit if fields changed`() = runTest {
        val current = testDrive(1)
        val updated = current.copy(tripCategory = TripCategory.BUSINESS, notes = "Work")
        
        coEvery { driveRepository.getDriveById(1) } returns current
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        val result = useCase(updated, "u1")

        assertTrue(result)
        coVerify { driveRepository.updateDrive(updated) }
        
        // Check audit rows for category and notes
        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "u1",
                eventType = AuditEventType.CATEGORY_CHANGED,
                fieldName = "trip_category",
                oldValue = "UNCLASSIFIED",
                newValue = "BUSINESS"
            )
        }
        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "u1",
                eventType = AuditEventType.NOTE_UPDATED,
                fieldName = "notes",
                oldValue = null,
                newValue = "Work"
            )
        }
    }

    @Test
    fun `updates drive but no audit if no auditable fields changed`() = runTest {
        val current = testDrive(1).copy(updatedAt = 1000L)
        val updated = current.copy(updatedAt = 2000L) // Not an auditable field

        coEvery { driveRepository.getDriveById(1) } returns current
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        useCase(updated, "u1")

        coVerify { driveRepository.updateDrive(updated) }
        coVerify(exactly = 0) { auditRepository.recordEvent(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `records DISTANCE_UPDATED with stringified float values`() = runTest {
        val current = testDrive(1) // distanceKm = 10f
        val updated = current.copy(distanceKm = 15.5f)

        coEvery { driveRepository.getDriveById(1) } returns current
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        useCase(updated, "u1")

        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "u1",
                eventType = AuditEventType.DISTANCE_UPDATED,
                fieldName = "distance_km",
                oldValue = "10.0",
                newValue = "15.5",
            )
        }
    }

    @Test
    fun `records VEHICLE_ASSIGNED when vehicle cleared to null`() = runTest {
        val current = testDrive(1) // vehicleId = 1
        val updated = current.copy(vehicleId = null)

        coEvery { driveRepository.getDriveById(1) } returns current
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        useCase(updated, "u1")

        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "u1",
                eventType = AuditEventType.VEHICLE_ASSIGNED,
                fieldName = "vehicle_id",
                oldValue = "1",
                newValue = null,
            )
        }
    }

    @Test
    fun `records PURPOSE_UPDATED when purpose text changes`() = runTest {
        val current = testDrive(1).copy(purposeText = null)
        val updated = current.copy(purposeText = "Client visit")

        coEvery { driveRepository.getDriveById(1) } returns current
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        useCase(updated, "u1")

        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "u1",
                eventType = AuditEventType.PURPOSE_UPDATED,
                fieldName = "purpose_text",
                oldValue = null,
                newValue = "Client visit",
            )
        }
    }

    @Test
    fun `records LOCATION_UPDATED for each edited endpoint`() = runTest {
        val current = testDrive(1) // start = "A", end = "B"
        val updated = current.copy(
            startLocation = "10 Main St, Saint John, NB, Canada",
            endLocation = "20 King St, Saint John, NB, Canada",
        )

        coEvery { driveRepository.getDriveById(1) } returns current
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        useCase(updated, "u1")

        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "u1",
                eventType = AuditEventType.LOCATION_UPDATED,
                fieldName = "start_location",
                oldValue = "A",
                newValue = "10 Main St, Saint John, NB, Canada",
            )
        }
        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "u1",
                eventType = AuditEventType.LOCATION_UPDATED,
                fieldName = "end_location",
                oldValue = "B",
                newValue = "20 King St, Saint John, NB, Canada",
            )
        }
    }

    @Test
    fun `records one audit row per changed field`() = runTest {
        val current = testDrive(1)
        val updated = current.copy(
            tripCategory = TripCategory.BUSINESS, // CATEGORY_CHANGED
            distanceKm = 20f,                     // DISTANCE_UPDATED
            vehicleId = 2,                        // VEHICLE_ASSIGNED
        )

        coEvery { driveRepository.getDriveById(1) } returns current
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        useCase(updated, "u1")

        coVerify(exactly = 3) {
            auditRepository.recordEvent(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `does not update or audit a missing drive`() = runTest {
        coEvery { driveRepository.getDriveById(1) } returns null

        val result = useCase(testDrive(1).copy(tripCategory = TripCategory.BUSINESS), "u1")

        assertFalse(result)
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
        coVerify(exactly = 0) { auditRepository.recordEvent(any(), any(), any(), any(), any(), any()) }
    }
}
