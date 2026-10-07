package com.example.mileagetracker.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.example.mileagetracker.domain.model.AppLanguage
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.ui.viewmodel.CraRateDiagnosticsUiState
import com.example.mileagetracker.ui.viewmodel.SettingsViewModel

data class SettingsScreenActions(
    val onNavigateToClassificationRules: () -> Unit = {},
    val onNavigateToExpenses: (vehicleId: Int, year: Int) -> Unit = { _, _ -> },
    val onNavigateToPrivacyDashboard: () -> Unit = {},
    val onNavigateToIntegrations: () -> Unit = {},
    val showIntegrations: Boolean = false,
)

internal data class SettingsCallbacks(
    val onLanguageSelected: (AppLanguage) -> Unit,
    val onDefaultVehicleSelected: (Int?) -> Unit,
    val onRateModeChange: (RateMode) -> Unit,
    val onCraRateRegionSelected: (CraRateRegion) -> Unit,
    val onCustomRateInputChanged: (String) -> Unit,
    val onApplyCustomRate: () -> Unit,
    val onTripStartReminderChanged: (Boolean) -> Unit,
    val onWeeklySummaryChanged: (Boolean) -> Unit,
    val onOdometerPhotoReminderChanged: (Boolean) -> Unit,
    val onAutoDetectSensitivityChanged: (Float) -> Unit,
    val onCloudUploadConsentChanged: (Boolean) -> Unit,
    val onOpenCraSource: () -> Unit,
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    actions: SettingsScreenActions = SettingsScreenActions(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.settingsState.collectAsStateWithLifecycle()
    val craRateDiagnosticsState by viewModel.craRateDiagnosticsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val callbacks = rememberSettingsCallbacks(
        viewModel = viewModel,
        craRateDiagnosticsState = craRateDiagnosticsState,
        onOpenSource = { sourceUrl ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)))
        },
    )
    val permissionState = rememberSettingsPermissionState(
        state = state,
        onAutomaticTrackingEnabledChange = viewModel.setAutomaticTrackingEnabled,
        onCalendarHintsEnabledChange = viewModel.setCalendarHintsEnabled,
    )

    LaunchedEffect(Unit) {
        viewModel.refreshAutomaticTrackingState()
        viewModel.refreshCraRateDiagnostics()
    }

    SettingsScreenContent(
        state = state,
        craRateDiagnosticsState = craRateDiagnosticsState,
        navigationActions = actions,
        callbacks = callbacks,
        permissionState = permissionState,
    )
}

private fun rememberSettingsCallbacks(
    viewModel: SettingsViewModel,
    craRateDiagnosticsState: CraRateDiagnosticsUiState,
    onOpenSource: (String) -> Unit,
): SettingsCallbacks = SettingsCallbacks(
    onLanguageSelected = viewModel.setLanguage,
    onDefaultVehicleSelected = viewModel.setDefaultVehicle,
    onRateModeChange = viewModel.setRateMode,
    onCraRateRegionSelected = viewModel.setCraRateRegion,
    onCustomRateInputChanged = viewModel.onCustomRateInputChanged,
    onApplyCustomRate = viewModel.applyCustomRate,
    onTripStartReminderChanged = viewModel.setTripStartReminder,
    onWeeklySummaryChanged = viewModel.setWeeklySummary,
    onOdometerPhotoReminderChanged = viewModel.setOdometerPhotoReminder,
    onAutoDetectSensitivityChanged = viewModel.setAutoDetectSensitivity,
    onCloudUploadConsentChanged = viewModel.setCloudUploadConsentGranted,
    onOpenCraSource = {
        craRateDiagnosticsState.diagnostics?.sourceUrl?.let(onOpenSource)
    },
)
