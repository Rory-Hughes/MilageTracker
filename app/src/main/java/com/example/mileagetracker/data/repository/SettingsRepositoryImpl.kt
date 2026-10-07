package com.example.mileagetracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.mileagetracker.domain.model.CloudUploadConsent
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.repository.ClassificationSettingsRepository
import com.example.mileagetracker.domain.repository.NotificationSettingsRepository
import com.example.mileagetracker.domain.repository.PrivacySettingsRepository
import com.example.mileagetracker.domain.repository.RateSettingsRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.repository.TrackingSettingsRepository
import com.example.mileagetracker.domain.repository.VehicleDefaultSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    dataStore: DataStore<Preferences>,
) : SettingsRepository,
    RateSettingsRepository by DataStoreRateSettingsRepository(dataStore),
    VehicleDefaultSettingsRepository by DataStoreVehicleDefaultSettingsRepository(dataStore),
    NotificationSettingsRepository by DataStoreNotificationSettingsRepository(dataStore),
    TrackingSettingsRepository by DataStoreTrackingSettingsRepository(dataStore),
    ClassificationSettingsRepository by DataStoreClassificationSettingsRepository(dataStore),
    PrivacySettingsRepository by DataStorePrivacySettingsRepository(dataStore)

private class DataStoreRateSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : RateSettingsRepository {
    override fun getRateMode(): Flow<RateMode> =
        dataStore.data.map { prefs ->
            val raw = prefs[SettingsPreferenceKeys.RATE_MODE] ?: SettingsPreferenceKeys.DEFAULT_RATE_MODE
            runCatching { RateMode.valueOf(raw) }.getOrDefault(RateMode.CRA_STANDARD)
        }

    override suspend fun setRateMode(mode: RateMode) {
        dataStore.edit { it[SettingsPreferenceKeys.RATE_MODE] = mode.name }
    }

    override fun getCraRateRegion(): Flow<CraRateRegion?> =
        dataStore.data.map { prefs ->
            prefs[SettingsPreferenceKeys.CRA_RATE_REGION]?.let { raw ->
                runCatching { CraRateRegion.valueOf(raw) }.getOrNull()
            }
        }

    override suspend fun setCraRateRegion(region: CraRateRegion) {
        dataStore.edit { it[SettingsPreferenceKeys.CRA_RATE_REGION] = region.name }
    }

    override fun getCustomRateDollars(): Flow<Float> =
        dataStore.data.map { prefs ->
            prefs[SettingsPreferenceKeys.CUSTOM_RATE_DOLLARS]
                ?: SettingsPreferenceKeys.DEFAULT_CUSTOM_RATE_DOLLARS
        }

    override suspend fun setCustomRateDollars(rate: Float) {
        require(rate > 0f) { "Custom rate must be positive; got $rate" }
        dataStore.edit { it[SettingsPreferenceKeys.CUSTOM_RATE_DOLLARS] = rate }
    }
}

private class DataStoreVehicleDefaultSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : VehicleDefaultSettingsRepository {
    override fun getDefaultVehicleId(): Flow<Int?> =
        dataStore.data.map { prefs ->
            prefs[SettingsPreferenceKeys.DEFAULT_VEHICLE_ID]
                ?.takeIf { it != SettingsPreferenceKeys.NO_VEHICLE }
        }

    override suspend fun setDefaultVehicleId(vehicleId: Int?) {
        dataStore.edit {
            it[SettingsPreferenceKeys.DEFAULT_VEHICLE_ID] =
                vehicleId ?: SettingsPreferenceKeys.NO_VEHICLE
        }
    }
}

private class DataStoreNotificationSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : NotificationSettingsRepository {
    override fun getTripStartReminder(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SettingsPreferenceKeys.TRIP_START_REMINDER] ?: true }

    override suspend fun setTripStartReminder(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.TRIP_START_REMINDER] = enabled }
    }

    override fun getWeeklySummary(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SettingsPreferenceKeys.WEEKLY_SUMMARY] ?: true }

    override suspend fun setWeeklySummary(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.WEEKLY_SUMMARY] = enabled }
    }

    override fun getOdometerPhotoReminder(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SettingsPreferenceKeys.ODOMETER_PHOTO_REMINDER] ?: true }

    override suspend fun setOdometerPhotoReminder(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.ODOMETER_PHOTO_REMINDER] = enabled }
    }
}

private class DataStoreTrackingSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : TrackingSettingsRepository {
    override fun getAutoDetectSensitivity(): Flow<Float> =
        dataStore.data.map { prefs ->
            prefs[SettingsPreferenceKeys.AUTO_DETECT_SENSITIVITY]
                ?: SettingsPreferenceKeys.DEFAULT_SENSITIVITY
        }

    override suspend fun setAutoDetectSensitivity(value: Float) {
        dataStore.edit {
            it[SettingsPreferenceKeys.AUTO_DETECT_SENSITIVITY] = value.coerceIn(0f, 1f)
        }
    }
}

private class DataStoreClassificationSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : ClassificationSettingsRepository {
    override fun getDisabledRules(): Flow<Set<String>> =
        dataStore.data.map { prefs ->
            prefs[SettingsPreferenceKeys.DISABLED_RULES] ?: emptySet()
        }

    override suspend fun setRuleEnabled(ruleName: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[SettingsPreferenceKeys.DISABLED_RULES] ?: emptySet()
            prefs[SettingsPreferenceKeys.DISABLED_RULES] = if (enabled) {
                current - ruleName
            } else {
                current + ruleName
            }
        }
    }
}

private class DataStorePrivacySettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : PrivacySettingsRepository {
    override fun getCalendarHintsEnabled(): Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SettingsPreferenceKeys.CALENDAR_HINTS_ENABLED] ?: true }

    override suspend fun setCalendarHintsEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.CALENDAR_HINTS_ENABLED] = enabled }
    }

    override fun getCloudUploadConsentVersion(): Flow<Int> =
        dataStore.data.map { prefs -> prefs[SettingsPreferenceKeys.CLOUD_UPLOAD_CONSENT_VERSION] ?: 0 }

    override suspend fun setCloudUploadConsentGranted(granted: Boolean) {
        dataStore.edit {
            it[SettingsPreferenceKeys.CLOUD_UPLOAD_CONSENT_VERSION] =
                if (granted) CloudUploadConsent.CURRENT_VERSION else 0
        }
    }
}

private object SettingsPreferenceKeys {
    val RATE_MODE = stringPreferencesKey("rate_mode")
    val CRA_RATE_REGION = stringPreferencesKey("cra_rate_region")
    val CUSTOM_RATE_DOLLARS = floatPreferencesKey("custom_rate_dollars")
    val DEFAULT_VEHICLE_ID = intPreferencesKey("default_vehicle_id")
    val TRIP_START_REMINDER = booleanPreferencesKey("trip_start_reminder")
    val WEEKLY_SUMMARY = booleanPreferencesKey("weekly_summary")
    val ODOMETER_PHOTO_REMINDER = booleanPreferencesKey("odometer_photo_reminder")
    val AUTO_DETECT_SENSITIVITY = floatPreferencesKey("auto_detect_sensitivity")
    val DISABLED_RULES = stringSetPreferencesKey("disabled_classification_rules")
    val CALENDAR_HINTS_ENABLED = booleanPreferencesKey("calendar_hints_enabled")
    val CLOUD_UPLOAD_CONSENT_VERSION = intPreferencesKey("cloud_upload_consent_version")

    const val DEFAULT_RATE_MODE = "CRA_STANDARD"
    const val DEFAULT_CUSTOM_RATE_DOLLARS = 0.70f
    const val NO_VEHICLE = -1
    const val DEFAULT_SENSITIVITY = 0.5f
}
