package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.local.entity.CraRateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [CraRateEntity].
 *
 * ## Read-mostly contract
 * CRA rate rows are inserted once by [CraRateSeeder] (Task 10.x) and are
 * never updated or deleted by user action.  This DAO therefore exposes
 * insert and read operations only.  No `@Update` or `@Delete` methods are
 * provided.
 *
 * ## Rate resolution
 * For a given [taxYear] the correct rate is the row with the highest
 * [CraRateEntity.effectiveFrom] that is still <= the current time.  In
 * practice there is one row per year, but the schema allows mid-year
 * revisions.  All read queries use `ORDER BY effective_from DESC LIMIT 1`
 * to resolve this correctly without any application-layer logic.
 */
@Dao
interface CraRateDao {

    // ───────────────────────────────────────────────────────────────────────────
    // Write
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Insert a single CRA rate row.
     *
     * [OnConflictStrategy.ABORT] is intentional: silently ignoring a
     * duplicate would mask a seeding bug where the same year+effectiveFrom
     * pair is inserted twice with different rate values.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRate(rate: CraRateEntity): Long

    /**
     * Batch-insert multiple rate rows in a single transaction.
     * Used by [CraRateSeeder] to seed all supported years at once.
     * Returns the list of new rowIds in insertion order.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRates(rates: List<CraRateEntity>): List<Long>

    // ───────────────────────────────────────────────────────────────────────────
    // Reads – by year
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Resolve the active CRA rate for [taxYear] as a reactive [Flow].
     *
     * Returns the row with the highest [CraRateEntity.effectiveFrom] for the
     * requested year, so a mid-year rate revision published after the initial
     * seed automatically supersedes the prior row without any code change.
     *
     * Returns `null` if no rate has been seeded for [taxYear] yet (e.g. the
     * app has not received an update containing the new year’s rates).
     *
     * Used by the Dashboard KPI cards so the displayed rate updates
     * reactively if the seeder runs in the background on first launch.
     */
    @Query(
        """
        SELECT * FROM cra_rates
        WHERE  tax_year = :taxYear
        ORDER BY effective_from DESC
        LIMIT 1
    """
    )
    fun getRateForYear(taxYear: Int): Flow<CraRateEntity?>

    /**
     * One-shot fetch of the active CRA rate for [taxYear].
     * Use this in use-cases and calculation paths where subscribing to a
     * [Flow] is not appropriate (e.g. inside [CalculateReimbursementUseCase]).
     *
     * Returns `null` if no rate is seeded for [taxYear].
     */
    @Query(
        """
        SELECT * FROM cra_rates
        WHERE  tax_year = :taxYear
        ORDER BY effective_from DESC
        LIMIT 1
    """
    )
    suspend fun getRateForYearOnce(taxYear: Int): CraRateEntity?

    // ───────────────────────────────────────────────────────────────────────────
    // Reads – seeding checks
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Returns `true` if at least one rate row exists for [taxYear].
     *
     * Used by [CraRateSeeder] (Task 10.x) to make seeding idempotent:
     * ```kotlin
     * if (!craRateDao.isYearSeeded(currentYear)) {
     *     craRateDao.insertRates(seedDataFor(currentYear))
     * }
     * ```
     * Checking per-year (rather than a global "any rows exist" check) allows
     * the seeder to add newly supported years on app update without
     * re-inserting rates for years already present.
     */
    @Query("SELECT COUNT(*) > 0 FROM cra_rates WHERE tax_year = :taxYear")
    suspend fun isYearSeeded(taxYear: Int): Boolean

    /**
     * Returns the list of tax years for which at least one rate row exists,
     * sorted ascending.
     *
     * Used by [CraRateSeeder] to determine which years still need seeding
     * when a range of years is being populated, and by the Settings screen
     * to display "CRA rates available for: 2022, 2023, 2024, 2025, 2026".
     */
    @Query("SELECT DISTINCT tax_year FROM cra_rates ORDER BY tax_year ASC")
    suspend fun getSeededYears(): List<Int>
}
