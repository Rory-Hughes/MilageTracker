package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CreateVehicleUseCaseTest {

    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var useCase: CreateVehicleUseCase

    @Before
    fun setUp() {
        vehicleRepository = mockk()
        useCase = CreateVehicleUseCase(vehicleRepository)
    }

    private fun testVehicle() = VehicleEntity(
        id = 0,
        ownerUid = "u1",
        displayName = "My Car",
        fiscalYear = 2025,
        make = "Honda",
        model = "Civic",
        year = 2021,
        ownershipType = VehicleOwnershipType.PERSONAL,
        isDefault = false,
        isActive = true,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun `saves vehicle and returns generated id`() = runTest {
        val vehicle = testVehicle()
        coEvery { vehicleRepository.saveVehicle(vehicle) } returns 7

        val result = useCase(vehicle)

        assertEquals(7, result)
        coVerify(exactly = 1) { vehicleRepository.saveVehicle(vehicle) }
    }

    @Test
    fun `promotes to default when setAsDefault is true`() = runTest {
        val vehicle = testVehicle()
        coEvery { vehicleRepository.saveVehicle(vehicle) } returns 10
        coEvery { vehicleRepository.setDefaultVehicle(10, "u1", 2025) } returns Unit

        val result = useCase(vehicle, setAsDefault = true)

        assertEquals(10, result)
        coVerify { vehicleRepository.setDefaultVehicle(10, "u1", 2025) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on blank displayName`() = runTest {
        useCase(testVehicle().copy(displayName = "  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on invalid fiscalYear`() = runTest {
        useCase(testVehicle().copy(fiscalYear = 1999))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws on negative opening odometer`() = runTest {
        useCase(testVehicle().copy(openingOdometerKm = -1f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when closing odometer is less than opening`() = runTest {
        useCase(testVehicle().copy(openingOdometerKm = 100f, closingOdometerKm = 50f))
    }
}
