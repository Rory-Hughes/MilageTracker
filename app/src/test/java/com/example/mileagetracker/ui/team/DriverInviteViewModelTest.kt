package com.example.mileagetracker.ui.team

import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.domain.usecase.InviteTeamMemberUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriverInviteViewModelTest {

    private lateinit var viewModel: DriverInviteViewModel
    private lateinit var inviteUseCase: InviteTeamMemberUseCase
    private lateinit var policyRepository: PolicyRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        inviteUseCase = mockk()
        policyRepository = mockk()

        every { policyRepository.observePolicies() } returns flowOf(emptyList())
        viewModel = DriverInviteViewModel(inviteUseCase, policyRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendInvite with invalid email sets error`() {
        viewModel.onEmailChange("invalid")
        viewModel.sendInvite()
        assertTrue(viewModel.uiState.value.emailError)
    }

    @Test
    fun `sendInvite success updates message and resets fields`() = runTest {
        coEvery { inviteUseCase(any(), any(), any()) } returns mockk()

        viewModel.onEmailChange("test@example.com")
        viewModel.sendInvite()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("", state.email)
        assertEquals("Invite sent to test@example.com.", state.statusMessage)
    }

    @Test
    fun `sendInvite failure updates status message`() = runTest {
        coEvery { inviteUseCase(any(), any(), any()) } throws RuntimeException("Network Error")

        viewModel.onEmailChange("test@example.com")
        viewModel.sendInvite()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.statusMessage?.contains("Network Error") == true)
    }
}
