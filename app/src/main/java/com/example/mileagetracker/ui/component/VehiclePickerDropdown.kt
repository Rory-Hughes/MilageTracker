package com.example.mileagetracker.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.mileagetracker.data.local.entity.VehicleEntity

/**
 * Reusable exposed-dropdown composable for picking a vehicle.
 *
 * Used by:
 * - [VehicleManagementScreen] (set-default flow)
 * - [TripDetailScreen]        (Task 3.1 — assign vehicle to a trip)
 * - [ManualTripEntryScreen]   (Task 3.2 — vehicle field in manual entry form)
 *
 * ## Empty list handling
 * When [vehicles] is empty the dropdown renders a disabled field labelled
 * "No vehicles available" so the parent form can still display without
 * crashing.
 *
 * @param vehicles       Active vehicles to display.
 * @param selectedVehicle  Currently selected vehicle, or null if none.
 * @param onVehicleSelected Callback fired with the newly selected vehicle.
 * @param modifier       Optional layout modifier.
 * @param label          Field label (default "Vehicle").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclePickerDropdown(
    vehicles: List<VehicleEntity>,
    selectedVehicle: VehicleEntity?,
    onVehicleSelected: (VehicleEntity) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Vehicle",
) {
    var expanded by remember { mutableStateOf(false) }

    val displayText = selectedVehicle?.let { v ->
        buildString {
            append(v.displayName)
            if (v.make != null || v.model != null) {
                append(" (${listOfNotNull(v.make, v.model, v.year?.toString()).joinToString(" ")})")
            }
        }
    } ?: if (vehicles.isEmpty()) "No vehicles available" else "Select a vehicle"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (vehicles.isNotEmpty()) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = vehicles.isNotEmpty(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            vehicles.forEach { vehicle ->
                val itemLabel = buildString {
                    append(vehicle.displayName)
                    val details = listOfNotNull(vehicle.make, vehicle.model, vehicle.year?.toString())
                    if (details.isNotEmpty()) append(" — ${details.joinToString(" ")}")
                    if (vehicle.isDefault) append(" ★")
                }
                DropdownMenuItem(
                    text = { Text(itemLabel) },
                    onClick = {
                        onVehicleSelected(vehicle)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
