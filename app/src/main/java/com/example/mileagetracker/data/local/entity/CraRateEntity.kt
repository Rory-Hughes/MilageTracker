package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores the Canada Revenue Agency (CRA) per-km reimbursement rates for a
 * given tax year.
 *
 * ## CRA two-tier rate structure
 * The CRA uses a two-tier reimbursement model for personal vehicle use:
 *
 * 1. A higher [firstTierRateCents] applies to the first [firstTierLimitKm]
 *    kilometres driven for business in the calendar year.
 * 2. A lower [additionalKmRateCents] applies to every kilometre beyond that
 *    threshold.
 *
 * Official values are resolved by
 * [com.example.mileagetracker.data.seeder.CraRateCatalog].
 *
 * Rates are stored as **integer cents** to avoid
 * floating-point rounding errors in financial calculations.  All reimbursement
 * math in use-cases must work in cents and convert to dollars only for display.
 *
 * ## Seeding and immutability
 * Rows are inserted once by [CraRateSeeder] (Task 10.x) and are never
 * updated by user action.  A future CRA rate update adds a new row with a
 * newer [effectiveFrom] timestamp; the rate resolver always selects the row
 * with the highest [effectiveFrom] for the requested [taxYear].
 *
 * ## One row per year (typical)
 * In most years there is one row per [taxYear].  The [effectiveFrom] field
 * accommodates the rare mid-year rate revision (e.g. a CRA bulletin update)
 * by allowing multiple rows for the same year with different effective dates.
 *
 * ## Source auditability
 * [sourceLabel] records where the rate came from so that future maintainers
 * can verify the seeded data (e.g. `"CRA IT522 2024"`,
 * `"https://www.canada.ca/en/revenue-agency/..."`).  It is for human
 * reference only and is not used in any calculation.
 */
@Entity(
    tableName = "cra_rates",
    indices = [
        Index("tax_year"),
        Index("tax_year", "effective_from"),
    ],
)
data class CraRateEntity(

    // ── Identity ──────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // ── Tax year ──────────────────────────────────────────────────────────────

    /**
     * The four-digit Canadian tax year these rates apply to, e.g. `2024`.
     * Matches the calendar year used by the CRA for T1 personal tax returns.
     */
    @ColumnInfo(name = "tax_year")
    val taxYear: Int,

    // ── First-tier rate ──────────────────────────────────────────────────────

    /**
     * Reimbursement rate in **cents per kilometre** for the first
     * [firstTierLimitKm] kilometres driven for business in the year.
     *
     * Stored as cents (integer) to avoid floating-point rounding errors.
     * Convert to dollars for display only: `firstTierRateCents / 100.0`.
     *
     * Example: a 73-cent rate is stored as `73`.
     */
    @ColumnInfo(name = "first_tier_rate_cents")
    val firstTierRateCents: Int,

    /**
     * The kilometre threshold at which the rate drops from [firstTierRateCents]
     * to [additionalKmRateCents].
     *
     * The CRA threshold has been 5,000 km since 2009.  Stored explicitly so
     * that a future change does not require a code change.
     */
    @ColumnInfo(name = "first_tier_limit_km")
    val firstTierLimitKm: Int,

    // ── Additional km rate ──────────────────────────────────────────────────

    /**
     * Reimbursement rate in **cents per kilometre** for every kilometre
     * beyond [firstTierLimitKm] in the same tax year.
     *
     * Example: a 67-cent rate is stored as `67`.
     */
    @ColumnInfo(name = "additional_km_rate_cents")
    val additionalKmRateCents: Int,

    // ── Source ────────────────────────────────────────────────────────────────

    /**
     * Human-readable reference identifying where this rate was sourced from.
     * Used for maintainer auditability only; not used in any calculation.
     *
     * Examples:
     * - `"CRA IT522 2024"`
     * - `"CRA Automobile Allowance Rates 2025 — canada.ca"`
     */
    @ColumnInfo(name = "source_label")
    val sourceLabel: String,

    // ── Effective date ─────────────────────────────────────────────────────────

    /**
     * Unix epoch millis from which this rate row is considered active for
     * [taxYear].
     *
     * For the typical case (one rate per year, effective January 1) this is
     * the epoch millis of `[taxYear]-01-01T00:00:00Z`.
     *
     * When the CRA issues a mid-year revision, a second row is inserted for
     * the same [taxYear] with a later [effectiveFrom].  [GetCraRateForYearUseCase]
     * resolves the correct row with:
     * ```sql
     * SELECT * FROM cra_rates
     * WHERE tax_year = :year
     * ORDER BY effective_from DESC
     * LIMIT 1
     * ```
     */
    @ColumnInfo(name = "effective_from")
    val effectiveFrom: Long,
)
