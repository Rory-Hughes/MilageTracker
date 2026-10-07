package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApproveRejectTripUseCaseTest {

    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var useCase: ApproveRejectTripUseCase

    @Before
    fun setUp() {
        approvalRepository = mockk()
        useCase = ApproveRejectTripUseCase(approvalRepository)
    }

    private fun testRequest(id: Long) = ApprovalRequest(
        id = id,
        tripId = 1,
        submittedByUserId = 10,
        submittedAt = 1000L,
        status = ApprovalStatus.PENDING
    )

    @Test
    fun `success status APPROVED updates request`() = runTest {
        val existing = testRequest(100L)
        coEvery { approvalRepository.getById(100L) } returns existing
        coEvery { approvalRepository.updateRequest(any()) } returns Unit

        val result = useCase(requestId = 100L, reviewerUserId = 50L, newStatus = ApprovalStatus.APPROVED)

        assertTrue(result.isSuccess)
        coVerify {
            approvalRepository.updateRequest(match {
                it.id == 100L && it.status == ApprovalStatus.APPROVED && it.reviewedByUserId == 50L
            })
        }
    }

    @Test
    fun `failure status REJECTED without comment`() = runTest {
        val result = useCase(requestId = 1L, reviewerUserId = 2L, newStatus = ApprovalStatus.REJECTED, comment = null)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("A comment is required when rejecting or returning a request.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `failure requestId not found`() = runTest {
        coEvery { approvalRepository.getById(999L) } returns null

        val result = useCase(requestId = 999L, reviewerUserId = 2L, newStatus = ApprovalStatus.APPROVED)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `failure cannot set status to PENDING`() = runTest {
        val result = useCase(requestId = 1L, reviewerUserId = 2L, newStatus = ApprovalStatus.PENDING)

        assertTrue(result.isFailure)
        assertEquals("Cannot set status back to PENDING.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `failure status RETURNED without comment`() = runTest {
        val result = useCase(requestId = 1L, reviewerUserId = 2L, newStatus = ApprovalStatus.RETURNED, comment = null)

        assertTrue(result.isFailure)
        assertEquals("A comment is required when rejecting or returning a request.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `success status RETURNED with comment`() = runTest {
        val existing = testRequest(200L)
        coEvery { approvalRepository.getById(200L) } returns existing
        coEvery { approvalRepository.updateRequest(any()) } returns Unit

        val result = useCase(
            requestId = 200L,
            reviewerUserId = 50L,
            newStatus = ApprovalStatus.RETURNED,
            comment = "Needs more info",
        )

        assertTrue(result.isSuccess)
        coVerify {
            approvalRepository.updateRequest(match {
                it.id == 200L && it.status == ApprovalStatus.RETURNED && it.reviewComment == "Needs more info"
            })
        }
    }
}
