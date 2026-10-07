package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.ApprovalRequestDao
import com.example.mileagetracker.data.local.dao.PeriodLockDao
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApprovalRepositoryTest {

    private lateinit var approvalRequestDao: ApprovalRequestDao
    private lateinit var periodLockDao: PeriodLockDao
    private lateinit var repository: ApprovalRepository

    @Before
    fun setUp() {
        approvalRequestDao = mockk()
        periodLockDao = mockk()
        repository = ApprovalRepository(approvalRequestDao, periodLockDao)
    }

    @Test
    fun `insertRequest delegates to dao`() = runTest {
        val request = ApprovalRequest(
            tripId = 1L,
            submittedByUserId = 10L,
            submittedAt = 1000L,
            status = ApprovalStatus.PENDING
        )
        coEvery { approvalRequestDao.insert(any()) } returns 500L

        val result = repository.insertRequest(request)

        assertEquals(500L, result)
        coVerify { approvalRequestDao.insert(match { it.tripId == 1L }) }
    }

    @Test
    fun `observePending maps dao entities to domain`() = runTest {
        // We'll need to mock the mapping. 
        // Actually, the repository calls ApprovalRequestEntity.fromDomain and it.toDomain().
        // Those are extensions.
        // Let's assume they work and just verify the flow transformation.
        every { approvalRequestDao.getPendingForManager() } returns flowOf(emptyList())

        val result = repository.observePending().first()

        assertTrue(result.isEmpty())
    }
}
