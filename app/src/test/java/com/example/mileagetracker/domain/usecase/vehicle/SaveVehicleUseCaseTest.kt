package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.domain.repository.VehicleRepository
import com.example.mileagetracker.domain.validation.ValidationResult
import com.example.mileagetracker.domain.validation.VehicleValidator
import com.example.mileagetracker.domain.validation.ValidationError
import com.example.mileagetracker.domain.validation.ErrorCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SaveVehicleUseCaseTest {

    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var vehicleValidator: VehicleValidator
    private lateinit var useCase: SaveVehicleUseCase

    @Before
    fun setUp() {
        vehicleRepository = mockk()
        vehicleValidator = mockk()
        useCase = SaveVehicleUseCase(vehicleRepository, vehicleValidator)
    }

    private fun testVehicle(id: Int = 0) = VehicleEntity(
        id = id,
        ownerUid = "u1",
        displayName = "Car",
        fiscalYear = 2025,
        ownershipType = VehicleOwnershipType.PERSONAL
    )

    @Test
    fun `inserts new vehicle and returns id`() = runTest {
        val vehicle = testVehicle(id = 0)
        every { vehicleValidator.validateAll(vehicle) } returns ValidationResult.Valid
        coEvery { vehicleRepository.saveVehicle(vehicle) } returns 5

        val result = useCase(vehicle)

        assertEquals(5, result)
    }

    @Test
    fun `updates existing vehicle and returns same id`() = runTest {
        val vehicle = testVehicle(id = 10)
        every { vehicleValidator.validateAll(vehicle) } returns ValidationResult.Valid
        coEvery { vehicleRepository.updateVehicle(vehicle) } returns Unit

        val result = useCase(vehicle)

        assertEquals(10, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `throws when validation fails`() = runTest {
        val vehicle = testVehicle()
        every { vehicleValidator.validateAll(vehicle) } returns ValidationResult.Invalid(
            listOf(ValidationError("displayName", ErrorCode.BLANK_VEHICLE_NAME, "Bad name"))
        )
        
        useCase(vehicle)
    }
}
