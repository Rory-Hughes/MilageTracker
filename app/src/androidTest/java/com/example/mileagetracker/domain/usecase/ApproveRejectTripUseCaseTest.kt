package com.example.mileagetracker.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [ApproveRejectTripUseCase] against a real in-memory Room DB.
 *
 * These verify not just validation, but that successful decisions persist the
 * expected status/reviewer metadata and failed decisions do not mutate the row.
 */
@RunWith(AndroidJUnit4::class)
class ApproveRejectTripUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var useCase: ApproveRejectTripUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        approvalRepository = ApprovalRepository(db.approvalRequestDao(), db.periodLockDao())
        useCase = ApproveRejectTripUseCase(approvalRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun request(
        tripId: Long = 1L,
        submittedByUserId: Long = 10L,
        submittedAt: Long = 1_000L,
        status: ApprovalStatus = ApprovalStatus.PENDING,
    ) = ApprovalRequest(
        tripId = tripId,
        submittedByUserId = submittedByUserId,
        submittedAt = submittedAt,
        status = status,
    )

    private suspend fun insertPendingRequest(tripId: Long = 1L): Long =
        approvalRepository.insertRequest(request(tripId = tripId))

    @Test
    fun invoke_approved_persistsStatusReviewerAndReviewedAt() = runTest {
        val requestId = insertPendingRequest()
        val before = System.currentTimeMillis()

        val result = useCase(
            requestId = requestId,
            reviewerUserId = 50L,
            newStatus = ApprovalStatus.APPROVED,
        )

        val after = System.currentTimeMillis()
        assertTrue(result.isSuccess)

        val updated = approvalRepository.getById(requestId)!!
        assertEquals(ApprovalStatus.APPROVED, updated.status)
        assertEquals(50L, updated.reviewedByUserId)
        assertNotNull(updated.reviewedAt)
        assertTrue(updated.reviewedAt!! in before..after)
        assertNull(updated.reviewComment)
    }

    @Test
    fun invoke_returnedWithComment_persistsReturnedStateAndComment() = runTest {
        val requestId = insertPendingRequest(tripId = 7L)

        val result = useCase(
            requestId = requestId,
            reviewerUserId = 99L,
            newStatus = ApprovalStatus.RETURNED,
            comment = "Needs a clearer trip purpose",
        )

        assertTrue(result.isSuccess)

        val updated = approvalRepository.getById(requestId)!!
        assertEquals(ApprovalStatus.RETURNED, updated.status)
        assertEquals(99L, updated.reviewedByUserId)
        assertEquals("Needs a clearer trip purpose", updated.reviewComment)
        assertNotNull(updated.reviewedAt)
    }

    @Test
    fun invoke_rejectedWithoutComment_returnsFailureAndDoesNotMutateRow() = runTest {
        val requestId = insertPendingRequest()

        val result = useCase(
            requestId = requestId,
            reviewerUserId = 77L,
            newStatus = ApprovalStatus.REJECTED,
            comment = null,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)

        val unchanged = approvalRepository.getById(requestId)!!
        assertEquals(ApprovalStatus.PENDING, unchanged.status)
        assertNull(unchanged.reviewedByUserId)
        assertNull(unchanged.reviewedAt)
        assertNull(unchanged.reviewComment)
    }

    @Test
    fun invoke_pendingStatus_returnsFailureAndDoesNotMutateRow() = runTest {
        val requestId = insertPendingRequest()

        val result = useCase(
            requestId = requestId,
            reviewerUserId = 12L,
            newStatus = ApprovalStatus.PENDING,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)

        val unchanged = approvalRepository.getById(requestId)!!
        assertEquals(ApprovalStatus.PENDING, unchanged.status)
        assertNull(unchanged.reviewedByUserId)
        assertNull(unchanged.reviewedAt)
    }

    @Test
    fun invoke_missingRequest_returnsFailureWithoutCreatingRow() = runTest {
        val result = useCase(
            requestId = 9999L,
            reviewerUserId = 12L,
            newStatus = ApprovalStatus.APPROVED,
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
        assertNull(approvalRepository.getById(9999L))
    }
}
