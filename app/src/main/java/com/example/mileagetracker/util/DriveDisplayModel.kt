package com.example.mileagetracker.util

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory

/**
 * Presentation-layer model for a single drive trip.
 *
 * ## Why a display model?
 * [DriveEntity] stores timestamps as Unix epoch milliseconds
 * ([DriveEntity.startedAt], [DriveEntity.endedAt]) — the legacy
 * `dateString` and `timeString` fields have been removed from the
 * Phase-1 schema.  [DriveDisplayModel] is the single place that
 * converts epoch values to localised, human-readable strings so that
 * composable screens never perform date formatting inline.
 *
 * ## Category labels
 * All [TripCategory] entries are handled.  Task 3.1 added
 * COMMUTE, MEDICAL, CHARITY and CUSTOM — previously these were
 * `TODO()` stubs that would crash at runtime.
 */
data class DriveDisplayModel(
    /** The underlying entity, for passing back to ViewModel actions. */
    val entity: DriveEntity,

    /** Short date of trip start, e.g. "Jun 14". */
    val dateLabel: String,

    /** Time of trip start in device locale, e.g. "8:45 AM". */
    val timeLabel: String,

    /** Distance rounded to one decimal place, e.g. "12.4 km". */
    val distanceLabel: String,

    /** Start → end location summary for the card header. */
    val routeLabel: String,

    /** Human-readable trip category. */
    val categoryLabel: String,
) {
    companion object {
        fun from(entity: DriveEntity): DriveDisplayModel {
            val hasStart = entity.startedAt > 0L
            return DriveDisplayModel(
                entity = entity,
                dateLabel = if (hasStart) DateFormats.uiShortDate(entity.startedAt) else "Unknown date",
                timeLabel = if (hasStart) DateFormats.uiTime(entity.startedAt) else "Unknown time",
                distanceLabel = "${"%,.1f".format(entity.distanceKm)} km",
                routeLabel = "${entity.startLocation} \u2192 ${entity.endLocation}",
                categoryLabel = when (entity.tripCategory) {
                    TripCategory.BUSINESS -> "Business"
                    TripCategory.PERSONAL -> "Personal"
                    TripCategory.COMMUTE -> "Commute"
                    TripCategory.MEDICAL -> "Medical"
                    TripCategory.CHARITY -> "Charity"
                    TripCategory.CUSTOM -> entity.purposeText?.takeIf { it.isNotBlank() }
                        ?: "Custom"
                    TripCategory.UNCLASSIFIED -> "Unclassified"
                },
            )
        }
    }
}
