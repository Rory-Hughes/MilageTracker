package com.example.mileagetracker.domain.places

/**
 * A single address autocomplete prediction returned by [PlacesService].
 *
 * This is a layer-neutral domain model so that callers (ViewModels) never depend
 * on the Google Places SDK's `AutocompletePrediction` type directly.
 */
data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullText: String,
)

/** A geographic coordinate resolved from a place id. */
data class GeoCoordinates(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Domain contract for address autocomplete and place geocoding.
 *
 * The interface lives in the **domain** layer; the concrete implementation
 * ([GooglePlacesService]) wraps the Google Places SDK in the **data** layer and
 * is injected via Hilt. Wrapping the SDK behind this interface keeps the
 * callback-based Places API out of the ViewModels, makes autocomplete mockable
 * in unit tests, and lets the wrapper be reused across screens.
 */
interface PlacesService {

    /**
     * Returns up to [limit] autocomplete predictions for [query].
     *
     * @throws Exception if the provider lookup fails, so callers can surface
     * feedback rather than silently showing an empty list.
     */
    suspend fun autocomplete(query: String, limit: Int = 5): List<PlacePrediction>

    /**
     * Resolves the coordinates for a place id.
     *
     * @throws Exception if the place has no coordinates or the lookup fails.
     */
    suspend fun fetchCoordinates(placeId: String): GeoCoordinates
}
