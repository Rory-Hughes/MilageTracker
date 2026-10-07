package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.ReportHistoryDao
import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import com.example.mileagetracker.domain.repository.ReportHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ReportHistoryRepository].
 *
 * ## CRA 6-year retention guard
 * [deleteOlderThan] computes the minimum permissible threshold as
 * `System.currentTimeMillis() - SIX_YEAR_MS` and throws
 * [IllegalArgumentException] if the caller's threshold is newer than that,
 * which would risk deleting records still within the CRA retention window.
 *
 * [deleteById] does **not** enforce the retention guard — the guard is
 * applied by [ReportHistoryViewModel.deleteReport] before calling this
 * method, keeping business logic in the ViewModel where it belongs.
 *
 * ## Dispatcher policy
 * Suspend writes switch to [Dispatchers.IO] explicitly.  Flow-returning
 * reads rely on Room's internal IO executor.
 */
@Singleton
class ReportHistoryRepositoryImpl @Inject constructor(
    private val dao: ReportHistoryDao,
) : ReportHistoryRepository {

    override suspend fun save(report: ReportHistoryEntity): Int =
        withContext(Dispatchers.IO) {
            dao.insert(report).toInt()
        }

    override fun getAll(): Flow<List<ReportHistoryEntity>> =
        dao.getAll()

    override suspend fun getById(id: Int): ReportHistoryEntity? =
        withContext(Dispatchers.IO) {
            dao.getById(id)
        }

    override suspend fun deleteById(id: Int) =
        withContext(Dispatchers.IO) {
            dao.deleteById(id)
        }

    override suspend fun deleteOlderThan(thresholdMs: Long) {
        val minimumAllowed = System.currentTimeMillis() - SIX_YEAR_MS
        require(thresholdMs <= minimumAllowed) {
            "deleteOlderThan threshold $thresholdMs violates the CRA 6-year retention " +
                "minimum.  Minimum permissible threshold: $minimumAllowed"
        }
        withContext(Dispatchers.IO) {
            dao.deleteOlderThan(thresholdMs)
        }
    }

    private companion object {
        /** 6 years expressed in milliseconds (non-leap approximation; safe for guard purposes). */
        const val SIX_YEAR_MS = 6L * 365 * 24 * 60 * 60 * 1_000
    }
}
