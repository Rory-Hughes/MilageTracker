package com.example.mileagetracker.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.seeder.CraRateCatalog
import com.example.mileagetracker.domain.model.PermissionDiagnostic
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.ui.viewmodel.DriveViewModel
import com.example.mileagetracker.ui.viewmodel.TrackingDiagnosticsViewModel
import com.example.mileagetracker.ui.viewmodel.UnclassifiedUiState
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DriveViewModel,
    onNavigateToUnclassified: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToManualTrip: () -> Unit = {},
    onTripTapped: (Int) -> Unit = {},
) {
    val diagnosticsViewModel: TrackingDiagnosticsViewModel = hiltViewModel()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = rememberDashboardScreenState(
        viewModel = viewModel,
        diagnosticsViewModel = diagnosticsViewModel,
    )

    DashboardLifecycleEffects(
        lifecycle = lifecycle,
        diagnosticsViewModel = diagnosticsViewModel,
    )
    DashboardScreenContent(
        state = state,
        onNavigateToUnclassified = onNavigateToUnclassified,
        onNavigateToDiagnostics = onNavigateToDiagnostics,
        onNavigateToManualTrip = onNavigateToManualTrip,
        onTripTapped = onTripTapped,
        onClearBatteryDismissal = diagnosticsViewModel::clearBatteryDismissal,
        onDismissBatteryWarning = diagnosticsViewModel::dismissBatteryWarning,
    )
}

@Composable
private fun DashboardLifecycleEffects(
    lifecycle: Lifecycle,
    diagnosticsViewModel: TrackingDiagnosticsViewModel,
) {
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            diagnosticsViewModel.refresh()
        }
    }
}

@Composable
private fun rememberDashboardScreenState(
    viewModel: DriveViewModel,
    diagnosticsViewModel: TrackingDiagnosticsViewModel,
): DashboardScreenState {
    val uiState by viewModel.unclassifiedUiState.collectAsStateWithLifecycle()
    val ytdDistance by viewModel.ytdBusinessDistance.collectAsStateWithLifecycle()
    val ytdDeductions by viewModel.ytdDeductions.collectAsStateWithLifecycle()
    val recentTrips by viewModel.recentTrips.collectAsStateWithLifecycle()
    val craRateRegion by viewModel.craRateRegion.collectAsStateWithLifecycle()
    val rateMode by viewModel.rateMode.collectAsStateWithLifecycle()
    val diagnostics by diagnosticsViewModel.diagnostics.collectAsStateWithLifecycle()
    val batteryDismissed by diagnosticsViewModel.batteryWarningDismissed.collectAsStateWithLifecycle()
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val craRate = craRateRegion?.let { CraRateCatalog.resolve(currentYear, it) }
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.CANADA) }
    val otherIssueCount = diagnostics.count {
        it !is PermissionDiagnostic.AllClear &&
            it !is PermissionDiagnostic.BatteryOptimizationActive
    }

    return DashboardScreenState(
        unclassifiedCount = (uiState as? UnclassifiedUiState.Success)?.drives?.size ?: 0,
        showBatteryBanner = diagnostics.any {
            it is PermissionDiagnostic.BatteryOptimizationActive
        } && batteryDismissed == false,
        showOtherBanner = otherIssueCount > 0,
        otherIssueCount = otherIssueCount,
        ytdDistance = ytdDistance,
        formattedDeductions = currencyFormatter.format(ytdDeductions),
        canShowEstimate = rateMode == RateMode.CUSTOM || craRate != null,
        kmRemaining = (TIER_THRESHOLD_KM - ytdDistance).coerceAtLeast(0f),
        progress = (ytdDistance / TIER_THRESHOLD_KM).coerceIn(0f, 1f),
        firstTierDollars = craRate?.firstTier?.div(100.0),
        additionalDollars = craRate?.additional?.div(100.0),
        recentTrips = recentTrips,
    )
}

internal data class DashboardScreenState(
    val unclassifiedCount: Int,
    val showBatteryBanner: Boolean,
    val showOtherBanner: Boolean,
    val otherIssueCount: Int,
    val ytdDistance: Float,
    val formattedDeductions: String,
    val canShowEstimate: Boolean,
    val kmRemaining: Float,
    val progress: Float,
    val firstTierDollars: Double?,
    val additionalDollars: Double?,
    val recentTrips: List<DriveEntity>,
)

internal const val TIER_THRESHOLD_KM = 5_000f
