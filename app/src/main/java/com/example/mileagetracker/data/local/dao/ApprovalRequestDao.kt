package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mileagetracker.data.local.entity.ApprovalRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApprovalRequestDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(request: ApprovalRequestEntity): Long

    @Update
    suspend fun update(request: ApprovalRequestEntity)

    @Query("SELECT * FROM approval_requests WHERE status = 'PENDING' ORDER BY submittedAt ASC")
    fun getPendingForManager(): Flow<List<ApprovalRequestEntity>>

    @Query("SELECT * FROM approval_requests WHERE tripId = :tripId")
    suspend fun getForTrip(tripId: Long): ApprovalRequestEntity?

    @Query("SELECT * FROM approval_requests WHERE expenseId = :expenseId")
    suspend fun getForExpense(expenseId: Long): ApprovalRequestEntity?

    @Query("SELECT * FROM approval_requests WHERE id = :id")
    suspend fun getById(id: Long): ApprovalRequestEntity?

    @Query("SELECT * FROM approval_requests WHERE submittedByUserId = :userId ORDER BY submittedAt DESC")
    fun getForDriver(userId: Long): Flow<List<ApprovalRequestEntity>>

    @Query(
        """
        SELECT * FROM approval_requests
        WHERE status = 'APPROVED'
          AND reviewedAt IS NOT NULL
          AND reviewedAt >= :fromMillis
          AND reviewedAt <= :toMillis
        ORDER BY reviewedAt ASC
        """
    )
    suspend fun getApprovedInRange(
        fromMillis: Long,
        toMillis: Long,
    ): List<ApprovalRequestEntity>
}
