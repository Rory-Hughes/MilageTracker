package com.example.mileagetracker.domain.repository

/**
 * Domain contract for route-distance lookups via an external directions provider
 * (currently the Google Directions API).
 *
 * The interface lives in the **domain** layer; the concrete implementation
 * ([GoogleDirectionsRepository]) lives in the **data** layer and is injected at
 * runtime via Hilt.  ViewModels depend only on this interface, so the network
 * integration can be mocked in unit tests and swapped (e.g. to the Routes API)
 * without touching UI code.
 */
interface DirectionsRepository {

    /**
     * Returns the driving distance in kilometres between two coordinates.
     *
     * @throws Exception if the route cannot be resolved (no network, HTTP error,
     *         or no route returned by the provider).
     */
    suspend fun getDrivingDistanceKm(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
    ): Double
}
