package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.ReportPeriod
import com.example.mileagetracker.ui.common.EmptyStateView
import com.example.mileagetracker.ui.common.ErrorStateView
import com.example.mileagetracker.ui.component.ReportReadinessCard
import com.example.mileagetracker.ui.component.ReportSummaryCard
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.ReportsUiState
import com.example.mileagetracker.ui.viewmodel.ReportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    activeVehicles: List<VehicleEntity>,
    onOpenLogbook: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenExpenses: (vehicleId: Int, year: Int) -> Unit = { _, _ -> },
    onOpenIntegrations: () -> Unit = {},
    showIntegrations: Boolean = false,
    onOpenExceptions: () -> Unit = {},
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsStateWithLifecycle()

    val periods = ReportPeriod.entries
    val selectedTabIndex = periods.indexOf(selectedPeriod)
    val expenseVehicleId = selectedVehicleId ?: activeVehicles.firstOrNull()?.id

    ReportsScreenScaffold(
        periods = periods,
        selectedTabIndex = selectedTabIndex,
        selectedYear = selectedYear,
        selectedVehicleId = selectedVehicleId,
        activeVehicles = activeVehicles,
        availableYears = viewModel.availableYears,
        expenseVehicleId = expenseVehicleId,
        uiState = uiState,
        showIntegrations = showIntegrations,
        onSelectPeriod = viewModel::selectPeriod,
        onSelectYear = viewModel::selectYear,
        onSelectVehicle = viewModel::selectVehicle,
        onOpenLogbook = onOpenLogbook,
        onOpenHistory = onOpenHistory,
        onOpenExport = onOpenExport,
        onOpenExpenses = { vehicleId -> onOpenExpenses(vehicleId, selectedYear) },
        onOpenIntegrations = onOpenIntegrations,
        onOpenExceptions = onOpenExceptions,
        onRetry = { viewModel.selectYear(selectedYear) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsScreenScaffold(
    periods: List<ReportPeriod>,
    selectedTabIndex: Int,
    selectedYear: Int,
    selectedVehicleId: Int?,
    activeVehicles: List<VehicleEntity>,
    availableYears: List<Int>,
    expenseVehicleId: Int?,
    uiState: ReportsUiState,
    showIntegrations: Boolean,
    onSelectPeriod: (ReportPeriod) -> Unit,
    onSelectYear: (Int) -> Unit,
    onSelectVehicle: (Int?) -> Unit,
    onOpenLogbook: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenExpenses: (Int) -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenExceptions: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ReportsPeriodTabs(
            periods = periods,
            selectedTabIndex = selectedTabIndex,
            onSelectPeriod = onSelectPeriod,
        )
        ReportsControls(
            availableYears = availableYears,
            selectedYear = selectedYear,
            selectedVehicleId = selectedVehicleId,
            activeVehicles = activeVehicles,
            expenseVehicleId = expenseVehicleId,
            showIntegrations = showIntegrations,
            onSelectYear = onSelectYear,
            onSelectVehicle = onSelectVehicle,
            onOpenLogbook = onOpenLogbook,
            onOpenHistory = onOpenHistory,
            onOpenExport = onOpenExport,
            onOpenExpenses = onOpenExpenses,
            onOpenIntegrations = onOpenIntegrations,
        )
        HorizontalDivider()
        ReportsContent(
            uiState = uiState,
            onRetry = onRetry,
            onOpenExceptions = onOpenExceptions,
        )
    }
}

@Composable
private fun ReportsPeriodTabs(
    periods: List<ReportPeriod>,
    selectedTabIndex: Int,
    onSelectPeriod: (ReportPeriod) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
        periods.forEachIndexed { index, period ->
            Tab(
                selected = index == selectedTabIndex,
                onClick = { onSelectPeriod(period) },
                text = {
                    Text(
                        period.name.lowercase().replaceFirstChar(Char::uppercase),
                    )
                },
            )
        }
    }
}

@Composable
private fun ReportsControls(
    availableYears: List<Int>,
    selectedYear: Int,
    selectedVehicleId: Int?,
    activeVehicles: List<VehicleEntity>,
    expenseVehicleId: Int?,
    showIntegrations: Boolean,
    onSelectYear: (Int) -> Unit,
    onSelectVehicle: (Int?) -> Unit,
    onOpenLogbook: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenExpenses: (Int) -> Unit,
    onOpenIntegrations: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ReportsYearDropdown(
            years = availableYears,
            selectedYear = selectedYear,
            onYearSelected = onSelectYear,
            modifier = Modifier.fillMaxWidth(),
        )
        ReportsVehicleFilters(
            activeVehicles = activeVehicles,
            selectedVehicleId = selectedVehicleId,
            onSelectVehicle = onSelectVehicle,
        )
        ReportsActionButtons(
            expenseVehicleId = expenseVehicleId,
            showIntegrations = showIntegrations,
            onOpenLogbook = onOpenLogbook,
            onOpenHistory = onOpenHistory,
            onOpenExport = onOpenExport,
            onOpenExpenses = onOpenExpenses,
            onOpenIntegrations = onOpenIntegrations,
        )
    }
}

@Composable
private fun ReportsVehicleFilters(
    activeVehicles: List<VehicleEntity>,
    selectedVehicleId: Int?,
    onSelectVehicle: (Int?) -> Unit,
) {
    if (activeVehicles.isEmpty()) {
        return
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        item {
            FilterChip(
                selected = selectedVehicleId == null,
                onClick = { onSelectVehicle(null) },
                label = { Text(stringResource(R.string.cra_logbook_filter_all_vehicles)) },
            )
        }
        items(activeVehicles) { vehicle ->
            FilterChip(
                selected = selectedVehicleId == vehicle.id,
                onClick = { onSelectVehicle(vehicle.id) },
                label = {
                    Text(
                        vehicle.displayName.ifBlank {
                            "${vehicle.make} ${vehicle.model}".trim()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ReportsActionButtons(
    expenseVehicleId: Int?,
    showIntegrations: Boolean,
    onOpenLogbook: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenExpenses: (Int) -> Unit,
    onOpenIntegrations: () -> Unit,
) {
    ReportsActionButton(
        text = stringResource(R.string.reports_cra_logbook),
        icon = Icons.AutoMirrored.Filled.MenuBook,
        onClick = onOpenLogbook,
    )
    ReportsActionButton(
        text = stringResource(R.string.report_history_title),
        icon = Icons.Default.History,
        onClick = onOpenHistory,
    )
    ReportsActionButton(
        text = stringResource(R.string.export_screen_title),
        icon = Icons.Default.FileDownload,
        onClick = onOpenExport,
    )
    OutlinedButton(
        onClick = { expenseVehicleId?.let(onOpenExpenses) },
        enabled = expenseVehicleId != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Vehicle Expenses")
    }
    if (showIntegrations) {
        OutlinedButton(
            onClick = onOpenIntegrations,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Integrations")
        }
    }
}

@Composable
private fun ReportsActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(end = Spacing.sm),
        )
        Text(text)
    }
}

@Composable
private fun ReportsContent(
    uiState: ReportsUiState,
    onRetry: () -> Unit,
    onOpenExceptions: () -> Unit,
) {
    when (val state = uiState) {
        ReportsUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is ReportsUiState.Error -> {
            ErrorStateView(
                message = state.message,
                onRetry = onRetry,
            )
        }

        is ReportsUiState.Success -> {
            ReportsSuccessContent(
                state = state,
                onOpenExceptions = onOpenExceptions,
            )
        }
    }
}

@Composable
private fun ReportsSuccessContent(
    state: ReportsUiState.Success,
    onOpenExceptions: () -> Unit,
) {
    ReportReadinessCard(
        readinessList = state.readinessList,
        onReviewTrips = onOpenExceptions,
    )

    if (state.summaries.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.BarChart,
            title = stringResource(R.string.reports_empty_title),
            body = stringResource(R.string.reports_empty_body),
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(
                items = state.summaries,
                key = { it.periodLabel },
            ) { summary ->
                ReportSummaryCard(summary = summary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsYearDropdown(
    years: List<Int>,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedYear.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.annual_summary_tax_year)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .wrapContentWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            years.sortedDescending().forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        onYearSelected(year)
                        expanded = false
                    },
                )
            }
        }
    }
}
