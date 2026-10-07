package com.example.mileagetracker.ui.screen

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.CalendarHint
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.model.TripAttachment
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.domain.model.TripSuggestion
import com.example.mileagetracker.ui.attachment.AttachmentEvent
import com.example.mileagetracker.ui.attachment.AttachmentPickerSheet
import com.example.mileagetracker.ui.attachment.AttachmentViewModel
import com.example.mileagetracker.ui.viewmodel.RouteMapUiState
import com.example.mileagetracker.ui.viewmodel.TripDetailEvent
import com.example.mileagetracker.ui.viewmodel.TripDetailUiState
import com.example.mileagetracker.ui.viewmodel.TripDetailViewModel

/** Full-screen trip detail and classification view. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    activeVehicles: List<VehicleEntity>,
    onNavigateBack: () -> Unit,
    onRepairTrip: (driveId: Int) -> Unit = {},
    onViewAuditHistory: (driveId: Int) -> Unit = {},
    onViewAttachment: (TripAttachment) -> Unit = {},
    onExpandMap: (driveId: Int) -> Unit = {},
    viewModel: TripDetailViewModel = hiltViewModel(),
    attachmentViewModel: AttachmentViewModel = hiltViewModel(),
) {
    val uiSnapshot = rememberTripDetailUiSnapshot(
        viewModel = viewModel,
        attachmentViewModel = attachmentViewModel,
    )
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAttachmentPicker by remember { mutableStateOf(false) }
    val successActions = TripDetailSuccessActions(
        onViewAuditHistory = onViewAuditHistory,
        onViewAttachment = onViewAttachment,
        onExpandMap = onExpandMap,
        onShowAttachmentPicker = { showAttachmentPicker = true },
    )

    TripDetailEventEffects(
        viewModel = viewModel,
        attachmentViewModel = attachmentViewModel,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
    )
    HandleTripDetailNotFound(uiState = uiSnapshot.uiState, onNavigateBack = onNavigateBack)

    TripDetailDialogs(
        showDeleteDialog = showDeleteDialog,
        onDismissDelete = { showDeleteDialog = false },
        onConfirmDelete = {
            viewModel.softDelete()
            showDeleteDialog = false
        },
        showAttachmentPicker = showAttachmentPicker,
        onDismissAttachmentPicker = { showAttachmentPicker = false },
        onAttachmentPicked = { uri ->
            showAttachmentPicker = false
            attachmentViewModel.addAttachment(uri)
        },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TripDetailTopBar(
                uiState = uiSnapshot.uiState,
                onNavigateBack = onNavigateBack,
                onRepairTrip = onRepairTrip,
                onDeleteRequested = { showDeleteDialog = true },
                onViewAuditHistory = onViewAuditHistory,
            )
        },
    ) { padding ->
        TripDetailScreenBody(
            uiSnapshot = uiSnapshot,
            activeVehicles = activeVehicles,
            viewModel = viewModel,
            attachmentViewModel = attachmentViewModel,
            actions = successActions,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun rememberTripDetailUiSnapshot(
    viewModel: TripDetailViewModel,
    attachmentViewModel: AttachmentViewModel,
): TripDetailUiSnapshot {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val routeMapState by viewModel.routeMapState.collectAsStateWithLifecycle()
    val topSuggestion by viewModel.topSuggestion.collectAsStateWithLifecycle()
    val calendarHints by viewModel.calendarHints.collectAsStateWithLifecycle()
    val activeException by viewModel.activeException.collectAsStateWithLifecycle()
    val attachments by attachmentViewModel.attachments.collectAsStateWithLifecycle()
    return TripDetailUiSnapshot(
        uiState = uiState,
        route = route,
        routeMapState = routeMapState,
        topSuggestion = topSuggestion,
        calendarHints = calendarHints,
        activeException = activeException,
        attachments = attachments,
    )
}

@Composable
private fun HandleTripDetailNotFound(
    uiState: TripDetailUiState,
    onNavigateBack: () -> Unit,
) {
    LaunchedEffect(uiState) {
        if (uiState is TripDetailUiState.NotFound) {
            onNavigateBack()
        }
    }
}

@Composable
private fun TripDetailScreenBody(
    uiSnapshot: TripDetailUiSnapshot,
    activeVehicles: List<VehicleEntity>,
    viewModel: TripDetailViewModel,
    attachmentViewModel: AttachmentViewModel,
    actions: TripDetailSuccessActions,
    modifier: Modifier = Modifier,
) {
    when (val state = uiSnapshot.uiState) {
        TripDetailUiState.Loading,
        TripDetailUiState.NotFound,
        -> TripDetailLoadingState(modifier = modifier)

        is TripDetailUiState.Success -> TripDetailContent(
            drive = state.drive,
            route = uiSnapshot.route,
            routeMapState = uiSnapshot.routeMapState,
            vehicle = state.vehicle,
            activeVehicles = activeVehicles,
            topSuggestion = uiSnapshot.topSuggestion,
            calendarHints = uiSnapshot.calendarHints,
            activeException = uiSnapshot.activeException,
            attachments = uiSnapshot.attachments,
            onApplySuggestion = viewModel.applySuggestion,
            onDismissSuggestion = viewModel.dismissSuggestion,
            onApplyCalendarHint = viewModel.applyCalendarHint,
            onDismissCalendarHints = viewModel.dismissCalendarHints,
            onResolveException = { actions.onViewAuditHistory(state.drive.id) },
            onDismissException = viewModel.dismissExceptionBanner,
            onClassify = viewModel.classifyTrip,
            onAssignVehicle = viewModel.assignVehicle,
            onSaveNotes = viewModel.updateNotes,
            onUpdateEndpoints = viewModel.updateEndpoints,
            onAddAttachment = actions.onShowAttachmentPicker,
            onDeleteAttachment = attachmentViewModel::deleteAttachment,
            onViewAttachment = actions.onViewAttachment,
            onExpandMap = { actions.onExpandMap(state.drive.id) },
            modifier = modifier,
        )
    }
}

@Composable
private fun TripDetailEventEffects(
    viewModel: TripDetailViewModel,
    attachmentViewModel: AttachmentViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
) {
    LaunchedEffect(viewModel, snackbarHostState, onNavigateBack) {
        viewModel.events.collect { event ->
            if (event == TripDetailEvent.Deleted) {
                snackbarHostState.showSnackbar("Trip deleted")
                onNavigateBack()
            } else {
                snackbarHostState.showSnackbar(event.snackbarMessage())
            }
        }
    }

    LaunchedEffect(attachmentViewModel, snackbarHostState) {
        attachmentViewModel.events.collect { event ->
            snackbarHostState.showSnackbar(event.snackbarMessage())
        }
    }
}

@Composable
private fun TripDetailDialogs(
    showDeleteDialog: Boolean,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    showAttachmentPicker: Boolean,
    onDismissAttachmentPicker: () -> Unit,
    onAttachmentPicked: (Uri) -> Unit,
) {
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.trip_detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.trip_detail_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(
                        text = stringResource(R.string.trip_detail_delete_confirm_yes),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.trip_detail_delete_confirm_no))
                }
            },
        )
    }

    if (showAttachmentPicker) {
        AttachmentPickerSheet(
            onDismiss = onDismissAttachmentPicker,
            onAttachmentPicked = onAttachmentPicked,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailTopBar(
    uiState: TripDetailUiState,
    onNavigateBack: () -> Unit,
    onRepairTrip: (Int) -> Unit,
    onDeleteRequested: () -> Unit,
    onViewAuditHistory: (Int) -> Unit,
) {
    val drive = (uiState as? TripDetailUiState.Success)?.drive
    var showOverflowMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.trip_detail_title)) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
        actions = {
            drive?.takeIf(DriveEntity::needsRepairAction)?.let { repairableDrive ->
                IconButton(onClick = { onRepairTrip(repairableDrive.id) }) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = stringResource(R.string.trip_detail_repair),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDeleteRequested) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.trip_detail_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Box {
                IconButton(onClick = { showOverflowMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.action_more_options),
                    )
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.trip_detail_view_edit_history)) },
                        onClick = {
                            showOverflowMenu = false
                            drive?.let { onViewAuditHistory(it.id) }
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun TripDetailLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

private fun DriveEntity.needsRepairAction(): Boolean =
    classificationStatus == ClassificationStatus.NEEDS_REVIEW ||
        classificationStatus == ClassificationStatus.INCOMPLETE

private fun TripDetailEvent.snackbarMessage(): String = when (this) {
    is TripDetailEvent.ClassifySuccess -> "Classified as ${category.displayName}"
    is TripDetailEvent.SuggestionApplied ->
        "Suggestion applied: ${suggestion.suggestedCategory.displayName}"

    is TripDetailEvent.CalendarHintApplied ->
        "Purpose set from calendar: \"${hint.suggestedPurpose}\""

    TripDetailEvent.VehicleAssigned -> "Vehicle assigned"
    TripDetailEvent.NotesSaved -> "Notes saved"
    TripDetailEvent.LocationsUpdated -> "Locations updated"
    TripDetailEvent.Deleted -> "Trip deleted"
    is TripDetailEvent.Error -> message
}

private fun AttachmentEvent.snackbarMessage(): String = when (this) {
    is AttachmentEvent.AddSuccess -> "Receipt saved"
    is AttachmentEvent.DeleteSuccess -> "Attachment removed"
    is AttachmentEvent.Error -> message
}

private data class TripDetailUiSnapshot(
    val uiState: TripDetailUiState,
    val route: List<RoutePoint>,
    val routeMapState: RouteMapUiState,
    val topSuggestion: TripSuggestion?,
    val calendarHints: List<CalendarHint>,
    val activeException: TripException?,
    val attachments: List<TripAttachment>,
)

private data class TripDetailSuccessActions(
    val onViewAuditHistory: (Int) -> Unit,
    val onViewAttachment: (TripAttachment) -> Unit,
    val onExpandMap: (Int) -> Unit,
    val onShowAttachmentPicker: () -> Unit,
)
