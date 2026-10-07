package com.example.mileagetracker.ui.team

import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.domain.usecase.GetReimbursementForecastUseCase
import io.mockk.coEvery
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerAnalyticsViewModelTest {

    private lateinit var viewModel: ManagerAnalyticsViewModel
    private lateinit var getReimbursementForecastUseCase: GetReimbursementForecastUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getReimbursementForecastUseCase = mockk()
        viewModel = ManagerAnalyticsViewModel(getReimbursementForecastUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testUser(role: TeamRole) = TeamMember(
        userId = "u1", email = "u1@e.com", displayName = "U1", role = role, isActive = true
    )

    @Test
    fun `initialise with unauthorised role hides analytics`() {
        viewModel.initialise(testUser(TeamRole.DRIVER), emptyList())
        assertFalse(viewModel.uiState.value.isRoleAuthorised)
    }

    @Test
    fun `initialise with MANAGER role loads data`() = runTest {
        val manager = testUser(TeamRole.MANAGER)
        val drivers = listOf(testUser(TeamRole.DRIVER))
        coEvery { getReimbursementForecastUseCase(drivers, any(), any()) } returns emptyList()

        viewModel.initialise(manager, drivers)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRoleAuthorised)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `previousMonth updates date and reloads`() = runTest {
        val manager = testUser(TeamRole.MANAGER)
        val currentMonth = viewModel.uiState.value.selectedMonth
        coEvery { getReimbursementForecastUseCase(any(), any(), any()) } returns emptyList()

        viewModel.initialise(manager, emptyList())
        viewModel.previousMonth()
        
        val newMonth = if (currentMonth == 0) 11 else currentMonth - 1
        assertEquals(newMonth, viewModel.uiState.value.selectedMonth)
        coEvery { getReimbursementForecastUseCase(emptyList(), any(), newMonth) }
    }
}
