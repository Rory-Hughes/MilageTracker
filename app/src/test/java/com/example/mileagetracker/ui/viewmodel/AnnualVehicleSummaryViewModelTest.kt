package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.domain.model.VehicleAnnualSummary
import com.example.mileagetracker.domain.usecase.rate.GetAnnualVehicleSummaryUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnnualVehicleSummaryViewModelTest {

    private lateinit var viewModel: AnnualVehicleSummaryViewModel
    private lateinit var getAnnualVehicleSummary: GetAnnualVehicleSummaryUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getAnnualVehicleSummary = mockk()
        viewModel = AnnualVehicleSummaryViewModel(getAnnualVehicleSummary)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() = runTest {
        assertEquals(AnnualVehicleSummaryUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `selectVehicle triggers load and Success state`() = runTest {
        val summary = mockk<VehicleAnnualSummary>()
        coEvery { getAnnualVehicleSummary(1, any()) } returns summary

        viewModel.selectVehicle(1)
        
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AnnualVehicleSummaryUiState.Success)
        assertEquals(summary, (viewModel.uiState.value as AnnualVehicleSummaryUiState.Success).summary)
    }

    @Test
    fun `selectYear reloads summary`() = runTest {
        val summary = mockk<VehicleAnnualSummary>()
        coEvery { getAnnualVehicleSummary(1, 2024) } returns summary

        viewModel.selectVehicle(1)
        viewModel.selectYear(2024)
        
        advanceUntilIdle()

        coVerify { getAnnualVehicleSummary(1, 2024) }
    }

    @Test
    fun `load error updates uiState to Error`() = runTest {
        coEvery { getAnnualVehicleSummary(any(), any()) } throws IllegalStateException("Database error")

        viewModel.selectVehicle(1)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AnnualVehicleSummaryUiState.Error)
        assertEquals("Database error", (viewModel.uiState.value as AnnualVehicleSummaryUiState.Error).message)
    }
}
