package com.example.mileagetracker.domain.model

/**
 * Pure domain representation of a single GPS fix captured during a trip.
 *
 * This class is framework-free and safe to use in use-case and ViewModel layers.
 * Conversion to/from [RoutePointEntity] is handled in the repository layer.
 */
data class RoutePoint(
    val id: Long = 0,
    val tripId: Long,
    val lat: Double,
    val lng: Double,
    val altitudeMetres: Double?,
    val accuracyMetres: Float?,
    val speedKph: Float?,
    val timestampMs: Long,
)
