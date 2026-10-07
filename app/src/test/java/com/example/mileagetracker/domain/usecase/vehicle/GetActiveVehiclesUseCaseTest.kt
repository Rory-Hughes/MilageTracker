package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.repository.VehicleRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetActiveVehiclesUseCaseTest {

    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var useCase: GetActiveVehiclesUseCase

    @Before
    fun setUp() {
        vehicleRepository = mockk()
        useCase = GetActiveVehiclesUseCase(vehicleRepository)
    }

    @Test
    fun `delegates to repository`() {
        val expected = flowOf(emptyList<VehicleEntity>())
        every { vehicleRepository.getActiveVehicles("u1", 2025) } returns expected

        val result = useCase("u1", 2025)

        assertEquals(expected, result)
    }
}
