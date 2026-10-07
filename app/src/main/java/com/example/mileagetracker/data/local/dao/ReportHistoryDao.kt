package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [ReportHistoryEntity] (table: `report_history`).
 *
 * ## Queries
 * - [insert]          — persists a new export record.
 * - [getAll]          — reactive stream, newest first; drives [ReportHistoryScreen].
 * - [getById]         — one-shot fetch for re-share flow.
 * - [deleteById]      — hard-delete a single row by PK; used by [ReportHistoryViewModel]
 *                       for user-initiated deletes outside the CRA retention window.
 * - [deleteOlderThan] — bulk hard-delete by age threshold; called only by
 *                       [ReportHistoryCleanupWorker] after the 7-year retention window.
 */
@Dao
interface ReportHistoryDao {

    /**
     * Insert a new report history row.
     * Returns the new row's `rowId` (same as `id` for auto-PKs).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportHistoryEntity): Long

    /**
     * Reactive stream of all report history rows, ordered by
     * [ReportHistoryEntity.generatedAt] descending (newest first).
     */
    @Query(
        """
        SELECT * FROM report_history
        ORDER BY generated_at DESC
        """
    )
    fun getAll(): Flow<List<ReportHistoryEntity>>

    /**
     * Fetch a single report history row by its primary key.
     * Returns `null` if no matching row exists.
     */
    @Query("SELECT * FROM report_history WHERE id = :id")
    suspend fun getById(id: Int): ReportHistoryEntity?

    /**
     * Hard-delete a single report history row by its primary key.
     *
     * Used by [ReportHistoryViewModel] when the user manually deletes a
     * record that has already passed the CRA 6-year retention window.
     * The caller is responsible for validating the retention guard before
     * invoking this method.
     *
     * @param id Primary key of the row to delete.
     */
    @Query("DELETE FROM report_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Hard-delete all rows whose [ReportHistoryEntity.generatedAt] is
     * strictly before [thresholdMs] (epoch millis).
     *
     * Called by [ReportHistoryCleanupWorker] to prune exports older than
     * 7 years.  **Must not be called with a threshold less than 6 years
     * in the past**; [ReportHistoryRepository.deleteOlderThan] enforces
     * this guard.
     *
     * @param thresholdMs epoch millis before which rows will be deleted.
     */
    @Query("DELETE FROM report_history WHERE generated_at < :thresholdMs")
    suspend fun deleteOlderThan(thresholdMs: Long)
}
