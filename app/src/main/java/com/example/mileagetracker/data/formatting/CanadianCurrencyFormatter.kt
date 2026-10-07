package com.example.mileagetracker.data.formatting

import com.example.mileagetracker.domain.model.CanadianUnits
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure Kotlin formatter for CAD amounts.
 * Use this in non-Compose contexts such as PDF/CSV generation.
 * In Compose UI, prefer [com.example.mileagetracker.ui.common.CurrencyText].
 */
@Singleton
class CanadianCurrencyFormatter @Inject constructor() {

    private val formatter: NumberFormat =
        NumberFormat.getCurrencyInstance(CanadianUnits.CURRENCY_LOCALE)

    /**
     * Formats [amount] as a CAD currency string, e.g. `$1,234.56`.
     */
    fun format(amount: Double): String = formatter.format(amount)
}
