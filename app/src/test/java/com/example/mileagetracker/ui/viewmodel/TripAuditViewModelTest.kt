package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.mileagetracker.domain.repository.AuditRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class TripAuditViewModelTest {

    private lateinit var viewModel: TripAuditViewModel
    private lateinit var auditRepository: AuditRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        auditRepository = mockk()
        val savedStateHandle = SavedStateHandle(mapOf(TripAuditViewModel.KEY_TRIP_ID to 1))
        
        every { auditRepository.getAuditTrailForTrip(1) } returns flowOf(emptyList())

        viewModel = TripAuditViewModel(savedStateHandle, auditRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading then Success`() = runTest {
        assertEquals(TripAuditUiState.Loading, viewModel.uiState.value)

        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is TripAuditUiState.Success)
        assertEquals(1, (viewModel.uiState.value as TripAuditUiState.Success).tripId)
        collector.cancel()
    }
}
