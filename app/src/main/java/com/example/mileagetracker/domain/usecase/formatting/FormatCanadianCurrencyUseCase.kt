package com.example.mileagetracker.domain.usecase.formatting

import com.example.mileagetracker.data.formatting.CanadianCurrencyFormatter
import javax.inject.Inject

/**
 * Use case that converts a raw [Double] amount into a locale-aware CAD string.
 *
 * Example:
 * ```kotlin
 * val label = formatCurrency(1234.5) // "$1,234.50"
 * ```
 */
class FormatCanadianCurrencyUseCase @Inject constructor(
    private val formatter: CanadianCurrencyFormatter
) {
    operator fun invoke(amount: Double): String = formatter.format(amount)
}
