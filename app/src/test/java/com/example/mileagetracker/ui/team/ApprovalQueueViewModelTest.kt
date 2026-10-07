package com.example.mileagetracker.ui.team

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus
import com.example.mileagetracker.domain.usecase.ApproveRejectTripUseCase
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalQueueViewModelTest {

    private lateinit var viewModel: ApprovalQueueViewModel
    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var approveRejectTripUseCase: ApproveRejectTripUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        approvalRepository = mockk()
        approveRejectTripUseCase = mockk()

        every { approvalRepository.observePending() } returns flowOf(emptyList())
        viewModel = ApprovalQueueViewModel(approvalRepository, approveRejectTripUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes and loads pending requests`() = runTest {
        val requests = listOf(
            ApprovalRequest(
                id = 1,
                tripId = 10,
                submittedByUserId = 100,
                submittedAt = 1000,
                status = ApprovalStatus.PENDING
            )
        )
        every { approvalRepository.observePending() } returns flowOf(requests)

        // Re-init to pick up mocked flow
        viewModel = ApprovalQueueViewModel(approvalRepository, approveRejectTripUseCase)
        advanceUntilIdle()

        assertEquals(requests, viewModel.uiState.value.pendingRequests)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `approve success updates message`() = runTest {
        coEvery { approveRejectTripUseCase(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.approve(requestId = 1, reviewerUserId = 50)
        advanceUntilIdle()

        assertEquals("Request approved.", viewModel.uiState.value.successMessage)
    }

    @Test
    fun `reject success updates message`() = runTest {
        coEvery { approveRejectTripUseCase(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.reject(requestId = 1, reviewerUserId = 50, comment = "Bad trip")
        advanceUntilIdle()

        assertEquals("Request rejected.", viewModel.uiState.value.successMessage)
    }

    @Test
    fun `clearMessage resets messages`() = runTest {
        coEvery { approveRejectTripUseCase(any(), any(), any(), any()) } returns Result.success(Unit)
        viewModel.approve(1, 50)
        advanceUntilIdle()

        viewModel.clearMessage()
        assertNull(viewModel.uiState.value.successMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
