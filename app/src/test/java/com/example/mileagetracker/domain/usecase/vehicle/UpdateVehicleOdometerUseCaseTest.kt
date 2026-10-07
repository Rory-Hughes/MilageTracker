package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateVehicleOdometerUseCaseTest {

    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var useCase: UpdateVehicleOdometerUseCase

    @Before
    fun setUp() {
        vehicleRepository = mockk()
        useCase = UpdateVehicleOdometerUseCase(vehicleRepository)
    }

    private fun testVehicle(opening: Float? = null, closing: Float? = null) = VehicleEntity(
        id = 1,
        ownerUid = "u1",
        displayName = "Car",
        fiscalYear = 2025,
        ownershipType = VehicleOwnershipType.PERSONAL,
        openingOdometerKm = opening,
        closingOdometerKm = closing
    )

    @Test
    fun `returns true and does nothing if both km are null`() = runTest {
        val result = useCase(1, openingKm = null, closingKm = null)
        assertTrue(result)
        coVerify(exactly = 0) { vehicleRepository.getVehicleById(any()) }
    }

    @Test
    fun `returns false if vehicle not found`() = runTest {
        coEvery { vehicleRepository.getVehicleById(1) } returns null
        val result = useCase(1, openingKm = 100f)
        assertFalse(result)
    }

    @Test
    fun `updates opening and closing km`() = runTest {
        val vehicle = testVehicle(opening = 50f, closing = 100f)
        coEvery { vehicleRepository.getVehicleById(1) } returns vehicle
        coEvery { vehicleRepository.updateVehicle(any()) } returns Unit

        val result = useCase(1, openingKm = 60f, closingKm = 120f)

        assertTrue(result)
        coVerify { vehicleRepository.updateVehicle(match {
            it.openingOdometerKm == 60f && it.closingOdometerKm == 120f
        }) }
    }

    @Test
    fun `validates new closing against existing opening`() = runTest {
        val vehicle = testVehicle(opening = 100f)
        coEvery { vehicleRepository.getVehicleById(1) } returns vehicle
        coEvery { vehicleRepository.updateVehicle(any()) } returns Unit

        val result = useCase(1, closingKm = 150f)
        assertTrue(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when new closing is less than existing opening`() = runTest {
        val vehicle = testVehicle(opening = 100f)
        coEvery { vehicleRepository.getVehicleById(1) } returns vehicle
        
        useCase(1, closingKm = 50f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when new opening is negative`() = runTest {
        val vehicle = testVehicle()
        coEvery { vehicleRepository.getVehicleById(1) } returns vehicle
        useCase(1, openingKm = -1f)
    }
}
