package com.example.mileagetracker.data.preferences

import com.example.mileagetracker.domain.model.CanadianUnits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user unit and currency preferences.
 *
 * Exposes reactive [Flow] accessors and a one-shot idempotent helper
 * [applyCanadianDefaultsIfNotSet] intended for use during first-run /
 * onboarding via [ApplyCanadianDefaultsUseCase].
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) {
    val distanceUnit: Flow<String> = dataSource.distanceUnit
    val currencyCode: Flow<String> = dataSource.currencyCode

    suspend fun setDistanceUnit(unit: String) = dataSource.setDistanceUnit(unit)
    suspend fun setCurrencyCode(code: String) = dataSource.setCurrencyCode(code)

    /**
     * Writes Canadian defaults (km / CAD) to DataStore only the first time
     * this is called.  A persisted boolean flag prevents re-seeding if the
     * user later changes their preferences.
     */
    suspend fun applyCanadianDefaultsIfNotSet() {
        val alreadyApplied = dataSource.canadianDefaultsApplied.first()
        if (alreadyApplied) return

        dataSource.setDistanceUnit(CanadianUnits.DISTANCE_UNIT)
        dataSource.setCurrencyCode(CanadianUnits.CURRENCY_CODE)
        dataSource.markCanadianDefaultsApplied()
    }
}
