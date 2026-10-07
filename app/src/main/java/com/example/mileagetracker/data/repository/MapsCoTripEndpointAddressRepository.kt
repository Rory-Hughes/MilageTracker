package com.example.mileagetracker.data.repository

import android.util.Log
import com.example.mileagetracker.data.formatting.NominatimAddressFormatter
import com.example.mileagetracker.di.GeocodeMapsCoApiKey
import com.example.mileagetracker.domain.model.TripEndpointAddresses
import com.example.mileagetracker.domain.repository.TripEndpointAddressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reverse-geocodes trip endpoints by hitting `https://geocode.maps.co/reverse`
 * directly from the client (no Firebase Function in the middle).
 *
 * ## Rate limiting
 * The free tier of geocode.maps.co caps clients at **1 request/second**.
 * A process-wide [Mutex] serialises every call and a `delay()` enforces the
 * minimum interval. Two calls per trip (start + end) means each trip costs
 * roughly two seconds — fine for ad-hoc resolution, slow for large backfills.
 *
 * ## Key handling
 * If [apiKey] is blank (e.g. the developer hasn't set `GEOCODE_MAPS_CO_API_KEY`
 * in `local.properties`) the repository short-circuits and returns nulls
 * rather than firing a guaranteed-401 request.
 *
 * ## Threading
 * The HTTP call runs on [Dispatchers.IO]. OkHttp is itself cancellable via
 * [suspendCancellableCoroutine] so the upstream [withTimeout] in
 * [com.example.mileagetracker.domain.usecase.drive.ResolveDriveLocationsUseCase]
 * actually cancels in-flight network IO instead of leaking sockets.
 */
@Singleton
class MapsCoTripEndpointAddressRepository @Inject constructor(
    private val httpClient: OkHttpClient,
    @param:GeocodeMapsCoApiKey private val apiKey: String,
) : TripEndpointAddressRepository {

    private val rateLimitGate = Mutex()
    private var lastRequestStartMs = 0L

    override suspend fun reverseGeocodeTripEndpoints(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
    ): TripEndpointAddresses {
        if (apiKey.isBlank()) {
            Log.w(
                TAG,
                "GEOCODE_MAPS_CO_API_KEY is unset — skipping reverse-geocode. " +
                    "Add the key to local.properties to enable address resolution.",
            )
            return TripEndpointAddresses(startAddress = null, endAddress = null)
        }
        return TripEndpointAddresses(
            startAddress = resolveOne(startLat, startLng),
            endAddress = resolveOne(endLat, endLng),
        )
    }

    private suspend fun resolveOne(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        throttle()
        val url = "https://geocode.maps.co/reverse".toHttpUrl()
            .newBuilder()
            .addQueryParameter("lat", lat.toString())
            .addQueryParameter("lon", lng.toString())
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("api_key", apiKey)
            .build()
        val request = Request.Builder().url(url).get().build()

        runCatching { httpClient.executeAsync(request) }
            .map { response -> response.use(::parseAddress) }
            .getOrElse { error ->
                Log.w(
                    TAG,
                    "maps.co reverse-geocode failed for ($lat,$lng): " +
                        "${error::class.simpleName}: ${error.message}",
                    error,
                )
                null
            }
    }

    /**
     * Honour the free-tier 1 req/sec limit. Acquires a process-wide mutex
     * so no two callers can race, then sleeps for any remaining interval
     * before letting the next request fire.
     */
    private suspend fun throttle() {
        rateLimitGate.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestStartMs
            if (elapsed in 0 until MIN_INTERVAL_MS) {
                delay(MIN_INTERVAL_MS - elapsed)
            }
            lastRequestStartMs = System.currentTimeMillis()
        }
    }

    /**
     * Prefers a clean address built from the structured `address` object
     * ([NominatimAddressFormatter]); falls back to the verbose `display_name`
     * only when structured fields are absent.
     */
    private fun parseAddress(response: Response): String? {
        if (!response.isSuccessful) {
            Log.w(TAG, "maps.co HTTP ${response.code}: ${response.message}")
            return null
        }
        val body = response.body?.string().orEmpty()
        return runCatching {
            body.takeIf { it.isNotBlank() }?.let { raw ->
                val json = JSONObject(raw)
                val structured = json.optJSONObject("address")
                    ?.let(::toStringMap)
                    ?.let(NominatimAddressFormatter::format)
                structured ?: json.optString("display_name").takeIf { it.isNotBlank() }
            }
        }.getOrElse { error ->
            Log.w(TAG, "maps.co JSON parse failed: ${error.message}", error)
            null
        }
    }

    private fun toStringMap(obj: JSONObject): Map<String, String> = buildMap {
        obj.keys().forEach { key -> put(key, obj.optString(key)) }
    }

    private companion object {
        const val TAG = "MapsCoGeocode"

        /** Free-tier cap is 1 request/sec; add a small margin to avoid 429s. */
        const val MIN_INTERVAL_MS = 1_100L
    }
}

/**
 * OkHttp's `enqueue` adapted to coroutines with cancellation that actually
 * cancels the underlying [Call].
 */
private suspend fun OkHttpClient.executeAsync(request: Request): Response =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response)
            }
        })
    }
