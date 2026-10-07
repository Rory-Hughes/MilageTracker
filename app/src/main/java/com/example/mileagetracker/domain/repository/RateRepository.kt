package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.data.local.entity.CraRateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for CRA per-km reimbursement rate operations.
 *
 * ## Read-mostly, write-once contract
 * CRA rates are seeded once by [CraRateSeeder] and are never updated or
 * deleted by user action.  The write surface is intentionally minimal:
 * [insertRate] for single rows, [insertRates] for a batch.
 *
 * ## Idempotency requirement
 * All insert implementations **must** use an `INSERT OR IGNORE` (or
 * equivalent `OnConflictStrategy.IGNORE`) strategy on the `(tax_year,
 * effective_from)` unique pair.  This ensures that calling [insertRates]
 * multiple times with the same data is a no-op, satisfying the offline-
 * safe guarantee described in [CraRateSeeder].
 *
 * ## Rate resolution
 * For any given [taxYear], [getRateForYear] resolves the correct row by
 * selecting the row with the highest [CraRateEntity.effectiveFrom],
 * handling rare mid-year rate revisions transparently.
 */
interface RateRepository {

    // ───────────────────────────────────────────────────────────────────────────
    // Write (seeding only)
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single CRA rate row.
     *
     * Implementations must use `INSERT OR IGNORE` so that re-inserting
     * an already-present row for the same `(tax_year, effective_from)` is
     * silently skipped rather than throwing a constraint violation.
     */
    suspend fun insertRate(rate: CraRateEntity)

    /**
     * Inserts a batch of CRA rate rows in a single transaction.
     *
     * Equivalent to calling [insertRate] for each row, but more efficient
     * for the 18-row first-launch seed.  Same `INSERT OR IGNORE` contract.
     */
    suspend fun insertRates(rates: List<CraRateEntity>)

    /**
     * Returns `true` if at least one rate row exists for [taxYear].
     *
     * Backed by a `SELECT COUNT(*) > 0` query; cheaper than fetching the
     * full entity just to null-check it.  Used by [CraRateSeeder] to
     * skip years that are already present.
     */
    suspend fun isYearSeeded(taxYear: Int): Boolean

    /**
     * Returns the list of tax years for which rate data has been seeded.
     * Useful for diagnostics, settings screens, and the year-picker UI.
     */
    suspend fun getSeededYears(): List<Int>

    // ───────────────────────────────────────────────────────────────────────────
    // Reads
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Reactive stream of the active CRA rate for [taxYear].
     * Emits `null` when no rate has been seeded yet; re-emits when the
     * seeder inserts a row for that year on first launch.
     */
    fun getRateForYear(taxYear: Int): Flow<CraRateEntity?>

    /**
     * One-shot fetch of the active CRA rate for [taxYear].
     * Returns `null` if no rate has been seeded.
     * Used by [CalculateReimbursementUseCase] and report generation.
     */
    suspend fun getRateForYearOnce(taxYear: Int): CraRateEntity?
}
