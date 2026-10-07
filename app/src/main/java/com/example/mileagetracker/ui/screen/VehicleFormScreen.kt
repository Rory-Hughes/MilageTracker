package com.example.mileagetracker.ui.screen

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import com.example.mileagetracker.R
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.local.entity.VehicleOwnershipType
import com.example.mileagetracker.ui.theme.Spacing
import com.example.mileagetracker.ui.viewmodel.VehicleSaveRequest
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFormScreen(
    existingVehicle: VehicleEntity? = null,
    ownerUid: String,
    fiscalYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    onSave: (VehicleSaveRequest) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val formState = rememberVehicleFormState(existingVehicle)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (!success) {
            formState.pendingPhotoUri = null
        }
    }
    val requiresOpeningPhoto = formState.requiresOpeningPhoto(existingVehicle)

    VehicleFormScaffold(
        title = if (existingVehicle == null) {
            stringResource(R.string.vehicle_add)
        } else {
            stringResource(R.string.vehicle_edit)
        },
        displayName = formState.displayName,
        make = formState.make,
        model = formState.model,
        year = formState.year,
        licencePlate = formState.licencePlate,
        ownershipType = formState.ownershipType,
        openingOdo = formState.openingOdo,
        openingPhotoUri = formState.pendingPhotoUri,
        requiresOpeningPhoto = requiresOpeningPhoto,
        isOwnershipExpanded = formState.isOwnershipExpanded,
        isSaveEnabled = formState.isSaveEnabled(existingVehicle),
        onDisplayNameChange = { formState.displayName = it },
        onMakeChange = { formState.make = it },
        onModelChange = { formState.model = it },
        onYearChange = { input ->
            if (input.length <= 4) {
                formState.year = input.filter(Char::isDigit)
            }
        },
        onLicencePlateChange = { formState.licencePlate = it },
        onOwnershipExpandedChange = { formState.isOwnershipExpanded = it },
        onOwnershipTypeChange = {
            formState.ownershipType = it
            formState.isOwnershipExpanded = false
        },
        onOpeningOdoChange = { input ->
            formState.openingOdo = input.filter { char -> char.isDigit() || char == '.' }
        },
        onTakeOpeningPhoto = {
            val uri = createVehicleOdometerCameraUri(context)
            formState.pendingPhotoUri = uri.toString()
            cameraLauncher.launch(uri)
        },
        onClearOpeningPhoto = {
            formState.pendingPhotoUri = null
        },
        onCancel = onCancel,
        onSave = {
            onSave(
                VehicleSaveRequest(
                    vehicle = buildVehicleEntity(
                        existingVehicle = existingVehicle,
                        ownerUid = ownerUid,
                        fiscalYear = fiscalYear,
                        displayName = formState.displayName,
                        make = formState.make,
                        model = formState.model,
                        year = formState.year,
                        licencePlate = formState.licencePlate,
                        ownershipType = formState.ownershipType,
                        openingOdometer = formState.openingOdo,
                    ),
                    openingOdometerPhotoUri = formState.pendingPhotoUri
                        ?.takeIf { requiresOpeningPhoto },
                ),
            )
        },
    )
}

@Composable
private fun rememberVehicleFormState(existingVehicle: VehicleEntity?): VehicleFormStateHolder {
    return remember(existingVehicle) {
        VehicleFormStateHolder(
            displayName = existingVehicle?.displayName ?: "",
            make = existingVehicle?.make ?: "",
            model = existingVehicle?.model ?: "",
            year = existingVehicle?.year?.toString() ?: "",
            licencePlate = existingVehicle?.vehicleType ?: "",
            ownershipType = existingVehicle?.ownershipType ?: VehicleOwnershipType.PERSONAL,
            openingOdo = existingVehicle?.openingOdometerKm?.toString() ?: "",
        )
    }
}

private class VehicleFormStateHolder(
    displayName: String,
    make: String,
    model: String,
    year: String,
    licencePlate: String,
    ownershipType: VehicleOwnershipType,
    openingOdo: String,
) {
    var displayName by mutableStateOf(displayName)
    var make by mutableStateOf(make)
    var model by mutableStateOf(model)
    var year by mutableStateOf(year)
    var licencePlate by mutableStateOf(licencePlate)
    var ownershipType by mutableStateOf(ownershipType)
    var openingOdo by mutableStateOf(openingOdo)
    var pendingPhotoUri by mutableStateOf<String?>(null)
    var isOwnershipExpanded by mutableStateOf(false)

    fun requiresOpeningPhoto(existingVehicle: VehicleEntity?): Boolean =
        existingVehicle == null || existingVehicle.openingOdometerKm != openingOdo.toFloatOrNull()

    fun isSaveEnabled(existingVehicle: VehicleEntity?): Boolean {
        if (displayName.isBlank()) return false
        val requiresPhoto = requiresOpeningPhoto(existingVehicle)
        if (!requiresPhoto) return true
        return openingOdo.toFloatOrNull()?.let { it >= 0f } == true && pendingPhotoUri != null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleFormScaffold(
    title: String,
    displayName: String,
    make: String,
    model: String,
    year: String,
    licencePlate: String,
    ownershipType: VehicleOwnershipType,
    openingOdo: String,
    openingPhotoUri: String?,
    requiresOpeningPhoto: Boolean,
    isOwnershipExpanded: Boolean,
    isSaveEnabled: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onMakeChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onLicencePlateChange: (String) -> Unit,
    onOwnershipExpandedChange: (Boolean) -> Unit,
    onOwnershipTypeChange: (VehicleOwnershipType) -> Unit,
    onOpeningOdoChange: (String) -> Unit,
    onTakeOpeningPhoto: () -> Unit,
    onClearOpeningPhoto: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            VehicleIdentitySection(
                displayName = displayName,
                make = make,
                model = model,
                year = year,
                licencePlate = licencePlate,
                onDisplayNameChange = onDisplayNameChange,
                onMakeChange = onMakeChange,
                onModelChange = onModelChange,
                onYearChange = onYearChange,
                onLicencePlateChange = onLicencePlateChange,
            )
            VehicleOwnershipSection(
                ownershipType = ownershipType,
                isExpanded = isOwnershipExpanded,
                onExpandedChange = onOwnershipExpandedChange,
                onOwnershipTypeChange = onOwnershipTypeChange,
            )
            OpeningOdometerField(
                openingOdo = openingOdo,
                onOpeningOdoChange = onOpeningOdoChange,
            )
            if (requiresOpeningPhoto) {
                OpeningOdometerPhotoField(
                    hasPhoto = openingPhotoUri != null,
                    onTakePhoto = onTakeOpeningPhoto,
                    onClearPhoto = onClearOpeningPhoto,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            VehicleFormActions(
                isSaveEnabled = isSaveEnabled,
                onCancel = onCancel,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun OpeningOdometerPhotoField(
    hasPhoto: Boolean,
    onTakePhoto: () -> Unit,
    onClearPhoto: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onTakePhoto,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Text(
                    text = if (hasPhoto) "Opening photo attached" else "Take opening photo",
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            if (hasPhoto) {
                IconButton(onClick = onClearPhoto) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove opening odometer photo",
                    )
                }
            }
        }
        Text(
            text = "Required for CRA odometer evidence.",
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

@Composable
private fun VehicleIdentitySection(
    displayName: String,
    make: String,
    model: String,
    year: String,
    licencePlate: String,
    onDisplayNameChange: (String) -> Unit,
    onMakeChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onLicencePlateChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = { Text(stringResource(R.string.vehicle_form_display_name_label)) },
        placeholder = { Text(stringResource(R.string.vehicle_form_display_name_hint)) },
        singleLine = true,
        isError = displayName.isBlank(),
        supportingText = if (displayName.isBlank()) {
            { Text(stringResource(R.string.vehicle_form_error_no_name)) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = make,
            onValueChange = onMakeChange,
            label = { Text(stringResource(R.string.vehicle_form_make_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text(stringResource(R.string.vehicle_form_model_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = year,
            onValueChange = onYearChange,
            label = { Text(stringResource(R.string.vehicle_form_year_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = licencePlate,
            onValueChange = onLicencePlateChange,
            label = { Text(stringResource(R.string.vehicle_form_plate_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleOwnershipSection(
    ownershipType: VehicleOwnershipType,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOwnershipTypeChange: (VehicleOwnershipType) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = onExpandedChange,
    ) {
        OutlinedTextField(
            value = ownershipType.name.lowercase().replaceFirstChar(Char::uppercase),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.vehicle_form_ownership_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isExpanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            VehicleOwnershipType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    onClick = { onOwnershipTypeChange(type) },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun OpeningOdometerField(
    openingOdo: String,
    onOpeningOdoChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = openingOdo,
        onValueChange = onOpeningOdoChange,
        label = { Text(stringResource(R.string.odometer_opening_label)) },
        placeholder = { Text(stringResource(R.string.vehicle_form_opening_odometer_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun createVehicleOdometerCameraUri(context: Context): Uri {
    val photoDir = File(context.cacheDir, "odometer_temp").also { it.mkdirs() }
    val file = File(photoDir, "vehicle_opening_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file,
    )
}

@Composable
private fun VehicleFormActions(
    isSaveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
    ) {
        Button(onClick = onCancel) {
            Text(stringResource(R.string.action_cancel))
        }
        Button(
            onClick = onSave,
            enabled = isSaveEnabled,
        ) {
            Text(stringResource(R.string.vehicle_form_save))
        }
    }
}

private fun buildVehicleEntity(
    existingVehicle: VehicleEntity?,
    ownerUid: String,
    fiscalYear: Int,
    displayName: String,
    make: String,
    model: String,
    year: String,
    licencePlate: String,
    ownershipType: VehicleOwnershipType,
    openingOdometer: String,
): VehicleEntity {
    val baseVehicle = existingVehicle ?: VehicleEntity(
        ownerUid = ownerUid,
        displayName = displayName,
        fiscalYear = fiscalYear,
    )
    return baseVehicle.copy(
        displayName = displayName,
        make = make.takeIf { it.isNotBlank() },
        model = model.takeIf { it.isNotBlank() },
        year = year.toIntOrNull(),
        vehicleType = licencePlate.takeIf { it.isNotBlank() },
        ownershipType = ownershipType,
        openingOdometerKm = openingOdometer.toFloatOrNull(),
        updatedAt = System.currentTimeMillis(),
    )
}
