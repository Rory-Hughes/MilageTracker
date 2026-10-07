package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.places.PlacePrediction
import com.example.mileagetracker.ui.viewmodel.ManualTripFormState
import com.example.mileagetracker.ui.viewmodel.ManualTripEntryEvent
import com.example.mileagetracker.ui.viewmodel.ManualTripEntryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTripEntryScreen(
    activeVehicles: List<VehicleEntity>,
    onNavigateBack: () -> Unit,
    onSaveSuccess: (driveId: Int, isRepair: Boolean) -> Unit,
    viewModel: ManualTripEntryViewModel = hiltViewModel(),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val startSuggestions by viewModel.startSuggestions.collectAsStateWithLifecycle()
    val endSuggestions by viewModel.endSuggestions.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    ManualTripEntryEffects(
        onSaveSuccess = onSaveSuccess,
        snackbar = snackbar,
        viewModel = viewModel,
    )
    ManualTripEntryScaffold(
        formState = formState,
        snackbar = snackbar,
        activeVehicles = activeVehicles,
        startSuggestions = startSuggestions,
        endSuggestions = endSuggestions,
        onNavigateBack = onNavigateBack,
        onStartedAtChange = viewModel.updateStartedAt,
        onEndedAtChange = viewModel.updateEndedAt,
        onStartLocationChange = viewModel.updateStartLocation,
        onEndLocationChange = viewModel.updateEndLocation,
        onStartSuggestionSelected = viewModel.selectStartSuggestion,
        onEndSuggestionSelected = viewModel.selectEndSuggestion,
        onDismissStartSuggestions = viewModel.clearStartSuggestions,
        onDismissEndSuggestions = viewModel.clearEndSuggestions,
        onDistanceChange = viewModel.updateDistanceKm,
        onCategoryChange = viewModel.updateCategory,
        onPurposeChange = viewModel.updatePurposeText,
        onVehicleChange = viewModel.updateVehicle,
        onNotesChange = viewModel.updateNotes,
        onSave = viewModel.save,
    )
}

@Composable
private fun ManualTripEntryEffects(
    onSaveSuccess: (driveId: Int, isRepair: Boolean) -> Unit,
    snackbar: SnackbarHostState,
    viewModel: ManualTripEntryViewModel,
) {
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ManualTripEntryEvent.SaveSuccess -> onSaveSuccess(event.driveId, event.isRepair)
                is ManualTripEntryEvent.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTripEntryScaffold(
    formState: ManualTripFormState,
    snackbar: SnackbarHostState,
    activeVehicles: List<VehicleEntity>,
    startSuggestions: List<PlacePrediction>,
    endSuggestions: List<PlacePrediction>,
    onNavigateBack: () -> Unit,
    onStartedAtChange: (Long) -> Unit,
    onEndedAtChange: (Long) -> Unit,
    onStartLocationChange: (String) -> Unit,
    onEndLocationChange: (String) -> Unit,
    onStartSuggestionSelected: (PlacePrediction) -> Unit,
    onEndSuggestionSelected: (PlacePrediction) -> Unit,
    onDismissStartSuggestions: () -> Unit,
    onDismissEndSuggestions: () -> Unit,
    onDistanceChange: (String) -> Unit,
    onCategoryChange: (TripCategory) -> Unit,
    onPurposeChange: (String) -> Unit,
    onVehicleChange: (VehicleEntity?) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ManualTripEntryTopBar(
                isRepairMode = formState.isRepairMode,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        ManualTripEntryContent(
            formState = formState,
            activeVehicles = activeVehicles,
            startSuggestions = startSuggestions,
            endSuggestions = endSuggestions,
            padding = padding,
            onStartedAtChange = onStartedAtChange,
            onEndedAtChange = onEndedAtChange,
            onStartLocationChange = onStartLocationChange,
            onEndLocationChange = onEndLocationChange,
            onStartSuggestionSelected = onStartSuggestionSelected,
            onEndSuggestionSelected = onEndSuggestionSelected,
            onDismissStartSuggestions = onDismissStartSuggestions,
            onDismissEndSuggestions = onDismissEndSuggestions,
            onDistanceChange = onDistanceChange,
            onCategoryChange = onCategoryChange,
            onPurposeChange = onPurposeChange,
            onVehicleChange = onVehicleChange,
            onNotesChange = onNotesChange,
            onSave = onSave,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualTripEntryTopBar(
    isRepairMode: Boolean,
    onNavigateBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                if (isRepairMode) {
                    stringResource(R.string.manual_trip_repair_title)
                } else {
                    stringResource(R.string.manual_trip_title)
                },
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
        actions = {
            if (isRepairMode) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = stringResource(R.string.manual_trip_repair_mode),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun ManualTripEntryContent(
    formState: ManualTripFormState,
    activeVehicles: List<VehicleEntity>,
    startSuggestions: List<PlacePrediction>,
    endSuggestions: List<PlacePrediction>,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onStartedAtChange: (Long) -> Unit,
    onEndedAtChange: (Long) -> Unit,
    onStartLocationChange: (String) -> Unit,
    onEndLocationChange: (String) -> Unit,
    onStartSuggestionSelected: (PlacePrediction) -> Unit,
    onEndSuggestionSelected: (PlacePrediction) -> Unit,
    onDismissStartSuggestions: () -> Unit,
    onDismissEndSuggestions: () -> Unit,
    onDistanceChange: (String) -> Unit,
    onCategoryChange: (TripCategory) -> Unit,
    onPurposeChange: (String) -> Unit,
    onVehicleChange: (VehicleEntity?) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        ManualTripIntro()
        ManualTripDateTimeSection(
            formState = formState,
            onStartedAtChange = onStartedAtChange,
            onEndedAtChange = onEndedAtChange,
        )
        ManualTripLocationsSection(
            formState = formState,
            startSuggestions = startSuggestions,
            endSuggestions = endSuggestions,
            onStartLocationChange = onStartLocationChange,
            onEndLocationChange = onEndLocationChange,
            onStartSuggestionSelected = onStartSuggestionSelected,
            onEndSuggestionSelected = onEndSuggestionSelected,
            onDismissStartSuggestions = onDismissStartSuggestions,
            onDismissEndSuggestions = onDismissEndSuggestions,
        )
        ManualTripDistanceSection(
            formState = formState,
            onDistanceChange = onDistanceChange,
        )
        ManualTripCategorySection(
            formState = formState,
            onCategoryChange = onCategoryChange,
            onPurposeChange = onPurposeChange,
        )
        ManualTripVehicleSection(
            activeVehicles = activeVehicles,
            selectedVehicle = formState.selectedVehicle,
            onVehicleChange = onVehicleChange,
        )
        ManualTripNotesSection(
            notes = formState.notes,
            onNotesChange = onNotesChange,
        )
        SaveManualTripButton(
            isRepairMode = formState.isRepairMode,
            isSaveEnabled = formState.isSaveEnabled,
            isSaving = formState.isSaving,
            onSave = onSave,
        )
        Spacer(Modifier.height(24.dp))
    }
}
