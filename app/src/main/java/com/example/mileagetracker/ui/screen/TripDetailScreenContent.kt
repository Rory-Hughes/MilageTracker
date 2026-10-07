package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.CalendarHint
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.domain.model.TripSuggestion
import com.example.mileagetracker.ui.attachment.AttachmentThumbnailRow
import com.example.mileagetracker.ui.audit.ExceptionFlagBanner
import com.example.mileagetracker.ui.calendar.AnimatedCalendarHintBanner
import com.example.mileagetracker.ui.common.CurrencyText
import com.example.mileagetracker.ui.common.DistanceText
import com.example.mileagetracker.ui.component.SuggestionBannerCard
import com.example.mileagetracker.ui.component.VehiclePickerDropdown
import com.example.mileagetracker.ui.map.TripRouteMapCard
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.util.localizedDisplayName
import com.example.mileagetracker.ui.viewmodel.RouteMapUiState
import com.example.mileagetracker.util.DriveDisplayModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TripDetailContent(
    drive: DriveEntity,
    route: List<RoutePoint>,
    routeMapState: RouteMapUiState,
    vehicle: VehicleEntity?,
    activeVehicles: List<VehicleEntity>,
    topSuggestion: TripSuggestion?,
    calendarHints: List<CalendarHint>,
    activeException: TripException?,
    attachments: List<TripAttachment>,
    onApplySuggestion: (TripSuggestion) -> Unit,
    onDismissSuggestion: () -> Unit,
    onApplyCalendarHint: (CalendarHint) -> Unit,
    onDismissCalendarHints: () -> Unit,
    onResolveException: () -> Unit,
    onDismissException: () -> Unit,
    onClassify: (TripCategory, String?) -> Unit,
    onAssignVehicle: (Int) -> Unit,
    onSaveNotes: (String) -> Unit,
    onUpdateEndpoints: (String, String) -> Unit,
    onAddAttachment: () -> Unit,
    onDeleteAttachment: (TripAttachment) -> Unit,
    onViewAttachment: (TripAttachment) -> Unit,
    onExpandMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = DriveDisplayModel.from(drive)
    var notesText by rememberSaveable(drive.id) { mutableStateOf(drive.notes ?: "") }
    var customText by rememberSaveable(drive.id) { mutableStateOf(drive.purposeText ?: "") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(modifier = Modifier.height(Spacing.xs))
        TripDetailBannerSection(
            topSuggestion = topSuggestion,
            calendarHints = calendarHints,
            purposeText = drive.purposeText,
            activeException = activeException,
            onApplySuggestion = onApplySuggestion,
            onDismissSuggestion = onDismissSuggestion,
            onApplyCalendarHint = onApplyCalendarHint,
            onDismissCalendarHints = onDismissCalendarHints,
            onResolveException = onResolveException,
            onDismissException = onDismissException,
        )
        TripHeaderCard(
            drive = drive,
            display = display,
            onUpdateEndpoints = onUpdateEndpoints,
        )
        TripCategorySection(
            drive = drive,
            customText = customText,
            onCustomTextChange = { customText = it },
            onClassify = onClassify,
        )
        TripVehicleSection(
            activeVehicles = activeVehicles,
            selectedVehicle = vehicle,
            onAssignVehicle = onAssignVehicle,
        )
        TripNotesSection(
            notesText = notesText,
            onNotesChange = { notesText = it },
            onSaveNotes = onSaveNotes,
        )
        AttachmentThumbnailRow(
            attachments = attachments,
            onAddClicked = onAddAttachment,
            onDeleteAttachment = onDeleteAttachment,
            onViewAttachment = onViewAttachment,
        )
        TripRouteSummaryCard(route = route)
        if (routeMapState !is RouteMapUiState.NoRoute) {
            TripRouteMapCard(state = routeMapState, onExpandMap = onExpandMap)
        }
        Spacer(modifier = Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun TripDetailBannerSection(
    topSuggestion: TripSuggestion?,
    calendarHints: List<CalendarHint>,
    purposeText: String?,
    activeException: TripException?,
    onApplySuggestion: (TripSuggestion) -> Unit,
    onDismissSuggestion: () -> Unit,
    onApplyCalendarHint: (CalendarHint) -> Unit,
    onDismissCalendarHints: () -> Unit,
    onResolveException: () -> Unit,
    onDismissException: () -> Unit,
) {
    val firstCalendarHint = calendarHints.firstOrNull()
    val showCalendarBanner = firstCalendarHint != null && purposeText.isNullOrBlank()

    topSuggestion?.let { suggestion ->
        SuggestionBannerCard(
            suggestion = suggestion,
            onApply = { onApplySuggestion(suggestion) },
            onDismiss = onDismissSuggestion,
        )
    }

    AnimatedCalendarHintBanner(
        visible = showCalendarBanner,
        hint = firstCalendarHint,
        onUseHint = { firstCalendarHint?.let(onApplyCalendarHint) },
        onDismiss = onDismissCalendarHints,
    )

    ExceptionFlagBanner(
        exception = activeException,
        onResolve = onResolveException,
        onDismiss = onDismissException,
    )
}

@Composable
private fun TripHeaderCard(
    drive: DriveEntity,
    display: DriveDisplayModel,
    onUpdateEndpoints: (String, String) -> Unit,
) {
    var showEndpointEditor by rememberSaveable(drive.id) { mutableStateOf(false) }

    if (showEndpointEditor) {
        TripEndpointsEditDialog(
            initialStart = drive.startLocation,
            initialEnd = drive.endLocation,
            onDismiss = { showEndpointEditor = false },
            onSave = { start, end ->
                onUpdateEndpoints(start, end)
                showEndpointEditor = false
            },
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${display.dateLabel} • ${display.timeLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DistanceText(
                    km = drive.distanceKm.toDouble(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
            TripLocationSummary(
                drive = drive,
                onEditEndpoints = { showEndpointEditor = true },
            )
            TripReimbursementSummary(reimbursementAmount = drive.reimbursementAmountSnapshot)
        }
    }
}

@Composable
private fun TripLocationSummary(
    drive: DriveEntity,
    onEditEndpoints: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(drive.startLocation, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "↓",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            Text(drive.endLocation, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(
            onClick = onEditEndpoints,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.trip_detail_edit_locations),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TripEndpointsEditDialog(
    initialStart: String,
    initialEnd: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var startText by rememberSaveable(initialStart) { mutableStateOf(initialStart) }
    var endText by rememberSaveable(initialEnd) { mutableStateOf(initialEnd) }
    val canSave = startText.isNotBlank() && endText.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_detail_edit_locations)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it },
                    label = { Text(stringResource(R.string.trip_detail_start_location)) },
                    isError = startText.isBlank(),
                    minLines = 1,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it },
                    label = { Text(stringResource(R.string.trip_detail_end_location)) },
                    isError = endText.isBlank(),
                    minLines = 1,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(startText.trim(), endText.trim()) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun TripReimbursementSummary(reimbursementAmount: Float?) {
    if (reimbursementAmount == null || reimbursementAmount <= 0f) {
        return
    }

    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.trip_detail_reimbursement),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CurrencyText(
            amount = reimbursementAmount.toDouble(),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripCategorySection(
    drive: DriveEntity,
    customText: String,
    onCustomTextChange: (String) -> Unit,
    onClassify: (TripCategory, String?) -> Unit,
) {
    Text(
        text = stringResource(R.string.trip_detail_category),
        style = MaterialTheme.typography.titleSmall,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        TripCategory.selectableEntries.forEach { category ->
            val isSelected = drive.tripCategory == category
            FilterChip(
                selected = isSelected,
                onClick = { onClassify(category, category.classificationPurpose(customText)) },
                label = { Text(category.localizedDisplayName()) },
                colors = if (isSelected && category == TripCategory.BUSINESS) {
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                },
            )
        }
    }

    TripCategory.selectableEntries
        .firstOrNull { it == drive.tripCategory }
        ?.craHint
        ?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

    if (drive.tripCategory == TripCategory.CUSTOM) {
        OutlinedTextField(
            value = customText,
            onValueChange = onCustomTextChange,
            label = { Text(stringResource(R.string.trip_detail_purpose_required_for_custom)) },
            singleLine = true,
            isError = customText.isBlank(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            trailingIcon = {
                TextButton(
                    onClick = { onClassify(TripCategory.CUSTOM, customText) },
                    enabled = customText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TripVehicleSection(
    activeVehicles: List<VehicleEntity>,
    selectedVehicle: VehicleEntity?,
    onAssignVehicle: (Int) -> Unit,
) {
    Text(
        text = stringResource(R.string.trip_detail_vehicle),
        style = MaterialTheme.typography.titleSmall,
    )
    VehiclePickerDropdown(
        vehicles = activeVehicles,
        selectedVehicle = selectedVehicle,
        onVehicleSelected = { onAssignVehicle(it.id) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TripNotesSection(
    notesText: String,
    onNotesChange: (String) -> Unit,
    onSaveNotes: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.trip_detail_notes),
        style = MaterialTheme.typography.titleSmall,
    )
    OutlinedTextField(
        value = notesText,
        onValueChange = onNotesChange,
        label = { Text(stringResource(R.string.trip_detail_notes_hint)) },
        minLines = 3,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
        ),
        trailingIcon = {
            TextButton(onClick = { onSaveNotes(notesText) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TripRouteSummaryCard(route: List<RoutePoint>) {
    if (route.isEmpty()) {
        return
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = Icons.Default.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.trip_detail_route_recorded),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.trip_detail_gps_points_captured,
                        route.size,
                        route.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun TripCategory.classificationPurpose(customText: String): String? =
    if (this == TripCategory.CUSTOM) customText.takeIf { it.isNotBlank() } else null
