package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.domain.model.TripEndpointAddresses

interface TripEndpointAddressRepository {
    suspend fun reverseGeocodeTripEndpoints(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
    ): TripEndpointAddresses
}
