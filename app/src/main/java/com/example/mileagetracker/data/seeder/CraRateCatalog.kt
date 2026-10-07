package com.example.mileagetracker.data.seeder

import com.example.mileagetracker.domain.model.CraRateRegion

/**
 * Single source of truth for CRA prescribed automobile allowance rates.
 *
 * Values are stored as cents per kilometre. The provincial rates are
 * canonical; the territorial supplement is applied when resolving a rate.
 */
object CraRateCatalog {
    const val SOURCE_URL =
        "https://www.canada.ca/en/revenue-agency/services/tax/businesses/" +
            "topics/payroll/benefits-allowances/automobile/automobile-motor-vehicle-" +
            "allowances/automobile-allowance-rates.html"

    private const val TERRITORIAL_SUPPLEMENT_CENTS = 4
    private const val STANDARD_TIER_LIMIT_KM = 5_000

    val provincialRates: List<Rate> = listOf(
        Rate(2009, 52, 46), Rate(2010, 52, 46), Rate(2011, 52, 46),
        Rate(2012, 53, 47), Rate(2013, 54, 48), Rate(2014, 54, 48),
        Rate(2015, 55, 49), Rate(2016, 54, 48), Rate(2017, 54, 48),
        Rate(2018, 55, 49), Rate(2019, 58, 52), Rate(2020, 59, 53),
        Rate(2021, 59, 53), Rate(2022, 61, 55), Rate(2023, 68, 62),
        Rate(2024, 70, 64), Rate(2025, 72, 66), Rate(2026, 73, 67),
    )

    fun resolve(year: Int, region: CraRateRegion): Rate? =
        provincialRates.firstOrNull { it.taxYear == year }?.let { provincial ->
            if (region == CraRateRegion.PROVINCES) {
                provincial
            } else {
                provincial.copy(
                    firstTier = provincial.firstTier + TERRITORIAL_SUPPLEMENT_CENTS,
                    additional = provincial.additional + TERRITORIAL_SUPPLEMENT_CENTS,
                )
            }
        }

    data class Rate(
        val taxYear: Int,
        val firstTier: Int,
        val additional: Int,
        val firstTierLimitKm: Int = STANDARD_TIER_LIMIT_KM,
    )
}
