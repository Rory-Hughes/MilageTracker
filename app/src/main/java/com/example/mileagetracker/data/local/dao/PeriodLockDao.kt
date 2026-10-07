package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.local.entity.PeriodLockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodLockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lock: PeriodLockEntity): Long

    @Query(
        "SELECT COUNT(*) > 0 FROM period_locks " +
            "WHERE periodYear = :year AND (periodMonth = :month OR periodMonth IS NULL)"
    )
    suspend fun isLocked(year: Int, month: Int?): Boolean

    @Query("SELECT * FROM period_locks ORDER BY periodYear DESC, periodMonth DESC")
    fun getAll(): Flow<List<PeriodLockEntity>>

    @Query("DELETE FROM period_locks WHERE periodYear = :year AND periodMonth IS :month")
    suspend fun delete(year: Int, month: Int?)
}
