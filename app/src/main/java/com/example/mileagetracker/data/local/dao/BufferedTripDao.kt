package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mileagetracker.data.local.entity.BufferedTripEntity

/**
 * Data-access object for the `buffered_trips` table.
 *
 * Intentionally minimal — all business logic around retry counting and
 * max-retry abandonment lives in [FlushTripBufferUseCase], not here.
 */
@Dao
interface BufferedTripDao {

    /** Enqueue a new trip snapshot for deferred persistence. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BufferedTripEntity): Long

    /** Returns all buffered trips ordered oldest-first for FIFO flushing. */
    @Query("SELECT * FROM buffered_trips ORDER BY created_at ASC")
    suspend fun getAll(): List<BufferedTripEntity>

    /** Removes a successfully flushed entry. */
    @Query("DELETE FROM buffered_trips WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Persists the incremented [BufferedTripEntity.retryCount] after a failed flush. */
    @Update
    suspend fun update(entity: BufferedTripEntity)

    /** Removes all entries that have exhausted their retry budget. */
    @Query("DELETE FROM buffered_trips WHERE retry_count >= :maxRetries")
    suspend fun deleteExhausted(maxRetries: Int)

    /** Returns the number of pending entries — useful for diagnostics/logging. */
    @Query("SELECT COUNT(*) FROM buffered_trips")
    suspend fun count(): Int
}
