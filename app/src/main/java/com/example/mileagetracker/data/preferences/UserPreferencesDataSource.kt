package com.example.mileagetracker.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.mileagetracker.domain.model.CanadianUnits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore source for user unit and currency preferences.
 *
 * Separation of concerns: this class owns the three Canadian-defaults keys
 * (distance unit, currency code, and a one-shot "defaults applied" flag).
 * Notification / vehicle / rate keys remain in [SettingsRepositoryImpl].
 *
 * ## Keys
 * | Key                           | Type    | Default (applied at read) |
 * |-------------------------------|---------|---------------------------|
 * | `pref_distance_unit`          | String  | `"km"`                    |
 * | `pref_currency_code`          | String  | `"CAD"`                   |
 * | `pref_canadian_defaults_set`  | Boolean | `false`                   |
 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private companion object {
        val KEY_DISTANCE_UNIT = stringPreferencesKey("pref_distance_unit")
        val KEY_CURRENCY_CODE = stringPreferencesKey("pref_currency_code")
        val KEY_CANADIAN_DEFAULTS_SET = booleanPreferencesKey("pref_canadian_defaults_set")
    }

    val distanceUnit: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DISTANCE_UNIT] ?: CanadianUnits.DISTANCE_UNIT
    }

    val currencyCode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENCY_CODE] ?: CanadianUnits.CURRENCY_CODE
    }

    val canadianDefaultsApplied: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CANADIAN_DEFAULTS_SET] ?: false
    }

    suspend fun setDistanceUnit(unit: String) {
        dataStore.edit { it[KEY_DISTANCE_UNIT] = unit }
    }

    suspend fun setCurrencyCode(code: String) {
        dataStore.edit { it[KEY_CURRENCY_CODE] = code }
    }

    suspend fun markCanadianDefaultsApplied() {
        dataStore.edit { it[KEY_CANADIAN_DEFAULTS_SET] = true }
    }
}
