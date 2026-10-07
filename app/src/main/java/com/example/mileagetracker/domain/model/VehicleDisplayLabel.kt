package com.example.mileagetracker.domain.model

import com.example.mileagetracker.data.local.entity.VehicleEntity

fun VehicleEntity.displayLabel(fallbackId: Int = id): String =
    displayName.ifBlank {
        listOfNotNull(make, model).joinToString(" ").ifBlank {
            "Vehicle $fallbackId"
        }
    }

fun resolvedVehicleLabel(
    vehicleId: Int?,
    vehicle: VehicleEntity?,
    unassignedLabel: String = "Unassigned",
): String = when {
    vehicleId == null -> unassignedLabel
    vehicle != null -> vehicle.displayLabel(vehicleId)
    else -> "Vehicle $vehicleId"
}
