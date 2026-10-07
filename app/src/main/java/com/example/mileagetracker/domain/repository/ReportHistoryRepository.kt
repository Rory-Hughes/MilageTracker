package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for CRA logbook export history persistence.
 *
 * ## CRA 6-year retention guard
 * CRA requires taxpayers to retain records supporting their income tax
 * return for at least 6 years from the end of the tax year to which they
 * relate.  [deleteOlderThan] enforces this by refusing to delete rows
 * within the retention window and throwing [IllegalArgumentException] when
 * the supplied threshold would violate the minimum.
 *
 * The [ReportHistoryCleanupWorker] (Task 5.5) calls [deleteOlderThan] with
 * a threshold of 7 years before the current date, which is safely beyond
 * the CRA minimum.
 *
 * ## Implementation
 * [ReportHistoryRepositoryImpl] satisfies this interface and is bound in
 * [RepositoryModule].
 */
interface ReportHistoryRepository {

    /**
     * Persist a new export record.
     *
     * @return The database-assigned ID of the inserted row.
     */
    suspend fun save(report: ReportHistoryEntity): Int

    /**
     * Reactive stream of all report history rows, ordered by
     * [ReportHistoryEntity.generatedAt] descending (newest first).
     *
     * Emits a new list every time any row is inserted or deleted.
     */
    fun getAll(): Flow<List<ReportHistoryEntity>>

    /**
     * Fetch a single report history row by its primary key.
     * Returns `null` if no matching row exists.
     */
    suspend fun getById(id: Int): ReportHistoryEntity?

    /**
     * Delete a single export record by its primary key.
     *
     * **Caller responsibility:** the caller must verify that the record's
     * [ReportHistoryEntity.generatedAt] is outside the CRA 6-year retention
     * window before calling this method.  [ReportHistoryViewModel.deleteReport]
     * performs this check and blocks the call if the guard is not satisfied.
     *
     * @param id Primary key of the row to delete.
     */
    suspend fun deleteById(id: Int)

    /**
     * Delete all export records whose [ReportHistoryEntity.generatedAt] is
     * before [thresholdMs] (epoch millis).
     *
     * ## Retention guard
     * Throws [IllegalArgumentException] if [thresholdMs] is less than 6 years
     * before the current time, preventing accidental deletion of records that
     * CRA may still require.
     *
     * Call this only from [ReportHistoryCleanupWorker] with a threshold of
     * at least 7 years before the current time.
     *
     * @param thresholdMs epoch millis before which rows are eligible for deletion.
     * @throws IllegalArgumentException if [thresholdMs] violates the 6-year guard.
     */
    suspend fun deleteOlderThan(thresholdMs: Long)
}
