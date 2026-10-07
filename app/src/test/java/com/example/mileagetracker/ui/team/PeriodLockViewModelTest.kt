package com.example.mileagetracker.ui.team

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.usecase.LockPeriodUseCase
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodLockViewModelTest {

    private lateinit var viewModel: PeriodLockViewModel
    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var lockPeriodUseCase: LockPeriodUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        approvalRepository = mockk()
        lockPeriodUseCase = mockk()

        every { approvalRepository.observeAllLocks() } returns flowOf(emptyList())
        viewModel = PeriodLockViewModel(approvalRepository, lockPeriodUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initializes with last 12 months`() = runTest {
        advanceUntilIdle()
        assertEquals(12, viewModel.uiState.value.periodRows.size)
        assertEquals(false, viewModel.uiState.value.isLoading)
    }

    @Test
    fun `lock success updates message`() = runTest {
        coEvery { lockPeriodUseCase(any(), any(), any()) } returns Result.success(1L)
        
        viewModel.lock(2025, 1, 50)
        advanceUntilIdle()
        
        assertEquals("Period locked.", viewModel.uiState.value.message)
    }

    @Test
    fun `unlock success updates message`() = runTest {
        coEvery { approvalRepository.unlockPeriod(any(), any()) } returns Unit
        
        viewModel.unlock(2025, 1)
        advanceUntilIdle()
        
        assertEquals("Period unlocked.", viewModel.uiState.value.message)
    }
}
