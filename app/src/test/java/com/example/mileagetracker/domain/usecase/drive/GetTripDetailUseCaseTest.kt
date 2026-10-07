package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.TrackingRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
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

class GetTripDetailUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var trackingRepository: TrackingRepository
    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var useCase: GetTripDetailUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        trackingRepository = mockk()
        vehicleRepository = mockk()
        useCase = GetTripDetailUseCase(driveRepository, trackingRepository, vehicleRepository)
    }

    private fun testDrive(id: Int = 1, vehicleId: Int? = null) = DriveEntity(
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
    fun `returns NotFound if drive does not exist`() = runTest {
        coEvery { driveRepository.getDriveById(88) } returns null
        
        val result = useCase(88).first()
        
        assertEquals(TripDetailResult.NotFound, result)
    }

    @Test
    fun `returns Success with drive, route and vehicle`() = runTest {
        val drive = testDrive(id = 1, vehicleId = 5)
        val vehicle = VehicleEntity(
            id = 5,
            ownerUid = "u1",
            displayName = "Truck",
            fiscalYear = 2025,
            make = "Ford",
            model = "F150",
            year = 2022,
            ownershipType = VehicleOwnershipType.PERSONAL,
            isDefault = true,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        
        coEvery { driveRepository.getDriveById(1) } returns drive
        every { trackingRepository.getRoutePoints(1L) } returns flowOf(emptyList())
        coEvery { vehicleRepository.getVehicleById(5) } returns vehicle

        val result = useCase(1).first()

        assertTrue(result is TripDetailResult.Success)
        val success = result as TripDetailResult.Success
        assertEquals(drive, success.drive)
        assertEquals(vehicle, success.vehicle)
        assertTrue(success.route.isEmpty())
    }
}
