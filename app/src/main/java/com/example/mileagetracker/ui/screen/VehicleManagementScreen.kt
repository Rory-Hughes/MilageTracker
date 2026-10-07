package com.example.mileagetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.ui.viewmodel.VehicleManagementViewModel
import com.example.mileagetracker.ui.viewmodel.VehicleUiEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleManagementScreen(
    viewModel: VehicleManagementViewModel = hiltViewModel(),
    onRecordOdometer: (VehicleEntity) -> Unit = {},
) {
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }

    var showFormFor by remember { mutableStateOf<VehicleEntity?>(null) }
    var showFormForNew by remember { mutableStateOf(false) }
    var confirmDeactivate by remember { mutableStateOf<VehicleEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<VehicleEntity?>(null) }

    VehicleManagementEffects(
        viewModel = viewModel,
        snackbarState = snackbarState,
    )
    VehicleManagementDialogs(
        confirmDeactivate = confirmDeactivate,
        onConfirmDeactivateChange = { confirmDeactivate = it },
        onDeactivateConfirmed = { vehicle ->
            viewModel.deactivate(vehicle)
            confirmDeactivate = null
        },
        confirmDelete = confirmDelete,
        onConfirmDeleteChange = { confirmDelete = it },
        onDeleteConfirmed = { vehicle ->
            viewModel.deleteVehicle(vehicle)
            confirmDelete = null
        },
    )

    val formVehicle = showFormFor
    if (showFormForNew || formVehicle != null) {
        VehicleFormScreen(
            existingVehicle = formVehicle,
            ownerUid = viewModel.ownerUid,
            onSave = { entity ->
                showFormFor = null
                showFormForNew = false
                viewModel.saveVehicle(entity)
            },
            onCancel = {
                showFormFor = null
                showFormForNew = false
            },
        )
        return
    }

    VehicleManagementScaffold(
        vehicles = vehicles,
        snackbarState = snackbarState,
        onAddVehicle = { showFormForNew = true },
        onEditVehicle = { showFormFor = it },
        onSetDefault = viewModel::setDefault,
        onDeactivate = { confirmDeactivate = it },
        onReactivate = viewModel::reactivate,
        onDelete = { confirmDelete = it },
        onRecordOdometer = onRecordOdometer,
    )
}

@Composable
private fun VehicleManagementEffects(
    viewModel: VehicleManagementViewModel,
    snackbarState: SnackbarHostState,
) {
    val vehicleSavedMessage = stringResource(R.string.vehicle_saved_snackbar)
    val defaultSetMessage = stringResource(R.string.vehicle_default_set_snackbar)
    val deactivateSuccessMessage = stringResource(R.string.vehicle_deactivated_snackbar)
    val reactivateSuccessMessage = stringResource(R.string.vehicle_reactivated_snackbar)
    val deleteSuccessMessage = stringResource(R.string.vehicle_deleted_snackbar)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                VehicleUiEvent.SaveSuccess -> vehicleSavedMessage
                VehicleUiEvent.DefaultSet -> defaultSetMessage
                VehicleUiEvent.DeactivateSuccess -> deactivateSuccessMessage
                VehicleUiEvent.ReactivateSuccess -> reactivateSuccessMessage
                VehicleUiEvent.DeleteSuccess -> deleteSuccessMessage
                is VehicleUiEvent.Error -> event.message
            }
            snackbarState.showSnackbar(message)
        }
    }
}

@Composable
private fun VehicleManagementDialogs(
    confirmDeactivate: VehicleEntity?,
    onConfirmDeactivateChange: (VehicleEntity?) -> Unit,
    onDeactivateConfirmed: (VehicleEntity) -> Unit,
    confirmDelete: VehicleEntity?,
    onConfirmDeleteChange: (VehicleEntity?) -> Unit,
    onDeleteConfirmed: (VehicleEntity) -> Unit,
) {
    confirmDeactivate?.let { vehicle ->
        VehicleConfirmationDialog(
            title = stringResource(R.string.vehicle_deactivate_confirm_title),
            body = stringResource(
                R.string.vehicle_deactivate_confirm_body,
                vehicle.displayName,
            ),
            confirmLabel = stringResource(R.string.vehicle_deactivate),
            confirmColor = null,
            onDismiss = { onConfirmDeactivateChange(null) },
            onConfirm = { onDeactivateConfirmed(vehicle) },
        )
    }

    confirmDelete?.let { vehicle ->
        VehicleConfirmationDialog(
            title = stringResource(R.string.vehicle_delete_confirm_title),
            body = stringResource(
                R.string.vehicle_delete_permanent_confirm_body,
                vehicle.displayName,
            ),
            confirmLabel = stringResource(R.string.action_delete),
            confirmColor = MaterialTheme.colorScheme.error,
            onDismiss = { onConfirmDeleteChange(null) },
            onConfirm = { onDeleteConfirmed(vehicle) },
        )
    }
}

@Composable
private fun VehicleConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmColor: androidx.compose.ui.graphics.Color?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                if (confirmColor != null) {
                    Text(text = confirmLabel, color = confirmColor)
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleManagementScaffold(
    vehicles: List<VehicleEntity>,
    snackbarState: SnackbarHostState,
    onAddVehicle: () -> Unit,
    onEditVehicle: (VehicleEntity) -> Unit,
    onSetDefault: (Int) -> Unit,
    onDeactivate: (VehicleEntity) -> Unit,
    onReactivate: (VehicleEntity) -> Unit,
    onDelete: (VehicleEntity) -> Unit,
    onRecordOdometer: (VehicleEntity) -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddVehicle) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.vehicle_add),
                )
            }
        },
    ) { padding ->
        VehicleManagementContent(
            vehicles = vehicles,
            padding = padding,
            onAddVehicle = onAddVehicle,
            onEditVehicle = onEditVehicle,
            onSetDefault = onSetDefault,
            onDeactivate = onDeactivate,
            onReactivate = onReactivate,
            onDelete = onDelete,
            onRecordOdometer = onRecordOdometer,
        )
    }
}

@Composable
internal fun VehicleManagementContent(
    vehicles: List<VehicleEntity>,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onAddVehicle: () -> Unit,
    onEditVehicle: (VehicleEntity) -> Unit,
    onSetDefault: (Int) -> Unit,
    onDeactivate: (VehicleEntity) -> Unit,
    onReactivate: (VehicleEntity) -> Unit,
    onDelete: (VehicleEntity) -> Unit,
    onRecordOdometer: (VehicleEntity) -> Unit,
) {
    if (vehicles.isEmpty()) {
        EmptyVehicleState(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            onAdd = onAddVehicle,
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        items(
            items = vehicles,
            key = { it.id },
        ) { vehicle ->
            VehicleCard(
                vehicle = vehicle,
                onEdit = { onEditVehicle(vehicle) },
                onSetDefault = { onSetDefault(vehicle.id) },
                onDeactivate = { onDeactivate(vehicle) },
                onReactivate = { onReactivate(vehicle) },
                onDelete = { onDelete(vehicle) },
                onRecordOdometer = { onRecordOdometer(vehicle) },
            )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
