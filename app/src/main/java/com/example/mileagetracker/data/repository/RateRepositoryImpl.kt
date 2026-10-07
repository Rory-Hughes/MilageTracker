package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.CraRateDao
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.domain.repository.RateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [RateRepository].
 *
 * Injected as a [Singleton] by Hilt via [RepositoryModule].
 *
 * ## Read-mostly, write-once
 * CRA rate rows are written once by [CraRateSeeder] and never updated or
 * deleted by user action.  The write surface is intentionally minimal.
 *
 * ## Idempotency contract
 * [insertRate] and [insertRates] delegate to DAO methods that use
 * `OnConflictStrategy.IGNORE`.  Re-inserting a row whose
 * `(tax_year, effective_from)` pair already exists is silently discarded
 * at the SQL layer, satisfying the offline-safe guarantee documented in
 * [RateRepository] and proven by [CraRateSeederTest].
 *
 * ## Rate resolution
 * [getRateForYear] and [getRateForYearOnce] always return the row with
 * the highest [CraRateEntity.effectiveFrom] for the requested year,
 * handling rare mid-year CRA revisions transparently.
 *
 * ## Dispatcher policy
 * All suspend operations switch to [Dispatchers.IO].  Flow-returning
 * reads delegate directly to the DAO (Room emits on its own IO executor).
 */
@Singleton
class RateRepositoryImpl @Inject constructor(
    private val craRateDao: CraRateDao,
) : RateRepository {

    // ───────────────────────────────────────────────────────────────────────────
    // Write (seeding only)
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single CRA rate row using `INSERT OR IGNORE`.
     *
     * Re-inserting a row for a `(taxYear, effectiveFrom)` pair that is
     * already present is silently discarded — no exception, no duplicate.
     */
    override suspend fun insertRate(rate: CraRateEntity): Unit =
        withContext(Dispatchers.IO) {
            craRateDao.insertRate(rate)
        }

    /**
     * Batch-inserts multiple CRA rate rows in a single transaction using
     * `INSERT OR IGNORE`.  More efficient than calling [insertRate] in a
     * loop for the 18-row first-launch seed.
     */
    override suspend fun insertRates(rates: List<CraRateEntity>): Unit =
        withContext(Dispatchers.IO) {
            if (rates.isNotEmpty()) craRateDao.insertRates(rates)
        }

    /**
     * Returns `true` if at least one row exists for [taxYear].
     *
     * Backed by `SELECT COUNT(*) > 0`; cheaper than loading a full entity
     * just to null-check it.  Used by [CraRateSeeder.seedAll] to skip
     * already-present years.
     */
    override suspend fun isYearSeeded(taxYear: Int): Boolean =
        withContext(Dispatchers.IO) {
            craRateDao.isYearSeeded(taxYear)
        }

    /** Returns the list of tax years for which rate data has been seeded. */
    override suspend fun getSeededYears(): List<Int> =
        withContext(Dispatchers.IO) {
            craRateDao.getSeededYears()
        }

    // ───────────────────────────────────────────────────────────────────────────
    // Reads
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Reactive stream of the resolved CRA rate for [taxYear].
     *
     * Emits `null` if not yet seeded.  Re-emits automatically when the
     * seeder inserts a row on first launch, so Dashboard KPI cards update
     * without any manual refresh.
     */
    override fun getRateForYear(taxYear: Int): Flow<CraRateEntity?> =
        craRateDao.getRateForYear(taxYear)

    /**
     * One-shot fetch of the resolved CRA rate for [taxYear].
     * Returns `null` if not seeded.  Used by [CalculateReimbursementUseCase]
     * and report generation.
     */
    override suspend fun getRateForYearOnce(taxYear: Int): CraRateEntity? =
        withContext(Dispatchers.IO) {
            craRateDao.getRateForYearOnce(taxYear)
        }
}
