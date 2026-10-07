package com.example.mileagetracker.data.repository

import com.example.mileagetracker.di.MapsApiKey
import com.example.mileagetracker.domain.repository.DirectionsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DirectionsRepository] backed by the Google Directions REST API.
 *
 * Injected as a singleton by Hilt via [RepositoryModule]. The [OkHttpClient]
 * and the maps API key are injected (see [NetworkModule]) so this class can be
 * exercised in isolation with a mock web server.
 *
 * ## Dispatcher policy
 * The blocking OkHttp call is dispatched on [Dispatchers.IO], matching the
 * convention used by the other Room-backed repositories.
 */
@Singleton
class GoogleDirectionsRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    @MapsApiKey private val mapsApiKey: String,
) : DirectionsRepository {

    override suspend fun getDrivingDistanceKm(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
    ): Double = withContext(Dispatchers.IO) {
        val origin = URLEncoder.encode("$startLat,$startLng", "UTF-8")
        val destination = URLEncoder.encode("$endLat,$endLng", "UTF-8")
        val url = "https://maps.googleapis.com/maps/api/directions/json" +
            "?origin=$origin&destination=$destination&mode=driving&key=$mapsApiKey"
        // response.use { } guarantees the ResponseBody is closed even if JSONObject
        // throws on malformed JSON — otherwise the connection would leak.
        val json = httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("Directions API HTTP ${response.code}")
            JSONObject(response.body.string())
        }
        val routes = json.optJSONArray("routes")?.takeIf { it.length() > 0 } ?: error("No routes returned")
        val legs = routes.getJSONObject(0).optJSONArray("legs")?.takeIf { it.length() > 0 } ?: error("No legs returned")
        legs.getJSONObject(0).getJSONObject("distance").getDouble("value") / 1000.0
    }
}
