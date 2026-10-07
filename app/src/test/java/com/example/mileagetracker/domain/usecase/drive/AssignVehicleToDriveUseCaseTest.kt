package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssignVehicleToDriveUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var useCase: AssignVehicleToDriveUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        vehicleRepository = mockk()
        auditRepository = mockk(relaxed = true)
        useCase = AssignVehicleToDriveUseCase(driveRepository, vehicleRepository, auditRepository)
    }

    private fun testDrive(id: Int, vehicleId: Int? = null) = DriveEntity(
        id = id,
        startedAt = 1000L,
        endedAt = 2000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = 10f,
        vehicleId = vehicleId,
        driverUid = "u1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1000L
    )

    @Test
    fun `returns false if drive not found`() = runTest {
        coEvery { vehicleRepository.getVehicleById(5) } returns mockk()
        coEvery { driveRepository.getDriveById(1) } returns null
        val result = useCase(1, 5, "u1")
        assertFalse(result)
    }

    @Test
    fun `updates drive and records audit on change`() = runTest {
        val drive = testDrive(1, null)
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { vehicleRepository.getVehicleById(5) } returns mockk()
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        val result = useCase(1, 5, "u1")

        assertTrue(result)
        coVerify { driveRepository.updateDrive(match { it.vehicleId == 5 }) }
        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "u1",
                eventType = AuditEventType.VEHICLE_ASSIGNED,
                fieldName = "vehicle_id",
                oldValue = null,
                newValue = "5"
            )
        }
    }

    @Test
    fun `returns true and does nothing if vehicle already assigned`() = runTest {
        val drive = testDrive(1, 5)
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { vehicleRepository.getVehicleById(5) } returns mockk()

        val result = useCase(1, 5, "u1")

        assertTrue(result)
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws if vehicleId provided but not found`() = runTest {
        coEvery { vehicleRepository.getVehicleById(99) } returns null
        useCase(1, 99, "u1")
    }
}
