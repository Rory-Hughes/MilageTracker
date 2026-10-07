package com.example.mileagetracker.ui.viewmodel

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

/**
 * Represents the three possible states of the route map card on TripDetailScreen.
 *
 * - [NoRoute]  : trip has zero GPS points (manual entry) — no map card rendered
 * - [Loading]  : route points are being fetched from Room
 * - [Ready]    : ≥2 points available; all data needed to render the map is present
 */
sealed interface RouteMapUiState {

    /** Trip has no GPS route data (manual entry or single-point trip). */
    data object NoRoute : RouteMapUiState

    /** Route point fetch is in progress. */
    data object Loading : RouteMapUiState

    /**
     * Route is ready to render.
     *
     * @param points      Ordered list of [LatLng] derived from [com.example.mileagetracker.domain.model.RoutePoint]
     *                    domain objects.
     * @param bounds      Camera bounds enclosing all [points] with builder padding applied in
     *                    the composable layer.
     * @param startMarker [LatLng] of the first captured GPS fix (green marker, label "Start").
     * @param endMarker   [LatLng] of the last captured GPS fix (red marker, label "End").
     */
    data class Ready(
        val points: List<LatLng>,
        val bounds: LatLngBounds,
        val startMarker: LatLng,
        val endMarker: LatLng,
        /** True if any RoutePoint in this trip has accuracyMetres > 50f. */
        val hasLowAccuracy: Boolean = false,
    ) : RouteMapUiState
}
