package com.example.mileagetracker.ui.team

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.domain.usecase.GetReimbursementForecastUseCase
import com.example.mileagetracker.domain.usecase.GetTripExceptionsUseCase
import io.mockk.coEvery
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamDashboardViewModelTest {

    private lateinit var viewModel: TeamDashboardViewModel
    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var getTripExceptionsUseCase: GetTripExceptionsUseCase
    private lateinit var getReimbursementForecastUseCase: GetReimbursementForecastUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        approvalRepository = mockk()
        getTripExceptionsUseCase = mockk()
        getReimbursementForecastUseCase = mockk()

        viewModel = TeamDashboardViewModel(
            approvalRepository,
            getTripExceptionsUseCase,
            getReimbursementForecastUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testUser(role: TeamRole) = TeamMember(
        userId = "u1",
        email = "u1@e.com",
        displayName = "U1",
        role = role,
        isActive = true
    )

    @Test
    fun `initialise with unauthorized role hides dashboard`() {
        val user = testUser(TeamRole.DRIVER)
        viewModel.initialise(user, emptyList())

        assertFalse(viewModel.uiState.value.isRoleAuthorised)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `initialise with MANAGER role shows dashboard and loads data`() = runTest {
        val manager = testUser(TeamRole.MANAGER)
        val drivers = listOf(testUser(TeamRole.DRIVER).copy(userId = "d1"))

        every { approvalRepository.observePending() } returns flowOf(emptyList())
        coEvery { getReimbursementForecastUseCase(drivers) } returns emptyList()
        every { getTripExceptionsUseCase("d1") } returns flowOf(emptyList())

        viewModel.initialise(manager, drivers)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRoleAuthorised)
        assertEquals(0, viewModel.uiState.value.pendingQueueCount)
        assertEquals(
            emptyList<com.example.mileagetracker.domain.model.ReimbursementForecast>(),
            viewModel.uiState.value.forecasts
        )
    }
}
