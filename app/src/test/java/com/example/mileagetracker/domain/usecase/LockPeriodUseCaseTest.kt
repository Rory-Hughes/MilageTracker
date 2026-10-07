package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.ApprovalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LockPeriodUseCaseTest {

    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var useCase: LockPeriodUseCase

    @Before
    fun setUp() {
        approvalRepository = mockk()
        useCase = LockPeriodUseCase(approvalRepository)
    }

    @Test
    fun `success locks new period`() = runTest {
        coEvery { approvalRepository.isLocked(2025, 5) } returns false
        coEvery { approvalRepository.lockPeriod(any()) } returns 77L

        val result = useCase(2025, 5, 10L)

        assertTrue(result.isSuccess)
        assertEquals(77L, result.getOrNull())
        coVerify { approvalRepository.lockPeriod(match {
            it.periodYear == 2025 && it.periodMonth == 5 && it.lockedByUserId == 10L
        }) }
    }

    @Test
    fun `failure when already locked`() = runTest {
        coEvery { approvalRepository.isLocked(2025, 5) } returns true

        val result = useCase(2025, 5, 10L)

        assertTrue(result.isFailure)
        assertEquals("Period 2025/5 is already locked.", result.exceptionOrNull()?.message)
    }
}
