package com.example.mileagetracker.domain.model

import com.example.mileagetracker.data.local.entity.TripCategory

/**
 * A single row in the CRA logbook view.
 *
 * @param origin       Start-location string ([DriveEntity.startLocation]).
 * @param destination  End-location string ([DriveEntity.endLocation]).
 * @param category     The trip's [TripCategory]; shown in the logbook's
 *                     "Category" column. Replaces the former free-text
 *                     `purpose` field, which merely echoed the category for
 *                     all but CUSTOM trips.
 */
data class LogbookEntry(
    val tripId: Int,
    val date: Long,
    val origin: String,
    val destination: String,
    val category: TripCategory,
    val businessKm: Float,
    val vehicleName: String,
    val vehicleId: Int? = null,
)
