package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.model.PermissionDiagnostic
import com.example.mileagetracker.ui.theme.Spacing

@Composable
internal fun DashboardScreenContent(
    state: DashboardScreenState,
    onNavigateToUnclassified: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToManualTrip: () -> Unit,
    onTripTapped: (Int) -> Unit,
    onClearBatteryDismissal: () -> Unit,
    onDismissBatteryWarning: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            DashboardFab(onNavigateToManualTrip = onNavigateToManualTrip)
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            if (state.showBatteryBanner) {
                BatteryWarningBanner(
                    onOpenSettings = {
                        onClearBatteryDismissal()
                        onNavigateToDiagnostics()
                    },
                    onDismiss = onDismissBatteryWarning,
                )
            }
            if (state.showOtherBanner) {
                OtherDiagnosticsBanner(
                    issueCount = state.otherIssueCount,
                    onOpenSettings = onNavigateToDiagnostics,
                )
            }
            if (state.unclassifiedCount > 0) {
                UnclassifiedDrivesBanner(
                    unclassifiedCount = state.unclassifiedCount,
                    onNavigateToUnclassified = onNavigateToUnclassified,
                )
            }
            YtdEstimateCard(state = state)
            RecentTripsSection(
                recentTrips = state.recentTrips,
                onTripTapped = onTripTapped,
            )
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun DashboardFab(onNavigateToManualTrip: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onNavigateToManualTrip,
        icon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.dashboard_trip_start),
            )
        },
        text = { Text(stringResource(R.string.dashboard_trip_start)) },
    )
}

@Composable
private fun BatteryWarningBanner(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    DashboardWarningCard {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarningIcon()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    PermissionDiagnostic.BatteryOptimizationActive.TITLE,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.diagnostics_battery_warning_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.diagnostics_open_settings))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_dismiss))
            }
        }
    }
}

@Composable
private fun OtherDiagnosticsBanner(
    issueCount: Int,
    onOpenSettings: () -> Unit,
) {
    DashboardWarningCard {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarningIcon(
                contentDescription = stringResource(R.string.diagnostics_battery_restricted),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.diagnostics_title),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "$issueCount ${stringResource(R.string.diagnostics_battery_restricted)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.diagnostics_open_settings))
            }
        }
    }
}

@Composable
private fun UnclassifiedDrivesBanner(
    unclassifiedCount: Int,
    onNavigateToUnclassified: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarningIcon(
                contentDescription = stringResource(R.string.trips_unclassified_banner),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.unclassified_title),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "$unclassifiedCount ${stringResource(R.string.trips_unclassified_banner)}",
                )
            }
            Button(onClick = onNavigateToUnclassified) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}

@Composable
private fun DashboardWarningCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun WarningIcon(contentDescription: String? = null) {
    Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = contentDescription,
        modifier = Modifier.padding(end = Spacing.md),
    )
}

@Composable
private fun YtdEstimateCard(state: DashboardScreenState) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Text(
                stringResource(R.string.dashboard_reimbursement_estimate),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (state.canShowEstimate) {
                    state.formattedDeductions
                } else {
                    stringResource(R.string.dashboard_rate_unavailable)
                },
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(Spacing.xl))
            Text(
                text = businessKmLabel(ytdDistance = state.ytdDistance),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                strokeCap = StrokeCap.Round,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            CraRateProgressText(state = state)
        }
    }
}

@Composable
private fun CraRateProgressText(state: DashboardScreenState) {
    if (state.firstTierDollars == null || state.additionalDollars == null) return

    Text(
        text = craRateProgressLabel(
            kmRemaining = state.kmRemaining,
            firstTierDollars = state.firstTierDollars,
            additionalDollars = state.additionalDollars,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun businessKmLabel(ytdDistance: Float): String =
    "${stringResource(R.string.dashboard_business_km)}: ${"%,.1f".format(ytdDistance)} km"

@Composable
private fun craRateProgressLabel(
    kmRemaining: Float,
    firstTierDollars: Double,
    additionalDollars: Double,
): String {
    val remainingKmLabel = "%,.0f".format(kmRemaining)
    val rateLabel = stringResource(R.string.annual_summary_cra_rate)
    return "$remainingKmLabel km $rateLabel ($$firstTierDollars → $$additionalDollars)"
}
