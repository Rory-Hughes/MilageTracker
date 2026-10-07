package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import kotlinx.coroutines.flow.Flow

interface RateSettingsRepository {
    fun getRateMode(): Flow<RateMode>

    suspend fun setRateMode(mode: RateMode)

    fun getCraRateRegion(): Flow<CraRateRegion?>

    suspend fun setCraRateRegion(region: CraRateRegion)

    fun getCustomRateDollars(): Flow<Float>

    suspend fun setCustomRateDollars(rate: Float)
}

interface VehicleDefaultSettingsRepository {
    fun getDefaultVehicleId(): Flow<Int?>

    suspend fun setDefaultVehicleId(vehicleId: Int?)
}

interface NotificationSettingsRepository {
    fun getTripStartReminder(): Flow<Boolean>

    suspend fun setTripStartReminder(enabled: Boolean)

    fun getWeeklySummary(): Flow<Boolean>

    suspend fun setWeeklySummary(enabled: Boolean)

    fun getOdometerPhotoReminder(): Flow<Boolean>

    suspend fun setOdometerPhotoReminder(enabled: Boolean)
}

interface TrackingSettingsRepository {
    fun getAutoDetectSensitivity(): Flow<Float>

    suspend fun setAutoDetectSensitivity(value: Float)
}

interface ClassificationSettingsRepository {
    fun getDisabledRules(): Flow<Set<String>>

    suspend fun setRuleEnabled(ruleName: String, enabled: Boolean)
}

interface PrivacySettingsRepository {
    fun getCalendarHintsEnabled(): Flow<Boolean>

    suspend fun setCalendarHintsEnabled(enabled: Boolean)

    fun getCloudUploadConsentVersion(): Flow<Int>

    suspend fun setCloudUploadConsentGranted(granted: Boolean)
}

/**
 * Domain contract for user-configurable app preferences.
 *
 * Reads are reactive [Flow] values backed by DataStore. The combined interface
 * preserves existing injection points while smaller parent contracts keep each
 * preference area narrow and easier to test.
 */
interface SettingsRepository :
    RateSettingsRepository,
    VehicleDefaultSettingsRepository,
    NotificationSettingsRepository,
    TrackingSettingsRepository,
    ClassificationSettingsRepository,
    PrivacySettingsRepository
