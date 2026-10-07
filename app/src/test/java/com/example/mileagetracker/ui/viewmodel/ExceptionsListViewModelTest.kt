package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.ReportReadiness
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.domain.usecase.GetReportReadinessUseCase
import com.example.mileagetracker.domain.usecase.GetTripExceptionsUseCase
import com.example.mileagetracker.domain.usecase.drive.GetTripSummaryUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExceptionsListViewModelTest {

    private lateinit var viewModel: ExceptionsListViewModel
    private lateinit var getTripSummaryUseCase: GetTripSummaryUseCase
    private lateinit var getReportReadinessUseCase: GetReportReadinessUseCase
    private lateinit var getTripExceptionsUseCase: GetTripExceptionsUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getTripSummaryUseCase = mockk()
        getReportReadinessUseCase = mockk()
        getTripExceptionsUseCase = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns "u1"
        every { getTripExceptionsUseCase("u1") } returns flowOf(emptyList())
        coEvery { getTripSummaryUseCase.getDrivesForYear(any(), any(), any()) } returns emptyList()
        every { getReportReadinessUseCase(any()) } returns emptyList()

        viewModel = ExceptionsListViewModel(
            getTripSummaryUseCase,
            getReportReadinessUseCase,
            getTripExceptionsUseCase,
            currentUserProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial states are empty`() = runTest {
        val collector = backgroundScope.launch { viewModel.activeExceptionCount.collect {} }
        advanceUntilIdle()
        assertEquals(emptyList<ReportReadiness>(), viewModel.incompleteTrips.value)
        assertEquals(emptyList<TripException>(), viewModel.exceptions.value)
        assertEquals(0, viewModel.activeExceptionCount.value)
        collector.cancel()
    }

    @Test
    fun `activeExceptionCount combines both sources and deduplicates`() = runTest {
        val readiness = listOf(
            mockk<ReportReadiness> { every { isReady } returns false; every { tripId } returns 1 },
            mockk<ReportReadiness> { every { isReady } returns false; every { tripId } returns 2 }
        )
        val exList = listOf(
            mockk<TripException> { every { isActive } returns true; every { tripId } returns 2 },
            mockk<TripException> { every { isActive } returns true; every { tripId } returns 3 }
        )

        coEvery { getTripSummaryUseCase.getDrivesForYear(any(), any(), any()) } returns emptyList()
        every { getReportReadinessUseCase(any()) } returns readiness
        every { getTripExceptionsUseCase("u1") } returns flowOf(exList)

        // Re-init to pick up mocked behavior in init flow
        viewModel = ExceptionsListViewModel(
            getTripSummaryUseCase,
            getReportReadinessUseCase,
            getTripExceptionsUseCase,
            currentUserProvider
        )

        val collector = backgroundScope.launch { viewModel.activeExceptionCount.collect {} }
        advanceUntilIdle()

        // tripIds involved: 1, 2 from readiness; 2, 3 from exceptions. Union: {1, 2, 3} -> count 3.
        assertEquals(3, viewModel.activeExceptionCount.value)
        collector.cancel()
    }

    @Test
    fun `incompleteTrips are filtered from readiness`() = runTest {
        val readiness = listOf(
            mockk<ReportReadiness> { every { isReady } returns false; every { tripId } returns 1 },
            mockk<ReportReadiness> { every { isReady } returns true; every { tripId } returns 2 }
        )
        every { getReportReadinessUseCase(any()) } returns readiness

        viewModel = ExceptionsListViewModel(
            getTripSummaryUseCase,
            getReportReadinessUseCase,
            getTripExceptionsUseCase,
            currentUserProvider
        )

        // incompleteTrips uses SharingStarted.WhileSubscribed — must have a collector for the
        // upstream flow to execute and populate the StateFlow's value.
        val collector = backgroundScope.launch { viewModel.incompleteTrips.collect {} }
        advanceUntilIdle()
        assertEquals(1, viewModel.incompleteTrips.value.size)
        assertEquals(1, viewModel.incompleteTrips.value[0].tripId)
        collector.cancel()
    }
}
