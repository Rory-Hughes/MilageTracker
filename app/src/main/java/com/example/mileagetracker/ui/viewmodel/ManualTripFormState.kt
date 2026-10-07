package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity

data class ManualTripFormState(
    val repairDriveId: Int? = null,
    val startLocation: String = "",
    val endLocation: String = "",
    val startPlaceId: String? = null,
    val endPlaceId: String? = null,
    val distanceKmText: String = "",
    val isDistanceFromMaps: Boolean = false,
    val startedAtMs: Long = 0L,
    val endedAtMs: Long = 0L,
    val category: TripCategory = TripCategory.UNCLASSIFIED,
    val purposeText: String = "",
    val selectedVehicle: VehicleEntity? = null,
    val notes: String = "",
    val fieldErrors: Map<String, String> = emptyMap(),
    val isSaveEnabled: Boolean = false,
    val isSaving: Boolean = false,
) {
    val isRepairMode: Boolean get() = repairDriveId != null

    companion object {
        const val FIELD_START_LOCATION = "startLocation"
        const val FIELD_END_LOCATION = "endLocation"
        const val FIELD_DISTANCE = "distanceKm"
        const val FIELD_START_TIME = "startedAt"
        const val FIELD_END_TIME = "endedAt"
        const val FIELD_PURPOSE = "purposeText"
    }
}
