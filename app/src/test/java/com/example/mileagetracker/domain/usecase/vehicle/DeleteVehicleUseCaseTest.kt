package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeleteVehicleUseCaseTest {

    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var useCase: DeleteVehicleUseCase

    @Before
    fun setUp() {
        vehicleRepository = mockk()
        useCase = DeleteVehicleUseCase(vehicleRepository)
    }

    private fun testVehicle() = VehicleEntity(
        id = 1,
        ownerUid = "u1",
        displayName = "Truck",
        fiscalYear = 2025,
        make = "Ford",
        model = "F150",
        year = 2022,
        ownershipType = VehicleOwnershipType.PERSONAL,
        isDefault = false,
        isActive = true,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `deletes vehicle when trip count is zero`() = runTest {
        val vehicle = testVehicle()
        coEvery { vehicleRepository.getTripCountForVehicle(1) } returns 0
        coEvery { vehicleRepository.deleteVehicle(vehicle) } returns Unit

        useCase(vehicle)

        coVerify { vehicleRepository.deleteVehicle(vehicle) }
    }

    @Test(expected = IllegalStateException::class)
    fun `throws when trip count is non-zero`() = runTest {
        val vehicle = testVehicle()
        coEvery { vehicleRepository.getTripCountForVehicle(1) } returns 5
        useCase(vehicle)
    }
}
