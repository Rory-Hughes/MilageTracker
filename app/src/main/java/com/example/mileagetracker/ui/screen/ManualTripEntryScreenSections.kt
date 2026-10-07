package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.places.PlacePrediction
import com.example.mileagetracker.ui.component.VehiclePickerDropdown
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.util.localizedDisplayName
import com.example.mileagetracker.ui.viewmodel.ManualTripFormState

@Composable
internal fun ManualTripIntro() {
    Text(
        text = stringResource(R.string.manual_trip_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun ManualTripDateTimeSection(
    formState: ManualTripFormState,
    onStartedAtChange: (Long) -> Unit,
    onEndedAtChange: (Long) -> Unit,
) {
    FormSectionCard(
        title = stringResource(R.string.manual_trip_datetime_section),
        subtitle = stringResource(R.string.manual_trip_datetime_subtitle),
    ) {
        DateTimeRow(
            label = stringResource(R.string.trip_detail_start),
            epochMs = formState.startedAtMs,
            error = formState.fieldErrors["startedAt"],
            onPickedMs = onStartedAtChange,
        )
        DateTimeRow(
            label = stringResource(R.string.trip_detail_end),
            epochMs = formState.endedAtMs,
            error = formState.fieldErrors["endedAt"],
            onPickedMs = onEndedAtChange,
        )
    }
}

@Composable
internal fun ManualTripLocationsSection(
    formState: ManualTripFormState,
    startSuggestions: List<PlacePrediction>,
    endSuggestions: List<PlacePrediction>,
    onStartLocationChange: (String) -> Unit,
    onEndLocationChange: (String) -> Unit,
    onStartSuggestionSelected: (PlacePrediction) -> Unit,
    onEndSuggestionSelected: (PlacePrediction) -> Unit,
    onDismissStartSuggestions: () -> Unit,
    onDismissEndSuggestions: () -> Unit,
) {
    FormSectionCard(
        title = stringResource(R.string.manual_trip_locations_section),
        subtitle = stringResource(R.string.manual_trip_locations_subtitle),
    ) {
        AddressAutocompleteField(
            value = formState.startLocation,
            label = stringResource(R.string.manual_trip_start_label),
            suggestions = startSuggestions,
            isError = formState.fieldErrors.containsKey("startLocation"),
            errorText = formState.fieldErrors["startLocation"],
            onValueChange = onStartLocationChange,
            onSuggestionSelected = onStartSuggestionSelected,
            onDismissSuggestions = onDismissStartSuggestions,
            fieldTag = "manual_trip_start_field",
        )
        AddressAutocompleteField(
            value = formState.endLocation,
            label = stringResource(R.string.manual_trip_end_label),
            suggestions = endSuggestions,
            isError = formState.fieldErrors.containsKey("endLocation"),
            errorText = formState.fieldErrors["endLocation"],
            onValueChange = onEndLocationChange,
            onSuggestionSelected = onEndSuggestionSelected,
            onDismissSuggestions = onDismissEndSuggestions,
            fieldTag = "manual_trip_end_field",
        )
    }
}

@Composable
internal fun ManualTripDistanceSection(
    formState: ManualTripFormState,
    onDistanceChange: (String) -> Unit,
) {
    FormSectionCard(title = stringResource(R.string.manual_trip_distance_label)) {
        if (formState.isDistanceFromMaps) {
            Text(
                text = stringResource(R.string.manual_trip_distance_maps_calculated),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        OutlinedTextField(
            value = formState.distanceKmText,
            onValueChange = onDistanceChange,
            label = { Text(stringResource(R.string.manual_trip_distance_label)) },
            singleLine = true,
            isError = formState.fieldErrors.containsKey("distanceKm"),
            supportingText = formState.fieldErrors["distanceKm"]?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_trip_distance_field"),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ManualTripCategorySection(
    formState: ManualTripFormState,
    onCategoryChange: (TripCategory) -> Unit,
    onPurposeChange: (String) -> Unit,
) {
    FormSectionCard(title = stringResource(R.string.manual_trip_category_label)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TripCategory.selectableEntries.forEach { category ->
                FilterChip(
                    selected = formState.category == category,
                    onClick = { onCategoryChange(category) },
                    label = { Text(category.localizedDisplayName()) },
                )
            }
        }
        formState.category?.craHint?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (formState.category == TripCategory.CUSTOM) {
            OutlinedTextField(
                value = formState.purposeText,
                onValueChange = onPurposeChange,
                label = { Text(stringResource(R.string.manual_trip_purpose_required_label)) },
                singleLine = true,
                isError = formState.fieldErrors.containsKey("purposeText"),
                supportingText = formState.fieldErrors["purposeText"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun ManualTripVehicleSection(
    activeVehicles: List<VehicleEntity>,
    selectedVehicle: VehicleEntity?,
    onVehicleChange: (VehicleEntity?) -> Unit,
) {
    FormSectionCard(title = stringResource(R.string.manual_trip_vehicle_label)) {
        VehiclePickerDropdown(
            vehicles = activeVehicles,
            selectedVehicle = selectedVehicle,
            onVehicleSelected = onVehicleChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ManualTripNotesSection(
    notes: String,
    onNotesChange: (String) -> Unit,
) {
    FormSectionCard(title = stringResource(R.string.manual_trip_notes_label)) {
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text(stringResource(R.string.manual_trip_notes_hint)) },
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SaveManualTripButton(
    isRepairMode: Boolean,
    isSaveEnabled: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
) {
    Button(
        onClick = onSave,
        enabled = isSaveEnabled && !isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("manual_trip_save_button"),
    ) {
        Text(
            if (isRepairMode) {
                stringResource(R.string.manual_trip_save_repair)
            } else {
                stringResource(R.string.action_add_trip)
            },
        )
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            content()
        }
    }
}

@Composable
private fun AddressAutocompleteField(
    value: String,
    label: String,
    suggestions: List<PlacePrediction>,
    isError: Boolean,
    errorText: String?,
    onValueChange: (String) -> Unit,
    onSuggestionSelected: (PlacePrediction) -> Unit,
    onDismissSuggestions: () -> Unit,
    fieldTag: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            supportingText = errorText?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier
                .fillMaxWidth()
                .let { if (fieldTag != null) it.testTag(fieldTag) else it },
        )
        if (suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    suggestions.forEachIndexed { index, suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSuggestionSelected(suggestion)
                                    onDismissSuggestions()
                                }
                                .padding(Spacing.md),
                        ) {
                            Text(
                                text = suggestion.primaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (suggestion.secondaryText.isNotBlank()) {
                                Text(
                                    text = suggestion.secondaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index < suggestions.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
