package com.example.mileagetracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.AppLanguage
import com.example.mileagetracker.domain.model.CloudUploadConsent
import com.example.mileagetracker.domain.model.CraRateDiagnostics
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.localization.GetLanguagePreferenceUseCase
import com.example.mileagetracker.domain.usecase.localization.ResetLanguagePreferenceUseCase
import com.example.mileagetracker.domain.usecase.localization.SetLanguagePreferenceUseCase
import com.example.mileagetracker.domain.usecase.rate.GetCraRateDiagnosticsUseCase
import com.example.mileagetracker.domain.usecase.vehicle.GetActiveVehiclesUseCase
import com.example.mileagetracker.domain.usecase.vehicle.SetDefaultVehicleUseCase
import com.example.mileagetracker.service.ActivityTransitionRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for [SettingsScreen].
 *
 * Combines multiple [SettingsRepository] flows into a single [SettingsUiState]
 * snapshot. The standard `combine()` overload supports at most 5 flows;
 * additional flows are layered via a second `combine()` call that wraps the
 * first into a [TopSettings] holder.
 *
 * [calendarHintsEnabled] is plumbed through a third combine tier
 * ([BottomSettings]) so all state can remain in a single [settingsState]
 * [StateFlow].
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val getActiveVehiclesUseCase: GetActiveVehiclesUseCase,
    private val setDefaultVehicleUseCase: SetDefaultVehicleUseCase,
    private val getLanguagePreferenceUseCase: GetLanguagePreferenceUseCase,
    private val setLanguagePreferenceUseCase: SetLanguagePreferenceUseCase,
    private val resetLanguagePreferenceUseCase: ResetLanguagePreferenceUseCase,
    private val getCraRateDiagnosticsUseCase: GetCraRateDiagnosticsUseCase,
    currentUserProvider: CurrentUserProvider,
    @param:ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val ownerUid = currentUserProvider.requireUid()
    private val fiscalYear = LocalDate.now().year

    private val _customRateInput = MutableStateFlow("")
    private val _customRateError = MutableStateFlow<String?>(null)

    // Pair the raw input and its validation error into one flow so the error is
    // reactively folded into [settingsState].
    private val _customRateFlow = combine(
        _customRateInput,
        _customRateError,
    ) { input, error -> CustomRateState(input, error) }
    private val _automaticTrackingEnabled = MutableStateFlow(false)
    private val _automaticTrackingRegistered = MutableStateFlow(false)
    private val _automaticTrackingFailure = MutableStateFlow<String?>(null)
    private val _craRateDiagnosticsState = MutableStateFlow(CraRateDiagnosticsUiState())
    private val activityTransitionRegistrar by lazy { ActivityTransitionRegistrar(appContext) }
    val craRateDiagnosticsState: StateFlow<CraRateDiagnosticsUiState> = _craRateDiagnosticsState

    private val _topFlow = combine(
        getLanguagePreferenceUseCase(),
        settingsRepository.getRateMode(),
        settingsRepository.getCustomRateDollars(),
        settingsRepository.getDefaultVehicleId(),
        settingsRepository.getTripStartReminder(),
    ) { language, rateMode, customRate, defaultVehicleId, tripReminder ->
        TopSettings(language, rateMode, customRate, defaultVehicleId, tripReminder)
    }

    private val _notificationFlow = combine(
        settingsRepository.getWeeklySummary(),
        settingsRepository.getOdometerPhotoReminder(),
    ) { weeklySummary, odometerReminder ->
        NotificationSettings(weeklySummary, odometerReminder)
    }

    private val _bottomFlow = combine(
        _topFlow,
        _notificationFlow,
        settingsRepository.getAutoDetectSensitivity(),
        getActiveVehiclesUseCase(ownerUid, fiscalYear),
        _customRateFlow,
    ) { top, notifications, sensitivity, vehicles, customRate ->
        BottomSettings(top, notifications, sensitivity, vehicles, customRate)
    }

    private val _trackingStatusFlow = combine(
        _automaticTrackingEnabled,
        _automaticTrackingRegistered,
        _automaticTrackingFailure,
    ) { enabled, registered, failure ->
        TrackingStatus(enabled, registered, failure)
    }

    val settingsState: StateFlow<SettingsUiState> = combine(
        _bottomFlow,
        settingsRepository.getCalendarHintsEnabled(),
        settingsRepository.getCraRateRegion(),
        settingsRepository.getCloudUploadConsentVersion(),
        _trackingStatusFlow,
    ) { bottom, calendarHints, craRateRegion, cloudUploadConsentVersion, tracking ->
        SettingsUiState(
            selectedLanguage = bottom.top.language,
            rateMode = bottom.top.rateMode,
            craRateRegion = craRateRegion,
            customRateDollars = bottom.top.customRate,
            customRateInput = bottom.customRate.input.ifEmpty { "%.2f".format(bottom.top.customRate) },
            customRateError = bottom.customRate.error,
            defaultVehicleId = bottom.top.defaultVehicleId,
            activeVehicles = bottom.vehicles,
            tripStartReminderEnabled = bottom.top.tripReminder,
            weeklySummaryEnabled = bottom.notifications.weeklySummary,
            odometerPhotoReminderEnabled = bottom.notifications.odometerReminder,
            autoDetectSensitivity = bottom.sensitivity,
            automaticTrackingEnabled = tracking.enabled,
            automaticTrackingRegistered = tracking.registered,
            automaticTrackingFailure = tracking.failure,
            calendarHintsEnabled = calendarHints,
            cloudUploadConsentGranted =
                cloudUploadConsentVersion == CloudUploadConsent.CURRENT_VERSION,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    val setLanguage: (AppLanguage) -> Unit = { language ->
        viewModelScope.launch {
            if (language == AppLanguage.SYSTEM_DEFAULT) {
                resetLanguagePreferenceUseCase()
            } else {
                setLanguagePreferenceUseCase(language)
            }
        }
    }

    val setRateMode: (RateMode) -> Unit = { mode ->
        viewModelScope.launch { settingsRepository.setRateMode(mode) }
    }

    val setCraRateRegion: (CraRateRegion) -> Unit = { region ->
        viewModelScope.launch { settingsRepository.setCraRateRegion(region) }
    }

    val onCustomRateInputChanged: (String) -> Unit = { raw ->
        _customRateInput.value = raw
        _customRateError.value = validateRate(raw)
    }

    val applyCustomRate: () -> Unit = {
        val raw = _customRateInput.value
        val error = validateRate(raw)
        _customRateError.value = error
        val rate = raw.toFloatOrNull()
        if (error == null && rate != null) {
            viewModelScope.launch { settingsRepository.setCustomRateDollars(rate) }
        }
    }

    val setDefaultVehicle: (Int?) -> Unit = { vehicleId ->
        viewModelScope.launch {
            settingsRepository.setDefaultVehicleId(vehicleId)
            if (vehicleId != null) {
                runCatching {
                    setDefaultVehicleUseCase(
                        vehicleId = vehicleId,
                        ownerUid = ownerUid,
                        fiscalYear = fiscalYear,
                    )
                }
            }
        }
    }

    val setTripStartReminder: (Boolean) -> Unit = { enabled ->
        viewModelScope.launch { settingsRepository.setTripStartReminder(enabled) }
    }

    val setWeeklySummary: (Boolean) -> Unit = { enabled ->
        viewModelScope.launch { settingsRepository.setWeeklySummary(enabled) }
    }

    val setOdometerPhotoReminder: (Boolean) -> Unit = { enabled ->
        viewModelScope.launch { settingsRepository.setOdometerPhotoReminder(enabled) }
    }

    val setAutoDetectSensitivity: (Float) -> Unit = { value ->
        viewModelScope.launch {
            settingsRepository.setAutoDetectSensitivity(value.coerceIn(0f, 1f))
        }
    }

    val setAutomaticTrackingEnabled: (Boolean) -> Unit = { enabled ->
        viewModelScope.launch {
            activityTransitionRegistrar.setAutomaticTrackingEnabled(enabled)
            refreshAutomaticTrackingState()
            if (enabled) {
                delay(750)
                refreshAutomaticTrackingState()
            }
        }
    }

    val refreshAutomaticTrackingState: () -> Unit = {
        _automaticTrackingEnabled.value = activityTransitionRegistrar.isEnabled()
        _automaticTrackingRegistered.value = activityTransitionRegistrar.isRegistered()
        _automaticTrackingFailure.value = activityTransitionRegistrar.registrationFailure()
    }

    val setCalendarHintsEnabled: (Boolean) -> Unit = { enabled ->
        viewModelScope.launch { settingsRepository.setCalendarHintsEnabled(enabled) }
    }

    val setCloudUploadConsentGranted: (Boolean) -> Unit = { granted ->
        viewModelScope.launch { settingsRepository.setCloudUploadConsentGranted(granted) }
    }

    val refreshCraRateDiagnostics: () -> Unit = {
        viewModelScope.launch {
            _craRateDiagnosticsState.value = CraRateDiagnosticsUiState(isLoading = true)
            _craRateDiagnosticsState.value = runCatching {
                CraRateDiagnosticsUiState(diagnostics = getCraRateDiagnosticsUseCase())
            }.getOrElse { error ->
                CraRateDiagnosticsUiState(
                    error = error.message ?: "Unable to load CRA rate status.",
                )
            }
        }
    }

    init {
        refreshAutomaticTrackingState()
        refreshCraRateDiagnostics()
    }

    private fun validateRate(raw: String): String? {
        val value = raw.toFloatOrNull()
        return when {
            raw.isBlank() -> "Enter a rate"
            value == null -> "Must be a number (e.g. 0.70)"
            value <= 0f -> "Rate must be greater than 0"
            value > 10f -> "Rate seems too high \u2014 enter $/km (e.g. 0.70)"
            else -> null
        }
    }

    private data class TopSettings(
        val language: AppLanguage,
        val rateMode: RateMode,
        val customRate: Float,
        val defaultVehicleId: Int?,
        val tripReminder: Boolean,
    )

    private data class NotificationSettings(
        val weeklySummary: Boolean,
        val odometerReminder: Boolean,
    )

    private data class BottomSettings(
        val top: TopSettings,
        val notifications: NotificationSettings,
        val sensitivity: Float,
        val vehicles: List<VehicleEntity>,
        val customRate: CustomRateState,
    )

    private data class CustomRateState(
        val input: String,
        val error: String?,
    )

    private data class TrackingStatus(
        val enabled: Boolean,
        val registered: Boolean,
        val failure: String?,
    )
}

data class CraRateDiagnosticsUiState(
    val isLoading: Boolean = false,
    val diagnostics: CraRateDiagnostics? = null,
    val error: String? = null,
)
