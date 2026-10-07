package com.example.mileagetracker.data.seeder

import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.domain.repository.RateRepository
import javax.inject.Inject

/**
 * One-time seeder that populates the `cra_rates` table with verified
 * Canada Revenue Agency automobile allowance rates.
 *
 * ## Idempotency guarantee
 * [seedAll] and [seedYear] are unconditionally safe to call on every app
 * launch, upgrade, or reinstall:
 *
 * 1. **Application layer** — [RateRepository.isYearSeeded] filters out years already in
 *    the database before attempting any write.
 * 2. **Repository layer** — [RateRepository.insertRates] uses
 *    `INSERT OR IGNORE` at the SQL level, so even a concurrent duplicate
 *    insert (e.g. two coroutines racing on first install) is silently
 *    discarded rather than throwing a constraint violation.
 *
 * Both guards are independent; either alone is sufficient to prevent
 * duplicates, but both together make the guarantee robust to race
 * conditions and future refactors.
 *
 * ## Performance on subsequent launches
 * [seedAll] calls [RateRepository.isYearSeeded] once per known year.  On
 * second and subsequent launches every call returns `true` immediately
 * (a single `COUNT > 0` DB query) and no inserts are executed.  The
 * total overhead is 18 lightweight count queries, typically completing
 * in < 1 ms on modern hardware.
 *
 * ## Adding future rates
 * Append a new entry to [KNOWN_RATES] each January:
 * ```kotlin
 * RateSeed(2027, firstTier = XX, additional = XX)
 * ```
 * No other changes are required; [seedAll] will insert only the new row.
 *
 * ## Source
 * All rates are sourced from the official CRA automobile allowance page:
 * [SOURCE_URL]
 */
class CraRateSeeder @Inject constructor(
    private val rateRepository: RateRepository,
) {

    // ───────────────────────────────────────────────────────────────────────────
    // Public API
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Seeds all years in [KNOWN_RATES] that are not already present.
     *
     * On first launch: inserts all 18 rows in a single batch transaction.
     * On subsequent launches: every year is already seeded; returns 0.
     *
     * @return the number of rows actually inserted.
     */
    suspend fun seedAll(): Int {
        val unseeded = KNOWN_RATES.filter { seed ->
            !rateRepository.isYearSeeded(seed.taxYear)
        }
        if (unseeded.isEmpty()) return 0
        rateRepository.insertRates(unseeded.map { it.toEntity() })
        return unseeded.size
    }

    /**
     * Seeds a specific [taxYear] if not already present.
     *
     * @param taxYear the four-digit year to seed.
     * @return `true` if inserted, `false` if already present or unknown.
     */
    suspend fun seedYear(taxYear: Int): Boolean =
        KNOWN_RATES.firstOrNull { it.taxYear == taxYear }?.let { seed ->
            if (rateRepository.isYearSeeded(taxYear)) {
                false
            } else {
                rateRepository.insertRate(seed.toEntity())
                true
            }
        } ?: false

    /**
     * Returns the list of years in [KNOWN_RATES] that have NOT yet been
     * seeded.  Useful for diagnostics and settings screens.
     */
    suspend fun unseededYears(): List<Int> =
        KNOWN_RATES
            .map { it.taxYear }
            .filter { !rateRepository.isYearSeeded(it) }

    // ───────────────────────────────────────────────────────────────────────────
    // Compile-time rate table
    // ───────────────────────────────────────────────────────────────────────────

    companion object {
        /** Official CRA automobile allowance rates page (source of truth). */
        const val SOURCE_URL = CraRateCatalog.SOURCE_URL

        private const val STANDARD_TIER_LIMIT_KM = 5_000

        /**
         * Verified CRA automobile allowance rates, 2009–2026.
         * Source: [SOURCE_URL] — all values in cents per kilometre.
         *
         * | Year | First (¢/km) | Additional (¢/km) |
         * |------|-------------|-------------------|
         * | 2009 | 52          | 46                |
         * | 2010 | 52          | 46                |
         * | 2011 | 52          | 46                |
         * | 2012 | 53          | 47                |
         * | 2013 | 54          | 48                |
         * | 2014 | 54          | 48                |
         * | 2015 | 55          | 49                |
         * | 2016 | 54          | 48                |
         * | 2017 | 54          | 48                |
         * | 2018 | 55          | 49                |
         * | 2019 | 58          | 52                |
         * | 2020 | 59          | 53                |
         * | 2021 | 59          | 53                |
         * | 2022 | 61          | 55                |
         * | 2023 | 68          | 62                |
         * | 2024 | 70          | 64                |
         * | 2025 | 72          | 66                |
         * | 2026 | 73          | 67                |
         */
        val KNOWN_RATES: List<RateSeed> = CraRateCatalog.provincialRates.map { rate ->
            RateSeed(rate.taxYear, rate.firstTier, rate.additional)
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Internal seed model
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Lightweight compile-time data holder for a single rate entry.
     * Separate from [CraRateEntity] to keep [KNOWN_RATES] readable as
     * a plain number table without Room annotations.
     */
    data class RateSeed(
        val taxYear: Int,
        val firstTier: Int,
        val additional: Int,
    ) {
        fun toEntity(): CraRateEntity = CraRateEntity(
            taxYear = taxYear,
            firstTierRateCents = firstTier,
            firstTierLimitKm = STANDARD_TIER_LIMIT_KM,
            additionalKmRateCents = additional,
            sourceLabel = "CRA Automobile Allowance Rates $taxYear — $SOURCE_URL",
            effectiveFrom = jan1UtcMillis(taxYear),
        )

        private fun jan1UtcMillis(year: Int): Long {
            val baseYear = 1970
            var days = 0L
            for (y in baseYear until year) {
                days += if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 366L else 365L
            }
            return days * 24L * 60 * 60 * 1_000
        }
    }
}
