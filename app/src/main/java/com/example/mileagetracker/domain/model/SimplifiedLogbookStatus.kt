package com.example.mileagetracker.domain.model

/**
 * Eligibility state and progress data for the CRA simplified logbook method.
 *
 * ## CRA Simplified Logbook Rules
 * A taxpayer may use the simplified method if they have:
 *   1. Maintained a **full base-year logbook** for a complete 12-month period
 *      that establishes a business-use percentage.
 *   2. In subsequent years, kept a **sample logbook** for a minimum continuous
 *      3-month period whose business-use ratio is within 10% of the base-year ratio.
 *
 * If the sample year ratio differs from the base year by more than 10 percentage
 * points, a new full base-year logbook must be maintained.
 *
 * ## Fields
 *
 * @property isEligible                `true` if the user has at least one full
 *                                     prior year with ≥ [MIN_BASE_YEAR_TRIPS] trips.
 * @property baseYear                  The tax year selected (or auto-detected) as
 *                                     the base year, or `null` if ineligible.
 * @property baseYearBusinessUsePercent Business-use % from the base year logbook,
 *                                     or `null` if ineligible.
 * @property baseYearTripCount         Total trips recorded in the base year.
 * @property currentSampleKm          Business km logged so far in the current
 *                                     sample period (current calendar year).
 * @property requiredSampleKm         Estimated km target for a representative
 *                                     3-month sample (25% of base-year business km).
 * @property sampleProgressPercent    [currentSampleKm] / [requiredSampleKm] × 100,
 *                                     capped at 100.0.
 * @property availableBaseYears        All full years available to select as base year;
 *                                     populated only when multiple exist.
 * @property guidanceText              Plain-language summary surfaced directly in the UI.
 */
data class SimplifiedLogbookStatus(
    val isEligible: Boolean,
    val baseYear: Int?,
    val baseYearBusinessUsePercent: Double?,
    val baseYearTripCount: Int,
    val currentSampleKm: Double,
    val requiredSampleKm: Double,
    val sampleProgressPercent: Double,
    val availableBaseYears: List<Int>,
    val guidanceText: String,
) {
    companion object {
        /** Minimum trips in a calendar year for it to qualify as a full base year. */
        const val MIN_BASE_YEAR_TRIPS = 50

        /**
         * CRA tolerance: if the sample year's business-use % differs from
         * the base year by more than this amount, a new full logbook is required.
         */
        const val MAX_RATIO_DEVIATION_PERCENT = 10.0

        /** Returns an ineligible status with a prompt to build a full base year. */
        fun ineligible(currentSampleKm: Double = 0.0): SimplifiedLogbookStatus =
            SimplifiedLogbookStatus(
                isEligible = false,
                baseYear = null,
                baseYearBusinessUsePercent = null,
                baseYearTripCount = 0,
                currentSampleKm = currentSampleKm,
                requiredSampleKm = 0.0,
                sampleProgressPercent = 0.0,
                availableBaseYears = emptyList(),
                guidanceText = "You need a full 12-month logbook with at " +
                    "least $MIN_BASE_YEAR_TRIPS trips before you can use the " +
                    "simplified method. Keep recording every trip this year.",
            )
    }
}
