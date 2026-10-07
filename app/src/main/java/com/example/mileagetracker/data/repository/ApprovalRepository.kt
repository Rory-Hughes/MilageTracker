package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.ApprovalRequestDao
import com.example.mileagetracker.data.local.dao.PeriodLockDao
import com.example.mileagetracker.data.local.entity.ApprovalRequestEntity
import com.example.mileagetracker.data.local.entity.PeriodLockEntity
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.PeriodLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ApprovalRequestRepository {
    suspend fun insertRequest(request: ApprovalRequest): Long

    suspend fun updateRequest(request: ApprovalRequest)

    fun observePending(): Flow<List<ApprovalRequest>>

    suspend fun getForTrip(tripId: Long): ApprovalRequest?

    suspend fun getForExpense(expenseId: Long): ApprovalRequest?

    suspend fun getById(id: Long): ApprovalRequest?

    fun observeForDriver(userId: Long): Flow<List<ApprovalRequest>>

    suspend fun getApprovedInRange(fromMillis: Long, toMillis: Long): List<ApprovalRequest>
}

interface PeriodLockRepository {
    suspend fun lockPeriod(lock: PeriodLock): Long

    suspend fun unlockPeriod(year: Int, month: Int?)

    suspend fun isLocked(year: Int, month: Int?): Boolean

    fun observeAllLocks(): Flow<List<PeriodLock>>
}

@Singleton
class ApprovalRepository @Inject constructor(
    approvalRequestDao: ApprovalRequestDao,
    periodLockDao: PeriodLockDao,
) : ApprovalRequestRepository by RoomApprovalRequestRepository(approvalRequestDao),
    PeriodLockRepository by RoomPeriodLockRepository(periodLockDao)

private class RoomApprovalRequestRepository(
    private val dao: ApprovalRequestDao,
) : ApprovalRequestRepository {
    override suspend fun insertRequest(request: ApprovalRequest): Long =
        dao.insert(ApprovalRequestEntity.fromDomain(request))

    override suspend fun updateRequest(request: ApprovalRequest) {
        dao.update(ApprovalRequestEntity.fromDomain(request))
    }

    override fun observePending(): Flow<List<ApprovalRequest>> =
        dao.getPendingForManager().map { list -> list.map { it.toDomain() } }

    override suspend fun getForTrip(tripId: Long): ApprovalRequest? =
        dao.getForTrip(tripId)?.toDomain()

    override suspend fun getForExpense(expenseId: Long): ApprovalRequest? =
        dao.getForExpense(expenseId)?.toDomain()

    override suspend fun getById(id: Long): ApprovalRequest? =
        dao.getById(id)?.toDomain()

    override fun observeForDriver(userId: Long): Flow<List<ApprovalRequest>> =
        dao.getForDriver(userId).map { list -> list.map { it.toDomain() } }

    override suspend fun getApprovedInRange(
        fromMillis: Long,
        toMillis: Long,
    ): List<ApprovalRequest> =
        dao.getApprovedInRange(fromMillis, toMillis).map { it.toDomain() }
}

private class RoomPeriodLockRepository(
    private val dao: PeriodLockDao,
) : PeriodLockRepository {
    override suspend fun lockPeriod(lock: PeriodLock): Long =
        dao.insert(PeriodLockEntity.fromDomain(lock))

    override suspend fun unlockPeriod(year: Int, month: Int?) {
        dao.delete(year, month)
    }

    override suspend fun isLocked(year: Int, month: Int?): Boolean =
        dao.isLocked(year, month)

    override fun observeAllLocks(): Flow<List<PeriodLock>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }
}
