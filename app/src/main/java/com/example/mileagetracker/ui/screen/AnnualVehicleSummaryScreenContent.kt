package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.AnnualVehicleSummaryUiState

@Composable
internal fun AnnualSummaryScreenContent(
    innerPadding: PaddingValues,
    availableYears: List<Int>,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    activeVehicles: List<VehicleEntity>,
    selectedVehicleId: Int?,
    onVehicleSelected: (Int) -> Unit,
    showFormatPicker: Boolean,
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
    uiState: AnnualVehicleSummaryUiState,
    onOpenOdometer: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        AnnualYearDropdown(
            years = availableYears,
            selectedYear = selectedYear,
            onYearSelected = onYearSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        AnnualSummaryVehiclePicker(
            activeVehicles = activeVehicles,
            selectedVehicleId = selectedVehicleId,
            onVehicleSelected = onVehicleSelected,
        )
        AnnualSummaryFormatPicker(
            isVisible = showFormatPicker,
            selectedFormat = selectedFormat,
            onFormatSelected = onFormatSelected,
        )
        HorizontalDivider()
        AnnualSummaryStateContent(
            uiState = uiState,
            onOpenOdometer = onOpenOdometer,
        )
    }
}

@Composable
private fun AnnualSummaryVehiclePicker(
    activeVehicles: List<VehicleEntity>,
    selectedVehicleId: Int?,
    onVehicleSelected: (Int) -> Unit,
) {
    if (activeVehicles.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(0.dp),
    ) {
        items(activeVehicles) { vehicle ->
            FilterChip(
                selected = selectedVehicleId == vehicle.id,
                onClick = { onVehicleSelected(vehicle.id) },
                label = { Text(vehicle.summaryChipLabel()) },
            )
        }
    }
}

@Composable
private fun AnnualSummaryFormatPicker(
    isVisible: Boolean,
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
) {
    if (!isVisible) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(0.dp),
    ) {
        items(ExportFormat.entries) { format ->
            FilterChip(
                selected = selectedFormat == format,
                onClick = { onFormatSelected(format) },
                label = { Text(format.name) },
            )
        }
    }
}

@Composable
private fun AnnualSummaryStateContent(
    uiState: AnnualVehicleSummaryUiState,
    onOpenOdometer: (Int) -> Unit,
) {
    when (val state = uiState) {
        AnnualVehicleSummaryUiState.Idle -> AnnualSummaryEmptyState()
        AnnualVehicleSummaryUiState.Loading -> AnnualSummaryLoadingState()
        is AnnualVehicleSummaryUiState.Error -> AnnualSummaryErrorState(message = state.message)
        is AnnualVehicleSummaryUiState.Success -> SummaryContent(
            summary = state.summary,
            onOpenOdometer = { onOpenOdometer(state.summary.vehicleId) },
        )
    }
}

@Composable
private fun AnnualSummaryEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.annual_summary_no_data),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AnnualSummaryLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AnnualSummaryErrorState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnualYearDropdown(
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
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            years.forEach { year ->
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

private fun VehicleEntity.summaryChipLabel(): String = displayName.ifBlank {
    listOfNotNull(make, model)
        .joinToString(" ")
        .ifBlank { "Vehicle $id" }
}
