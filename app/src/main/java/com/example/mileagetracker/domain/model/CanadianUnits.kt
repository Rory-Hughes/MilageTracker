package com.example.mileagetracker.domain.model

import java.util.Locale

/**
 * App-wide defaults for Canadian units and currency.
 * Reference this object wherever distance, volume, or currency constants are needed
 * so there is a single authoritative source of truth.
 */
object CanadianUnits {
    const val DISTANCE_UNIT: String = "km"
    const val VOLUME_UNIT: String = "L"
    const val CURRENCY_CODE: String = "CAD"
    val CURRENCY_LOCALE: Locale = Locale.CANADA
}

/**
 * Convenience alias so call sites can write `AppDefaults.DISTANCE_UNIT` etc.
 */
typealias AppDefaults = CanadianUnits
