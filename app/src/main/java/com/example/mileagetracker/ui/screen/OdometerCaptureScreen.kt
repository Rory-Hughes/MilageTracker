package com.example.mileagetracker.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.domain.model.OdometerBoundaryType
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.model.OdometerCaptureType
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.OdometerCaptureEvent
import com.example.mileagetracker.ui.viewmodel.OdometerFormState
import com.example.mileagetracker.ui.viewmodel.OdometerCaptureViewModel
import com.example.mileagetracker.util.DateFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdometerCaptureScreen(
    vehicleName: String,
    onNavigateBack: () -> Unit,
    viewModel: OdometerCaptureViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val history by viewModel.historyFlow.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        viewModel.onPhotoResult(success)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = formState.captureDateMs,
    )

    OdometerCaptureEffects(
        snackbar = snackbar,
        viewModel = viewModel,
    )
    OdometerDatePickerDialog(
        visible = showDatePicker,
        datePickerState = datePickerState,
        onDismiss = { showDatePicker = false },
        onDateConfirmed = {
            viewModel.updateCaptureDate(it)
            showDatePicker = false
        },
    )
    OdometerCaptureScaffold(
        vehicleName = vehicleName,
        snackbar = snackbar,
        formState = formState,
        history = history,
        onNavigateBack = onNavigateBack,
        onOpenCamera = {
            val uri = viewModel.createCameraUri()
            cameraLauncher.launch(uri)
        },
        onClearPhoto = viewModel::clearPhoto,
        onReadingTextChange = viewModel::updateReadingText,
        onOpenDatePicker = { showDatePicker = true },
        onBoundaryTypeChange = viewModel::updateBoundaryType,
        onNotesChange = viewModel::updateNotes,
        onSave = viewModel::save,
    )
}

@Composable
private fun OdometerCaptureEffects(
    snackbar: SnackbarHostState,
    viewModel: OdometerCaptureViewModel,
) {
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OdometerCaptureEvent.SaveSuccess -> {
                    snackbar.showSnackbar("Odometer reading saved.")
                }

                is OdometerCaptureEvent.RegressionWarning -> {
                    snackbar.showSnackbar(
                        "Saved ${event.savedKm} km - lower than previous reading " +
                            "(${event.previousKm} km). Was the odometer replaced?",
                    )
                }

                is OdometerCaptureEvent.Error -> {
                    snackbar.showSnackbar(event.message)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OdometerDatePickerDialog(
    visible: Boolean,
    datePickerState: DatePickerState,
    onDismiss: () -> Unit,
    onDateConfirmed: (Long) -> Unit,
) {
    if (!visible) {
        return
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let(onDateConfirmed)
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OdometerCaptureScaffold(
    vehicleName: String,
    snackbar: SnackbarHostState,
    formState: OdometerFormState,
    history: List<OdometerCapture>,
    onNavigateBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onClearPhoto: () -> Unit,
    onReadingTextChange: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onBoundaryTypeChange: (OdometerBoundaryType) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Record Odometer")
                        Text(
                            text = vehicleName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item { Spacer(Modifier.height(Spacing.xs)) }
            item {
                OdometerEntryCard(
                    formState = formState,
                    dateLabel = DateFormats.uiMonthFullDate(formState.captureDateMs),
                    onOpenCamera = onOpenCamera,
                    onClearPhoto = onClearPhoto,
                    onReadingTextChange = onReadingTextChange,
                    onOpenDatePicker = onOpenDatePicker,
                    onBoundaryTypeChange = onBoundaryTypeChange,
                    onNotesChange = onNotesChange,
                    onSave = onSave,
                )
            }
            odometerHistoryItems(history)
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

@Composable
private fun OdometerEntryCard(
    formState: OdometerFormState,
    dateLabel: String,
    onOpenCamera: () -> Unit,
    onClearPhoto: () -> Unit,
    onReadingTextChange: (String) -> Unit,
    onOpenDatePicker: () -> Unit,
    onBoundaryTypeChange: (OdometerBoundaryType) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "New Reading",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OdometerPhotoRow(
                hasPhoto = formState.photoUri != null,
                onOpenCamera = onOpenCamera,
                onClearPhoto = onClearPhoto,
            )
            OutlinedTextField(
                value = formState.readingKmText,
                onValueChange = onReadingTextChange,
                label = { Text("Odometer reading (km)") },
                singleLine = true,
                isError = formState.kmError != null,
                supportingText = formState.kmError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = onOpenDatePicker,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(dateLabel)
            }
            OdometerBoundarySelector(
                selected = formState.boundaryType,
                onSelected = onBoundaryTypeChange,
            )
            OutlinedTextField(
                value = formState.notes,
                onValueChange = onNotesChange,
                label = { Text("Notes (optional)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSave,
                enabled = formState.isSaveEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (formState.isSaving) "Saving..." else "Save Reading")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OdometerBoundarySelector(
    selected: OdometerBoundaryType,
    onSelected: (OdometerBoundaryType) -> Unit,
) {
    val options = OdometerBoundaryType.entries
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = "CRA evidence type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = selected == type,
                    onClick = { onSelected(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(boundaryLabel(type))
                }
            }
        }
    }
}

@Composable
private fun OdometerPhotoRow(
    hasPhoto: Boolean,
    onOpenCamera: () -> Unit,
    onClearPhoto: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        OutlinedButton(
            onClick = onOpenCamera,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(if (hasPhoto) "Photo attached" else "Take photo")
        }
        if (hasPhoto) {
            IconButton(onClick = onClearPhoto) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove photo",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun LazyListScope.odometerHistoryItems(history: List<OdometerCapture>) {
    item {
        Text(
            text = "Reading History",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        HorizontalDivider(modifier = Modifier.padding(top = Spacing.xs))
    }

    if (history.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No readings recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        items(
            items = history,
            key = { it.id },
        ) { capture ->
            OdometerHistoryRow(capture = capture)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Spacing.xs),
                thickness = 0.5.dp,
            )
        }
    }
}

private fun formatHistoryDate(epochMs: Long): String = DateFormats.uiDate(epochMs)

@Composable
private fun OdometerHistoryRow(capture: OdometerCapture) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = "${capture.readingKm} km",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (!capture.notes.isNullOrBlank()) {
                    Text(
                        text = capture.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${boundaryLabel(capture.boundaryType)} ${capture.taxYear}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = formatHistoryDate(capture.captureDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Added ${formatHistoryDate(capture.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (capture.captureType == OdometerCaptureType.PHOTO_ASSISTED) {
                Text(
                    text = "Photo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun boundaryLabel(type: OdometerBoundaryType): String = when (type) {
    OdometerBoundaryType.OPENING -> "Opening"
    OdometerBoundaryType.CLOSING -> "Closing"
    OdometerBoundaryType.OTHER -> "Other"
}
