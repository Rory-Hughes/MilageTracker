package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.mileagetracker.R
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.CraRateDiagnosticsUiState
import com.example.mileagetracker.ui.viewmodel.SettingsUiState
import java.time.LocalDate

@Composable
internal fun SettingsScreenContent(
    state: SettingsUiState,
    craRateDiagnosticsState: CraRateDiagnosticsUiState,
    navigationActions: SettingsScreenActions,
    callbacks: SettingsCallbacks,
    permissionState: SettingsPermissionState,
) {
    CalendarPermissionRationaleDialog(permissionState.calendar)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        LanguageSettingsSection(state, callbacks)
        VehicleSettingsSection(state, callbacks)
        RateSettingsSection(state, craRateDiagnosticsState, callbacks)
        NotificationSettingsSection(state, callbacks)
        TrackingSettingsSection(state, permissionState.tracking, callbacks)
        PrivacySettingsSection(state, navigationActions, callbacks)
        SmartClassificationSection(state, permissionState.calendar)
        ClassificationNavigationSection(navigationActions)
        ExpensesAndIntegrationsSection(state, navigationActions)
    }
}

@Composable
private fun LanguageSettingsSection(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
) {
    SettingsSection(title = stringResource(R.string.settings_section_language)) {
        com.example.mileagetracker.ui.component.LanguagePicker(
            selectedLanguage = state.selectedLanguage,
            onLanguageSelected = callbacks.onLanguageSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.settings_language_change_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VehicleSettingsSection(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
) {
    SettingsSection(title = stringResource(R.string.settings_section_vehicle)) {
        VehicleDefaultPicker(
            state = state,
            onSelect = callbacks.onDefaultVehicleSelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RateSettingsSection(
    state: SettingsUiState,
    craRateDiagnosticsState: CraRateDiagnosticsUiState,
    callbacks: SettingsCallbacks,
) {
    SettingsSection(title = stringResource(R.string.settings_section_rate)) {
        RateModeSection(
            state = state,
            diagnosticsState = craRateDiagnosticsState,
            actions = RateModeActions(
                onRateModeChange = callbacks.onRateModeChange,
                onRegionChange = callbacks.onCraRateRegionSelected,
                onInputChange = callbacks.onCustomRateInputChanged,
                onApply = callbacks.onApplyCustomRate,
                onOpenSource = callbacks.onOpenCraSource,
            ),
        )
    }
}

@Composable
private fun NotificationSettingsSection(
    state: SettingsUiState,
    callbacks: SettingsCallbacks,
) {
    SettingsSection(title = stringResource(R.string.settings_section_notifications)) {
        SwitchRow(
            label = stringResource(R.string.settings_notif_trip_label),
            subtitle = stringResource(R.string.settings_notif_trip_subtitle),
            checked = state.tripStartReminderEnabled,
            onCheckedChange = callbacks.onTripStartReminderChanged,
        )
        Spacer(Modifier.height(Spacing.xs))
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
        Spacer(Modifier.height(Spacing.xs))
        SwitchRow(
            label = stringResource(R.string.settings_notif_weekly_label),
            subtitle = stringResource(R.string.settings_notif_weekly_subtitle),
            checked = state.weeklySummaryEnabled,
            onCheckedChange = callbacks.onWeeklySummaryChanged,
        )
        Spacer(Modifier.height(Spacing.xs))
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
        Spacer(Modifier.height(Spacing.xs))
        SwitchRow(
            label = stringResource(R.string.settings_notif_odometer_label),
            subtitle = stringResource(R.string.settings_notif_odometer_subtitle),
            checked = state.odometerPhotoReminderEnabled,
            onCheckedChange = callbacks.onOdometerPhotoReminderChanged,
        )
    }
}

@Composable
private fun TrackingSettingsSection(
    state: SettingsUiState,
    trackingPermissionState: TrackingPermissionUiState,
    callbacks: SettingsCallbacks,
) {
    SettingsSection(title = stringResource(R.string.settings_section_tracking)) {
        SwitchRow(
            label = stringResource(R.string.settings_tracking_background_label),
            subtitle = stringResource(R.string.settings_tracking_background_subtitle),
            checked = state.automaticTrackingEnabled,
            onCheckedChange = trackingPermissionState.onToggle,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = trackingPermissionState.statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.automaticTrackingFailure?.let { failure ->
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = failure,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        trackingPermissionState.permissionMessage?.let { message ->
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        SensitivitySlider(
            value = state.autoDetectSensitivity,
            onChange = callbacks.onAutoDetectSensitivityChanged,
        )
    }
}

@Composable
private fun PrivacySettingsSection(
    state: SettingsUiState,
    navigationActions: SettingsScreenActions,
    callbacks: SettingsCallbacks,
) {
    SettingsSection(title = stringResource(R.string.settings_section_privacy)) {
        SwitchRow(
            label = stringResource(R.string.settings_cloud_upload_label),
            subtitle = stringResource(R.string.settings_cloud_upload_subtitle),
            checked = state.cloudUploadConsentGranted,
            onCheckedChange = callbacks.onCloudUploadConsentChanged,
        )
        Spacer(Modifier.height(Spacing.md))
        NavEntryRow(
            title = stringResource(R.string.privacy_dashboard_title),
            subtitle = stringResource(R.string.privacy_dashboard_subtitle),
            onClick = navigationActions.onNavigateToPrivacyDashboard,
        )
    }
}

@Composable
private fun SmartClassificationSection(
    state: SettingsUiState,
    calendarPermissionState: CalendarPermissionUiState,
) {
    SettingsSection(title = "Smart Classification") {
        SwitchRow(
            label = "Calendar Hints",
            subtitle = "Suggest a trip purpose based on calendar events near your drive time",
            checked = state.calendarHintsEnabled,
            onCheckedChange = calendarPermissionState.onToggle,
        )
        calendarPermissionState.message?.let { message ->
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClassificationNavigationSection(
    navigationActions: SettingsScreenActions,
) {
    SettingsSection(title = stringResource(R.string.settings_section_classification)) {
        NavEntryRow(
            title = stringResource(R.string.settings_classification_title),
            subtitle = stringResource(R.string.settings_classification_subtitle),
            onClick = navigationActions.onNavigateToClassificationRules,
        )
    }
}

@Composable
private fun ExpensesAndIntegrationsSection(
    state: SettingsUiState,
    navigationActions: SettingsScreenActions,
) {
    val vehicleId = state.defaultVehicleId ?: state.activeVehicles.firstOrNull()?.id
    SettingsSection(title = "Expenses & Integrations") {
        if (vehicleId == null) {
            Text(
                text = "Add a vehicle before logging expenses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            NavEntryRow(
                title = "Vehicle Expenses",
                subtitle = "Log fuel, maintenance, and receipt-based expenses",
                onClick = {
                    navigationActions.onNavigateToExpenses(vehicleId, LocalDate.now().year)
                },
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        if (navigationActions.showIntegrations) {
            NavEntryRow(
                title = "Accounting Integrations",
                subtitle = "Connect QuickBooks, Xero, or payroll CSV export",
                onClick = navigationActions.onNavigateToIntegrations,
            )
        }
    }
}
