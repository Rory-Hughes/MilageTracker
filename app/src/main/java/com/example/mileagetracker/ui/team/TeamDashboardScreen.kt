package com.example.mileagetracker.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mileagetracker.domain.model.ReimbursementForecast
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.ui.theme.Spacing

/**
 * Manager Dashboard screen, role-gated to [TeamRole.MANAGER] and [TeamRole.ADMIN].
 *
 * Displays:
 * - Fleet-wide pending approval badge + shortcut button to [ApprovalQueueScreen]
 * - Per-driver reimbursement forecast cards for the current month
 * - Up to 5 most-recent trip exceptions across all managed drivers
 * - Navigation shortcuts to [ManagerAnalyticsScreen] and [PeriodLockScreen]
 *
 * @param currentUser    The signed-in team member (injected from the session).
 * @param managedDrivers All active [TeamRole.DRIVER] members this manager oversees.
 * @param onNavigateToApprovalQueue   Navigate to [ApprovalQueueScreen].
 * @param onNavigateToAnalytics       Navigate to [ManagerAnalyticsScreen].
 * @param onNavigateToPeriodLock      Navigate to [PeriodLockScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDashboardScreen(
    currentUser: TeamMember,
    managedDrivers: List<TeamMember>,
    onNavigateToApprovalQueue: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToPeriodLock: () -> Unit,
    viewModel: TeamDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(currentUser.userId) {
        viewModel.initialise(currentUser, managedDrivers)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Team Dashboard") },
                actions = {
                    IconButton(onClick = onNavigateToAnalytics) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Analytics")
                    }
                    IconButton(onClick = onNavigateToPeriodLock) {
                        Icon(Icons.Filled.Lock, contentDescription = "Lock Period")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            !uiState.isRoleAuthorised -> {
                RoleGuard(modifier = Modifier.padding(innerPadding))
            }
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                DashboardContent(
                    uiState = uiState,
                    innerPadding = innerPadding,
                    onNavigateToApprovalQueue = onNavigateToApprovalQueue,
                )
            }
        }
    }
}

/**
 * Role-guard placeholder shown when the current user does not have
 * MANAGER or ADMIN access. Declared `internal` so [ManagerAnalyticsScreen]
 * (same package) can reuse it.
 */
@Composable
internal fun RoleGuard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "Access Restricted",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "This screen is only available to Managers and Admins.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DashboardContent(
    uiState: TeamDashboardUiState,
    innerPadding: PaddingValues,
    onNavigateToApprovalQueue: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            ApprovalQueueBanner(
                pendingCount = uiState.pendingQueueCount,
                onClick = onNavigateToApprovalQueue,
            )
        }
        forecastItems(uiState.forecasts)
        exceptionItems(uiState.recentExceptions)
    }
}

private fun LazyListScope.forecastItems(
    forecasts: List<ReimbursementForecast>,
) {
    item {
        Text(
            text = "Reimbursement Forecasts - ${forecasts.firstOrNull()?.periodLabel.orEmpty()}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (forecasts.isEmpty()) {
        item {
            Text(
                text = "No active drivers found.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    items(forecasts, key = { it.driverUid }) { forecast ->
        ForecastCard(forecast)
    }
}

private fun LazyListScope.exceptionItems(
    recentExceptions: List<TripException>,
) {
    if (recentExceptions.isEmpty()) {
        return
    }

    item {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Recent Trip Exceptions",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }

    items(recentExceptions, key = { it.tripId }) { exception ->
        ExceptionCard(exception)
    }
}

@Composable
private fun ExceptionCard(
    exception: TripException,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = exceptionLabel(exception),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Trip #${exception.tripId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun exceptionLabel(exception: TripException): String =
    exception.exceptionType.name
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }

@Composable
private fun ApprovalQueueBanner(
    pendingCount: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedBox(
                badge = {
                    if (pendingCount > 0) {
                        Badge { Text(pendingCount.toString()) }
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Assignment,
                    contentDescription = "Approval Queue",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Approval Queue",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                val subtitle = if (pendingCount == 0) {
                    "No pending requests"
                } else {
                    "$pendingCount request${if (pendingCount > 1) "s" else ""} awaiting review"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ForecastCard(forecast: ReimbursementForecast) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = forecast.driverDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (forecast.pendingApprovalCount > 0) {
                    Badge {
                        Text(text = "${forecast.pendingApprovalCount} pending")
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            ForecastRow(label = "Actual km", value = "${"%,.1f".format(forecast.actualBusinessKm)} km")
            ForecastRow(label = "Projected km", value = "${"%,.1f".format(forecast.projectedBusinessKm)} km")
            ForecastRow(
                label = "Actual reimbursement",
                value = "$${"%,.2f".format(forecast.actualReimbursement)}",
            )
            ForecastRow(
                label = "Projected reimbursement",
                value = "$${"%,.2f".format(forecast.projectedReimbursement)}",
                valueBold = true,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Rate: ${forecast.rateLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ForecastRow(
    label: String,
    value: String,
    valueBold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
