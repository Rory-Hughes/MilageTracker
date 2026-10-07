package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.ReportPeriod
import com.example.mileagetracker.domain.usecase.GetReportReadinessUseCase
import com.example.mileagetracker.domain.usecase.drive.GetTripSummaryUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class ReportsViewModelTest {

    private lateinit var viewModel: ReportsViewModel
    private lateinit var getTripSummaryUseCase: GetTripSummaryUseCase
    private lateinit var getReportReadinessUseCase: GetReportReadinessUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getTripSummaryUseCase = mockk()
        getReportReadinessUseCase = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns "u1"
        coEvery { getTripSummaryUseCase(any(), any(), any(), any()) } returns emptyList()
        coEvery { getTripSummaryUseCase.getDrivesForYear(any(), any(), any()) } returns emptyList()
        every { getReportReadinessUseCase(any()) } returns emptyList()

        viewModel = ReportsViewModel(getTripSummaryUseCase, getReportReadinessUseCase, currentUserProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Success after loading`() = runTest {
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ReportsUiState.Success)
        collector.cancel()
    }

    @Test
    fun `selectPeriod updates state and triggers reload`() = runTest {
        viewModel.selectPeriod(ReportPeriod.ANNUAL)
        assertEquals(ReportPeriod.ANNUAL, viewModel.selectedPeriod.value)

        advanceUntilIdle()
        coEvery { getTripSummaryUseCase(any(), any(), ReportPeriod.ANNUAL, any()) }
    }
}
