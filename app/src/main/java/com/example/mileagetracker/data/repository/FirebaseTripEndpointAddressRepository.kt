package com.example.mileagetracker.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.example.mileagetracker.domain.model.TripEndpointAddresses
import com.example.mileagetracker.domain.repository.TripEndpointAddressRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTripEndpointAddressRepository @Inject constructor(
    private val functions: FirebaseFunctions,
) : TripEndpointAddressRepository {

    override suspend fun reverseGeocodeTripEndpoints(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
    ): TripEndpointAddresses {
        val payload = mapOf(
            "start" to mapOf(
                "latitude" to startLat,
                "longitude" to startLng,
            ),
            "end" to mapOf(
                "latitude" to endLat,
                "longitude" to endLng,
            ),
        )
        val response = functions
            .getHttpsCallable("reverseGeocodeTripEndpoints")
            .call(payload)
            .await()
            .data as? Map<*, *> ?: emptyMap<Any, Any>()

        return TripEndpointAddresses(
            startAddress = response["startAddress"] as? String,
            endAddress = response["endAddress"] as? String,
        )
    }
}
