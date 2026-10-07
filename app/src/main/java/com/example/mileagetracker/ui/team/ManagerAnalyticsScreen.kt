package com.example.mileagetracker.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.mileagetracker.domain.model.ReimbursementForecast
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerAnalyticsScreen(
    currentUser: TeamMember,
    managedDrivers: List<TeamMember>,
    onNavigateUp: () -> Unit,
    viewModel: ManagerAnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    ManagerAnalyticsEffects(
        currentUser = currentUser,
        managedDrivers = managedDrivers,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
    )

    ManagerAnalyticsScaffold(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
    )
}

@Composable
private fun ManagerAnalyticsEffects(
    currentUser: TeamMember,
    managedDrivers: List<TeamMember>,
    uiState: ManagerAnalyticsUiState,
    snackbarHostState: SnackbarHostState,
    viewModel: ManagerAnalyticsViewModel,
) {
    LaunchedEffect(currentUser.userId) {
        viewModel.initialise(currentUser, managedDrivers)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagerAnalyticsScaffold(
    uiState: ManagerAnalyticsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Manager Analytics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ManagerAnalyticsContent(
            uiState = uiState,
            innerPadding = innerPadding,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
        )
    }
}

@Composable
private fun ManagerAnalyticsContent(
    uiState: ManagerAnalyticsUiState,
    innerPadding: PaddingValues,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    if (!uiState.isRoleAuthorised) {
        RoleGuard(modifier = Modifier.padding(innerPadding))
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            MonthNavigator(
                periodLabel = uiState.forecasts.firstOrNull()?.periodLabel
                    ?: "${monthName(uiState.selectedMonth)} ${uiState.selectedYear}",
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
            )
        }

        if (uiState.isLoading) {
            item { ManagerAnalyticsLoadingState() }
            return@LazyColumn
        }

        item {
            FleetTotalsCard(
                totalActual = uiState.totalActualReimbursement,
                totalProjected = uiState.totalProjectedReimbursement,
            )
        }

        item {
            Text(
                text = "Driver Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        driverForecastItems(forecasts = uiState.forecasts)
    }
}

private fun LazyListScope.driverForecastItems(
    forecasts: List<ReimbursementForecast>,
) {
    if (forecasts.isEmpty()) {
        item {
            Text(
                text = "No driver data for this period.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val maxProjected = forecasts.maxOfOrNull { it.projectedReimbursement } ?: 1f
    items(forecasts, key = { it.driverUid }) { forecast ->
        DriverAnalyticsRow(
            forecast = forecast,
        )
    }
}

@Composable
private fun ManagerAnalyticsLoadingState() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MonthNavigator(
    periodLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun FleetTotalsCard(
    totalActual: Float,
    totalProjected: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "Fleet Reimbursement",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TotalColumn(
                    label = "Actual (to date)",
                    value = "$${"%,.2f".format(totalActual)}",
                )
                TotalColumn(
                    label = "Projected (EOM)",
                    value = "$${"%,.2f".format(totalProjected)}",
                    bold = true,
                )
            }
        }
    }
}

@Composable
private fun TotalColumn(
    label: String,
    value: String,
    bold: Boolean = false,
) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun DriverAnalyticsRow(
    forecast: ReimbursementForecast,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = forecast.driverDisplayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = {
                    (
                        forecast.actualReimbursement /
                            forecast.projectedReimbursement.coerceAtLeast(0.01f)
                        ).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            DriverAnalyticsSummaryRow(forecast = forecast)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = forecast.rateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun monthName(zeroBasedMonth: Int): String = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)[zeroBasedMonth]
