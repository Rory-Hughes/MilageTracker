package com.example.mileagetracker.util

import com.example.mileagetracker.data.seeder.CraRateSeeder

/**
 * Application-wide constants derived from authoritative sources so that
 * a single change propagates everywhere.
 *
 * ## Supported year range
 * [FIRST_SUPPORTED_YEAR] and [LATEST_SUPPORTED_YEAR] are derived
 * directly from [CraRateSeeder.KNOWN_RATES], making them the single
 * source of truth.  UI components that need to render a year picker or
 * validate user input should reference these constants rather than
 * hard-coding year values.
 */
object AppConstants {

    /**
     * The earliest tax year for which CRA rates are seeded.
     * Derived from [CraRateSeeder.KNOWN_RATES].
     */
    val FIRST_SUPPORTED_YEAR: Int =
        CraRateSeeder.KNOWN_RATES.minOf { it.taxYear }

    /**
     * The most recent tax year for which CRA rates are seeded.
     * Derived from [CraRateSeeder.KNOWN_RATES].
     *
     * Use this as the default selection in year-picker UI and as the
     * upper bound on fiscal year validation when the app is offline
     * (i.e. before a remote rate update is available).
     */
    val LATEST_SUPPORTED_YEAR: Int =
        CraRateSeeder.KNOWN_RATES.maxOf { it.taxYear }

    /**
     * Convenience range covering all seeded years.
     * Example use: `if (year in SUPPORTED_YEAR_RANGE) { … }`
     */
    val SUPPORTED_YEAR_RANGE: IntRange
        get() = FIRST_SUPPORTED_YEAR..LATEST_SUPPORTED_YEAR
}
