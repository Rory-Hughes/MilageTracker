package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.LogbookEntry
import com.example.mileagetracker.domain.model.LogbookSummary
import com.example.mileagetracker.domain.model.displayLabel
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.CraLogbookUiState
import com.example.mileagetracker.ui.viewmodel.ExportUiState
import com.example.mileagetracker.util.DateFormats
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalLocale
import java.text.DateFormatSymbols
import java.util.Locale

@Composable
internal fun CraLogbookScreenContent(
    innerPadding: PaddingValues,
    availableYears: List<Int>,
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    selectedMonth: Int?,
    onMonthSelected: (Int?) -> Unit,
    activeVehicles: List<VehicleEntity>,
    selectedVehicleId: Int?,
    onVehicleSelected: (Int?) -> Unit,
    filterableCategories: List<TripCategory>,
    selectedCategories: Set<TripCategory>,
    onToggleCategory: (TripCategory) -> Unit,
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
    uiState: CraLogbookUiState,
    onTripTapped: (Int) -> Unit,
    exportingState: ExportUiState.Exporting?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                LogbookYearDropdown(
                    years = availableYears,
                    selectedYear = selectedYear,
                    onYearSelected = onYearSelected,
                    modifier = Modifier.weight(1f),
                )
                LogbookMonthDropdown(
                    selectedMonth = selectedMonth,
                    onMonthSelected = onMonthSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            LogbookVehicleDropdown(
                activeVehicles = activeVehicles,
                selectedVehicleId = selectedVehicleId,
                onVehicleSelected = onVehicleSelected,
                modifier = Modifier.fillMaxWidth(),
            )
            LogbookCategoryFilters(
                filterableCategories = filterableCategories,
                selectedCategories = selectedCategories,
                onToggleCategory = onToggleCategory,
            )
            LogbookFormatFilters(
                selectedFormat = selectedFormat,
                onFormatSelected = onFormatSelected,
            )
        }

        HorizontalDivider()
        LogbookColumnHeaders()
        HorizontalDivider()
        CraLogbookStateContent(uiState = uiState, onTripTapped = onTripTapped)
    }

    exportingState?.let {
        ExportProgressDialog(
            taxYear = it.taxYear,
            vehicleId = it.vehicleId,
            format = selectedFormat,
            activeVehicles = activeVehicles,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogbookMonthDropdown(
    selectedMonth: Int?,
    onMonthSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allMonthsLabel = stringResource(R.string.cra_logbook_filter_all_months)
    val monthNames = remember {
        DateFormatSymbols(Locale.getDefault()).months.filter { it.isNotBlank() }
    }
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedMonth
        ?.let { monthNames.getOrNull(it - 1) }
        ?: allMonthsLabel

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.cra_logbook_filter_month)) },
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
            DropdownMenuItem(
                text = { Text(allMonthsLabel) },
                onClick = {
                    onMonthSelected(null)
                    expanded = false
                },
            )
            monthNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onMonthSelected(index + 1)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogbookVehicleDropdown(
    activeVehicles: List<VehicleEntity>,
    selectedVehicleId: Int?,
    onVehicleSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allVehiclesLabel = stringResource(R.string.cra_logbook_filter_all_vehicles)
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selectedVehicleId
        ?.let { id -> activeVehicles.firstOrNull { it.id == id }?.displayLabel() }
        ?: allVehiclesLabel

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.cra_logbook_filter_vehicle)) },
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
            DropdownMenuItem(
                text = { Text(allVehiclesLabel) },
                onClick = {
                    onVehicleSelected(null)
                    expanded = false
                },
            )
            activeVehicles.forEach { vehicle ->
                DropdownMenuItem(
                    text = { Text(vehicle.displayLabel()) },
                    onClick = {
                        onVehicleSelected(vehicle.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LogbookCategoryFilters(
    filterableCategories: List<TripCategory>,
    selectedCategories: Set<TripCategory>,
    onToggleCategory: (TripCategory) -> Unit,
) {
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

@Composable
private fun LogbookFormatFilters(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit,
) {
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
private fun ExportProgressDialog(
    taxYear: Int,
    vehicleId: Int?,
    format: ExportFormat,
    activeVehicles: List<VehicleEntity>,
) {
    val allVehiclesLabel = stringResource(R.string.cra_logbook_filter_all_vehicles)
    val vehicleLabel = if (vehicleId == null) {
        allVehiclesLabel
    } else {
        activeVehicles
            .firstOrNull { it.id == vehicleId }
            ?.displayLabel(vehicleId)
            ?: "Vehicle #$vehicleId"
    }
    val title = stringResource(R.string.cra_logbook_generating_title).format(format.name)

    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.annual_summary_tax_year) + " $taxYear · $vehicleLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun LogbookColumnHeaders() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            stringResource(R.string.cra_logbook_col_date),
            style = style,
            color = color,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            stringResource(R.string.cra_logbook_col_origin),
            style = style,
            color = color,
            modifier = Modifier.weight(2.5f),
        )
        Text(
            stringResource(R.string.cra_logbook_col_destination),
            style = style,
            color = color,
            modifier = Modifier.weight(2.5f),
        )
        Text(
            stringResource(R.string.cra_logbook_col_category),
            style = style,
            color = color,
            modifier = Modifier.weight(2f),
        )
        Text(
            stringResource(R.string.cra_logbook_col_km),
            style = style,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.cra_logbook_col_vehicle),
            style = style,
            color = color,
            modifier = Modifier.weight(1.5f),
        )
    }
}

@Composable
internal fun LogbookEntryRow(
    entry: LogbookEntry,
    onTap: () -> Unit,
) {
    val dateStr = DateFormats.uiMonthDay(entry.date, LocalLocale.current.platformLocale)
    val kmStr = "${(entry.businessKm * 10).roundToInt() / 10f}"
    val bodyStyle = MaterialTheme.typography.bodySmall

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dateStr, style = bodyStyle, modifier = Modifier.weight(1.5f))
        Text(
            text = entry.origin,
            style = bodyStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2.5f),
        )
        Text(
            text = entry.destination,
            style = bodyStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2.5f),
        )
        Text(
            text = entry.category.displayName,
            style = bodyStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2f),
        )
        Text(
            text = kmStr,
            style = bodyStyle,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = entry.vehicleName,
            style = bodyStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f),
        )
    }
}

@Composable
internal fun LogbookSummaryBar(summary: LogbookSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.cra_logbook_eligible_km),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "${(summary.businessKm * 10).roundToInt() / 10f} km",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.cra_logbook_total_km),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "${(summary.totalKm * 10).roundToInt() / 10f} km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.cra_logbook_business_use),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "${(summary.businessUsePercent * 10).roundToInt() / 10f}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogbookYearDropdown(
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
