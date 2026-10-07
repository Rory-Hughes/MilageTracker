package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.mileagetracker.R
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.CraRateDiagnosticsUiState
import com.example.mileagetracker.ui.viewmodel.SettingsUiState

internal data class RateModeActions(
    val onRateModeChange: (RateMode) -> Unit,
    val onRegionChange: (CraRateRegion) -> Unit,
    val onInputChange: (String) -> Unit,
    val onApply: () -> Unit,
    val onOpenSource: () -> Unit,
)

@Composable
internal fun RateModeSection(
    state: SettingsUiState,
    diagnosticsState: CraRateDiagnosticsUiState,
    actions: RateModeActions,
) {
    RateModeSelector(
        selectedMode = state.rateMode,
        onModeSelected = actions.onRateModeChange,
    )
    if (state.rateMode == RateMode.CUSTOM) {
        CustomRateEditor(state, actions)
    } else {
        CraStandardRateSection(state, diagnosticsState, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RateModeSelector(
    selectedMode: RateMode,
    onModeSelected: (RateMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = when (selectedMode) {
                RateMode.CRA_STANDARD -> stringResource(R.string.settings_rate_cra)
                RateMode.CUSTOM -> stringResource(R.string.settings_rate_custom)
            },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_rate_label)) },
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_rate_cra)) },
                onClick = {
                    onModeSelected(RateMode.CRA_STANDARD)
                    expanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_rate_custom)) },
                onClick = {
                    onModeSelected(RateMode.CUSTOM)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun CustomRateEditor(
    state: SettingsUiState,
    actions: RateModeActions,
) {
    Spacer(Modifier.height(Spacing.md))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = state.customRateInput,
            onValueChange = actions.onInputChange,
            label = { Text(stringResource(R.string.settings_rate_custom_label)) },
            placeholder = { Text(stringResource(R.string.settings_rate_custom_placeholder)) },
            isError = state.customRateError != null,
            supportingText = state.customRateError?.let { { Text(it) } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { actions.onApply() },
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .wrapContentWidth(unbounded = false),
        )
        TextButton(
            onClick = actions.onApply,
            modifier = Modifier.align(Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.settings_rate_apply))
        }
    }
    if (state.customRateError == null) {
        Text(
            text = "$${"%.2f".format(state.customRateDollars)}/km",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CraStandardRateSection(
    state: SettingsUiState,
    diagnosticsState: CraRateDiagnosticsUiState,
    actions: RateModeActions,
) {
    Spacer(Modifier.height(Spacing.xs))
    Text(
        text = stringResource(R.string.settings_rate_cra_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.md))
    CraRateRegionPicker(
        selected = state.craRateRegion,
        onSelect = actions.onRegionChange,
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = stringResource(R.string.settings_rate_cra_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.md))
    CraRateDiagnosticsCard(
        state = diagnosticsState,
        onOpenSource = actions.onOpenSource,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CraRateRegionPicker(
    selected: CraRateRegion?,
    onSelect: (CraRateRegion) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        CraRateRegion.PROVINCES -> stringResource(R.string.settings_rate_region_provinces)
        CraRateRegion.TERRITORIES -> stringResource(R.string.settings_rate_region_territories)
        null -> stringResource(R.string.settings_rate_region_required)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_rate_region_label)) },
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
            CraRateRegion.entries.forEach { region ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (region) {
                                CraRateRegion.PROVINCES ->
                                    stringResource(R.string.settings_rate_region_provinces)
                                CraRateRegion.TERRITORIES ->
                                    stringResource(R.string.settings_rate_region_territories)
                            },
                        )
                    },
                    onClick = {
                        onSelect(region)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CraRateDiagnosticsCard(
    state: CraRateDiagnosticsUiState,
    onOpenSource: () -> Unit,
) {
    when {
        state.isLoading -> DiagnosticsStatusText(
            text = stringResource(R.string.settings_rate_validation_loading),
            isError = false,
        )

        state.error != null -> DiagnosticsStatusText(
            text = state.error,
            isError = true,
        )

        state.diagnostics != null -> CraRateDiagnosticsContent(
            state = state,
            onOpenSource = onOpenSource,
        )
    }
}

@Composable
private fun DiagnosticsStatusText(
    text: String,
    isError: Boolean,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun CraRateDiagnosticsContent(
    state: CraRateDiagnosticsUiState,
    onOpenSource: () -> Unit,
) {
    val diagnostics = requireNotNull(state.diagnostics)
    val currentYearReady = diagnostics.currentYearSupported && diagnostics.currentYearSeeded
    val missingYears = diagnostics.unseededYears
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
        ?: "None"

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.settings_rate_validation_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                R.string.settings_rate_validation_catalog,
                diagnostics.catalogStartYear,
                diagnostics.catalogEndYear,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.settings_rate_validation_seeded,
                formatYears(diagnostics.seededYears),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_rate_validation_missing, missingYears),
            style = MaterialTheme.typography.bodySmall,
            color = if (diagnostics.unseededYears.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = stringResource(
                if (currentYearReady) {
                    R.string.settings_rate_validation_current_ready
                } else {
                    R.string.settings_rate_validation_current_missing
                },
                diagnostics.currentYear,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (currentYearReady) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        TextButton(onClick = onOpenSource) {
            Text(stringResource(R.string.settings_rate_validation_source))
        }
    }
}

private fun formatYears(years: List<Int>): String {
    val sortedYears = years.sorted()
    val contiguousRange = sortedYears
        .takeIf { it.size > 1 && it.zipWithNext().all { (left, right) -> right == left + 1 } }
        ?.let { "${it.first()}-${it.last()}" }

    return when {
        years.isEmpty() -> "None"
        years.size == 1 -> years.first().toString()
        contiguousRange != null -> contiguousRange
        else -> sortedYears.joinToString(", ")
    }
}
