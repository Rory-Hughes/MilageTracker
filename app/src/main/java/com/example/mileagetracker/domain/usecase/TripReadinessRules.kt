package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.MissingField

internal object TripReadinessRules {
    private val reportableTripCategories = setOf(
        TripCategory.BUSINESS,
        TripCategory.MEDICAL,
        TripCategory.CHARITY,
    )

    fun isTrackedForReportReadiness(drive: DriveEntity): Boolean =
        drive.deletedAt == null && (
            drive.tripCategory == TripCategory.UNCLASSIFIED ||
                drive.tripCategory in reportableTripCategories
            )

    fun missingReportReadinessFields(drive: DriveEntity): List<MissingField> {
        if (!isTrackedForReportReadiness(drive)) {
            return emptyList()
        }

        val missing = mutableListOf<MissingField>()
        if (drive.tripCategory == TripCategory.UNCLASSIFIED) {
            missing += MissingField.PURPOSE
        }
        if (drive.vehicleId == null || drive.vehicleId == 0) {
            missing += MissingField.VEHICLE
        }
        if (drive.tripCategory !in reportableTripCategories) {
            missing += MissingField.CATEGORY
        }
        if (drive.distanceKm <= 0f) {
            missing += MissingField.DISTANCE
        }
        return missing
    }
}
