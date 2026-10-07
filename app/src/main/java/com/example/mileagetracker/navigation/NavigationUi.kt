package com.example.mileagetracker.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R

@Composable
fun DisabledFeatureMessage(featureName: String) {
    Text(
        text = "$featureName are disabled in this solo CRA launch build.",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun Screen.localizedTitle(): String =
    screenTitleResIds[this]?.let { stringResource(it) } ?: title

/**
 * Screens that render their own top bar (back arrow + title) suppress
 * the shared hamburger bar from [MainAppScaffold] to avoid a stacked
 * double-title look.
 */
val Screen.showsMainTopBar: Boolean
    get() = this !in screensWithOwnTopBar

private val screensWithOwnTopBar = setOf(
    Screen.CraLogbook,
    Screen.TripDetail,
    Screen.ExceptionsList,
    Screen.ReportHistory,
    Screen.Export,
    Screen.VehicleExpense,
    Screen.ManualTripEntry,
)

private val screenTitleResIds = mapOf(
    Screen.Dashboard to R.string.nav_dashboard,
    Screen.Unclassified to R.string.nav_trips,
    Screen.ManualTrip to R.string.tracking_control_title,
    Screen.Reports to R.string.nav_reports,
    Screen.VehicleManagement to R.string.nav_vehicles,
    Screen.Settings to R.string.nav_settings,
    Screen.PrivacyDashboard to R.string.privacy_dashboard_title,
    Screen.TripDetail to R.string.trip_detail_title,
    Screen.CraLogbook to R.string.reports_cra_logbook,
    Screen.Export to R.string.export_screen_title,
    Screen.ClassificationRules to R.string.classification_rules_title,
    Screen.AnnualVehicleSummary to R.string.annual_summary_title,
    Screen.ReportHistory to R.string.report_history_title,
    Screen.SimplifiedLogbook to R.string.simplified_logbook_title,
    Screen.OdometerCapture to R.string.odometer_title,
)
