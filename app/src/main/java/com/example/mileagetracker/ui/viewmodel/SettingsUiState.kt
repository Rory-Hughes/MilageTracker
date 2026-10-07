package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.AppLanguage
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode

/**
 * All user-configurable preferences loaded from [SettingsRepository],
 * assembled into a single snapshot for [SettingsScreen].
 *
 * ## Task 7.4 addition
 * [calendarHintsEnabled] mirrors the `calendar_hints_enabled` DataStore key.
 * When false, [TripDetailViewModel] skips the [GetCalendarHintsForTripUseCase]
 * call entirely so the banner never appears and no calendar query is made.
 */
data class SettingsUiState(
    val selectedLanguage: AppLanguage = AppLanguage.SYSTEM_DEFAULT,
    val rateMode: RateMode = RateMode.CRA_STANDARD,
    val craRateRegion: CraRateRegion? = null,
    val customRateDollars: Float = 0.70f,
    val customRateInput: String = "0.70",
    val customRateError: String? = null,
    val defaultVehicleId: Int? = null,
    val activeVehicles: List<VehicleEntity> = emptyList(),
    val tripStartReminderEnabled: Boolean = true,
    val weeklySummaryEnabled: Boolean = true,
    val odometerPhotoReminderEnabled: Boolean = true,
    val autoDetectSensitivity: Float = 0.5f,
    val automaticTrackingEnabled: Boolean = false,
    val automaticTrackingRegistered: Boolean = false,
    val automaticTrackingFailure: String? = null,
    // ── Task 7.4 ──────────────────────────────────────────────────────────
    /** When false the calendar-hint banner is suppressed on TripDetailScreen. */
    val calendarHintsEnabled: Boolean = true,
    val cloudUploadConsentGranted: Boolean = false,
)
