package com.example.mileagetracker.data.places

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchPlaceResponse
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse
import com.google.android.libraries.places.api.net.PlacesClient
import com.example.mileagetracker.domain.places.GeoCoordinates
import com.example.mileagetracker.domain.places.PlacePrediction
import com.example.mileagetracker.domain.places.PlacesService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [PlacesService] backed by the Google Places SDK.
 *
 * Injected as a singleton by Hilt via [RepositoryModule]. The [PlacesClient] is
 * created lazily on first use; the Places SDK is initialised once in
 * `MileageApplication.onCreate()` before any consumer of this service exists.
 */
@Singleton
class GooglePlacesService @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlacesService {

    private val placesClient: PlacesClient by lazy { Places.createClient(context) }

    // Errors propagate to the caller so the ViewModel can surface UI feedback;
    // the service no longer silently returns an empty list on failure.
    override suspend fun autocomplete(query: String, limit: Int): List<PlacePrediction> {
        val request = FindAutocompletePredictionsRequest.builder().setQuery(query).build()
        val response = placesClient.findAutocompletePredictionsAwait(request)
        return response.autocompletePredictions.take(limit).map { it.toPrediction() }
    }

    // No withContext(Dispatchers.IO): fetchPlaceAwait suspends on the Places SDK's
    // async callback rather than blocking a thread, so a dispatcher hop adds nothing.
    override suspend fun fetchCoordinates(placeId: String): GeoCoordinates {
        val request = FetchPlaceRequest.newInstance(placeId, listOf(Place.Field.LOCATION))
        val location = placesClient.fetchPlaceAwait(request).place.location
            ?: error("No coordinates for place $placeId")
        return GeoCoordinates(location.latitude, location.longitude)
    }

    private fun AutocompletePrediction.toPrediction() = PlacePrediction(
        placeId = placeId,
        primaryText = getPrimaryText(null).toString(),
        secondaryText = getSecondaryText(null).toString(),
        fullText = getFullText(null).toString(),
    )
}

private suspend fun PlacesClient.findAutocompletePredictionsAwait(
    request: FindAutocompletePredictionsRequest,
) = suspendCancellableCoroutine<FindAutocompletePredictionsResponse> { cont ->
    findAutocompletePredictions(request)
        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
}

private suspend fun PlacesClient.fetchPlaceAwait(
    request: FetchPlaceRequest,
) = suspendCancellableCoroutine<FetchPlaceResponse> { cont ->
    fetchPlace(request)
        .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
}
