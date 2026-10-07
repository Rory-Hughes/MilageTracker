package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SetDefaultVehicleUseCaseTest {

    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var useCase: SetDefaultVehicleUseCase

    @Before
    fun setUp() {
        vehicleRepository = mockk()
        useCase = SetDefaultVehicleUseCase(vehicleRepository)
    }

    @Test
    fun `delegates to repository`() = runTest {
        coEvery { vehicleRepository.setDefaultVehicle(any(), any(), any()) } returns Unit
        
        useCase(1, "u1", 2025)
        
        coVerify { vehicleRepository.setDefaultVehicle(1, "u1", 2025) }
    }
}
