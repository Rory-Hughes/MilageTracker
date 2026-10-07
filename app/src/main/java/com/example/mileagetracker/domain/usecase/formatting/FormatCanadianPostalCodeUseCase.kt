package com.example.mileagetracker.domain.usecase.formatting

import com.example.mileagetracker.data.formatting.PostalCodeFormatter
import javax.inject.Inject

/**
 * Accepts a raw postal code string and returns it normalized to `A1A 1A1` format,
 * or `null` if the input cannot be normalized.
 */
class FormatCanadianPostalCodeUseCase @Inject constructor() {
    operator fun invoke(raw: String): String? = PostalCodeFormatter.format(raw)
}
