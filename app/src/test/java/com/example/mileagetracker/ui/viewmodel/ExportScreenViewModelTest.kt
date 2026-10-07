package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.usecase.vehicle.GetActiveVehiclesUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportScreenViewModelTest {

    private lateinit var getActiveVehiclesUseCase: GetActiveVehiclesUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getActiveVehiclesUseCase = mockk()
        currentUserProvider = mockk()
        every { currentUserProvider.requireUid() } returns "u1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `active vehicles follow selected year and last category cannot be removed`() = runTest {
        every { getActiveVehiclesUseCase(any(), 2026) } returns flowOf(
            listOf(VehicleEntity(id = 1, ownerUid = "u1", displayName = "Sedan", fiscalYear = 2026))
        )
        every { getActiveVehiclesUseCase(any(), 2024) } returns flowOf(
            listOf(VehicleEntity(id = 2, ownerUid = "u1", displayName = "Archive", fiscalYear = 2024))
        )

        val viewModel = ExportScreenViewModel(getActiveVehiclesUseCase, currentUserProvider)
        advanceUntilIdle()

        viewModel.selectVehicle(1)
        viewModel.toggleCategory(TripCategory.BUSINESS)
        assertEquals(setOf(TripCategory.BUSINESS), viewModel.selectedCategories.value)

        viewModel.selectYear(2024)
        advanceUntilIdle()

        assertEquals(listOf(2), viewModel.activeVehicles.value.map { it.id })
        assertNull(viewModel.selectedVehicleId.value)
    }
}
