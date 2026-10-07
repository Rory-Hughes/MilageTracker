package com.example.mileagetracker.domain.model

import com.example.mileagetracker.data.local.entity.CraRateEntity

/**
 * Clean domain model representing a single CRA per-kilometre reimbursement
 * rate for a given tax year.
 *
 * This is the domain layer's view of rate data — decoupled from Room
 * annotations and the `cra_rates` table schema.  Use-cases and ViewModels
 * should work with [CraRate], not [CraRateEntity].
 *
 * ## Field naming
 * Fields follow the Phase 5 plan spec:
 * - [tier1RateCentsPerKm]   — first-tier rate (higher, applied up to [tier1ThresholdKm])
 * - [tier2RateCentsPerKm]   — second-tier rate (lower, applied beyond threshold)
 * - [tier1ThresholdKm]      — km at which rate drops; CRA standard is 5,000 km
 * - [forTerritory]          — optional override for northern/remote territories
 *
 * ## Mapping
 * Convert a [CraRateEntity] to [CraRate] via the [CraRateEntity.toDomain]
 * extension function defined at the bottom of this file.
 *
 * ## Storage
 * Rates are stored in integer cents (e.g. `70` = $0.70/km) to avoid
 * floating-point rounding errors.  Use [tier1RateDollars] and
 * [tier2RateDollars] for display only.
 */
data class CraRate(
    /** The four-digit CRA tax year, e.g. 2026. */
    val year: Int,

    /**
     * First-tier reimbursement rate in **cents per kilometre**.
     * Applied to business km up to [tier1ThresholdKm] within the tax year.
     * Example: 73 → $0.73/km (CRA 2026 provinces).
     */
    val tier1RateCentsPerKm: Int,

    /**
     * Second-tier reimbursement rate in **cents per kilometre**.
     * Applied to every business km beyond [tier1ThresholdKm].
     * Example: 67 → $0.67/km (CRA 2026 provinces).
     */
    val tier2RateCentsPerKm: Int,

    /**
     * The kilometre threshold at which the rate steps down from
     * [tier1RateCentsPerKm] to [tier2RateCentsPerKm].
     *
     * The CRA standard has been 5,000 km since 2009.  Stored explicitly
     * so a future CRA change does not require a code change.
     */
    val tier1ThresholdKm: Int,

    /**
     * Optional territory identifier for northern/remote rate overrides.
     * `null` for the standard national rate.
     *
     * Examples: `"NT"` (Northwest Territories), `"YT"` (Yukon),
     * `"NU"` (Nunavut).  Future use — reserved per Phase 5 plan spec.
     */
    val forTerritory: String? = null,
) {
    // ── Display helpers ────────────────────────────────────────────────────────

    /** First-tier rate in dollars, for display only. */
    val tier1RateDollars: Double get() = tier1RateCentsPerKm / 100.0

    /** Second-tier rate in dollars, for display only. */
    val tier2RateDollars: Double get() = tier2RateCentsPerKm / 100.0

    /**
     * Returns a human-readable rate string, e.g.
     * `"$0.73 / km (first 5,000 km), $0.67 / km thereafter"`.
     */
    fun displayLabel(): String =
        "$${"%.2f".format(tier1RateDollars)} / km " +
            "(first %,d km), ".format(tier1ThresholdKm) +
            "$${"%.2f".format(tier2RateDollars)} / km thereafter"
}

// ── Mapping extension ─────────────────────────────────────────────────────────

/**
 * Maps a [CraRateEntity] (Room/data layer) to a [CraRate] (domain layer).
 *
 * Column name alignment:
 * | Entity field              | Domain field              |
 * |---------------------------|---------------------------|
 * | taxYear                   | year                      |
 * | firstTierRateCents        | tier1RateCentsPerKm       |
 * | additionalKmRateCents     | tier2RateCentsPerKm       |
 * | firstTierLimitKm          | tier1ThresholdKm          |
 * | (no entity field)         | forTerritory = null       |
 *
 * [forTerritory] defaults to `null`; a future northern-rate entity field
 * can be plumbed through here without changing callers.
 */
fun CraRateEntity.toDomain(): CraRate = CraRate(
    year = taxYear,
    tier1RateCentsPerKm = firstTierRateCents,
    tier2RateCentsPerKm = additionalKmRateCents,
    tier1ThresholdKm = firstTierLimitKm,
    forTerritory = null,
)
