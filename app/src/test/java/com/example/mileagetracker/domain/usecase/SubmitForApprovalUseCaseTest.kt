package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.ApprovalStatus
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubmitForApprovalUseCaseTest {

    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var useCase: SubmitForApprovalUseCase

    @Before
    fun setUp() {
        approvalRepository = mockk()
        useCase = SubmitForApprovalUseCase(approvalRepository)
    }

    @Test
    fun `success with tripId`() = runTest {
        coEvery { approvalRepository.insertRequest(any()) } returns 500L

        val result = useCase(submittedByUserId = 10L, tripId = 1L)

        assertTrue(result.isSuccess)
        assertEquals(500L, result.getOrNull())
        coEvery { approvalRepository.insertRequest(match {
            it.tripId == 1L && it.expenseId == null && it.status == ApprovalStatus.PENDING
        }) }
    }

    @Test
    fun `success with expenseId`() = runTest {
        coEvery { approvalRepository.insertRequest(any()) } returns 501L

        val result = useCase(submittedByUserId = 10L, expenseId = 2L)

        assertTrue(result.isSuccess)
        assertEquals(501L, result.getOrNull())
        coEvery { approvalRepository.insertRequest(match {
            it.expenseId == 2L && it.tripId == null
        }) }
    }

    @Test
    fun `failure when both provided`() = runTest {
        val result = useCase(submittedByUserId = 10L, tripId = 1L, expenseId = 2L)

        assertTrue(result.isFailure)
        assertEquals("Provide exactly one of tripId or expenseId.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `failure when neither provided`() = runTest {
        val result = useCase(submittedByUserId = 10L)

        assertTrue(result.isFailure)
        assertEquals("Provide exactly one of tripId or expenseId.", result.exceptionOrNull()?.message)
    }
}
