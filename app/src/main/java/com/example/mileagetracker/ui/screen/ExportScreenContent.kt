package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.displayLabel
import com.example.mileagetracker.ui.theme.Spacing

@Composable
internal fun ExportScreenContent(
    innerPadding: PaddingValues,
    availableYears: List<Int>,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    activeVehicles: List<VehicleEntity>,
    selectedVehicleId: Int?,
    onVehicleSelected: (Int?) -> Unit,
    selectedCategories: Set<TripCategory>,
    filterableCategories: List<TripCategory>,
    onToggleCategory: (TripCategory) -> Unit,
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
    isExporting: Boolean,
    onExport: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            ExportYearSection(
                availableYears = availableYears,
                selectedYear = selectedYear,
                onYearSelected = onYearSelected,
            )
            ExportVehicleSection(
                activeVehicles = activeVehicles,
                selectedVehicleId = selectedVehicleId,
                onVehicleSelected = onVehicleSelected,
            )
            ExportCategorySection(
                selectedCategories = selectedCategories,
                filterableCategories = filterableCategories,
                onToggleCategory = onToggleCategory,
            )
            ExportFormatSection(
                selectedFormat = selectedFormat,
                onFormatSelected = onFormatSelected,
            )
            ExportActionSection(
                selectedFormat = selectedFormat,
                isExporting = isExporting,
                onExport = onExport,
            )
            Text(
                text = stringResource(R.string.odometer_cra_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}

@Composable
private fun ExportYearSection(
    availableYears: List<Int>,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
) {
    ExportSectionCard(title = stringResource(R.string.annual_summary_tax_year)) {
        ExportYearDropdown(
            availableYears = availableYears,
            selectedYear = selectedYear,
            onYearSelected = onYearSelected,
        )
    }
}

@Composable
private fun ExportVehicleSection(
    activeVehicles: List<VehicleEntity>,
    selectedVehicleId: Int?,
    onVehicleSelected: (Int?) -> Unit,
) {
    ExportSectionCard(title = stringResource(R.string.export_screen_vehicle_scope_title)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(0.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedVehicleId == null,
                    onClick = { onVehicleSelected(null) },
                    label = { Text(stringResource(R.string.cra_logbook_filter_all_vehicles)) },
                )
            }
            items(activeVehicles) { vehicle ->
                FilterChip(
                    selected = selectedVehicleId == vehicle.id,
                    onClick = { onVehicleSelected(vehicle.id) },
                    label = { Text(vehicle.displayLabel()) },
                )
            }
        }
    }
}

@Composable
private fun ExportCategorySection(
    selectedCategories: Set<TripCategory>,
    filterableCategories: List<TripCategory>,
    onToggleCategory: (TripCategory) -> Unit,
) {
    ExportSectionCard(title = stringResource(R.string.export_screen_categories_title)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(0.dp),
        ) {
            items(filterableCategories) { category ->
                FilterChip(
                    selected = category in selectedCategories,
                    onClick = { onToggleCategory(category) },
                    label = { Text(category.displayName) },
                )
            }
        }
    }
}

@Composable
private fun ExportFormatSection(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
) {
    ExportSectionCard(title = stringResource(R.string.action_export)) {
        val formatOptions = listOf(ExportFormat.PDF, ExportFormat.CSV)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            formatOptions.forEachIndexed { index, format ->
                SegmentedButton(
                    selected = selectedFormat == format,
                    onClick = { onFormatSelected(format) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = formatOptions.size,
                    ),
                    label = { Text(format.exportLabel()) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.export_screen_route_details_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExportActionSection(
    selectedFormat: ExportFormat,
    isExporting: Boolean,
    onExport: () -> Unit,
) {
    if (isExporting) {
        ExportLoadingRow(selectedFormat = selectedFormat)
    } else {
        ExportActionButton(
            selectedFormat = selectedFormat,
            onExport = onExport,
        )
    }
}

@Composable
private fun ExportLoadingRow(selectedFormat: ExportFormat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(Spacing.md))
        Text(
            text = stringResource(
                R.string.cra_logbook_generating_title,
                selectedFormat.name,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ExportActionButton(
    selectedFormat: ExportFormat,
    onExport: () -> Unit,
) {
    Button(
        onClick = onExport,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.FileDownload,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = Spacing.xs),
        )
        Text(text = selectedFormat.exportLabel())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportYearDropdown(
    availableYears: List<Int>,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedYear.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.annual_summary_tax_year)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availableYears.sortedDescending().forEach { year ->
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

@Composable
private fun ExportSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.md))
            content()
        }
    }
}

@Composable
private fun ExportFormat.exportLabel(): String = when (this) {
    ExportFormat.PDF -> stringResource(R.string.cra_logbook_export_pdf)
    ExportFormat.CSV -> stringResource(R.string.cra_logbook_export_csv)
}
