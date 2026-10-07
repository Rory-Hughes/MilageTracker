package com.example.mileagetracker.data.formatting

import com.example.mileagetracker.domain.model.CanadianUnits
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Kotlin formatter for kilometre values.
 * Use this in non-Compose contexts such as PDF/CSV generation.
 * In Compose UI, prefer [com.example.mileagetracker.ui.common.DistanceText].
 */
@Singleton
class CanadianDistanceFormatter @Inject constructor() {

    private val numberFormat: NumberFormat =
        NumberFormat.getInstance(CanadianUnits.CURRENCY_LOCALE).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 1
        }

    /**
     * Formats [km] as a distance string, e.g. `1,234.5 km`.
     */
    fun format(km: Double): String = "${numberFormat.format(km)} ${CanadianUnits.DISTANCE_UNIT}"
}
