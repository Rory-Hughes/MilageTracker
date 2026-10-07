package com.example.mileagetracker.domain.usecase.drive

import android.util.Log
import com.example.mileagetracker.data.formatting.CanadianAddressFormatter
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.model.TripEndpointAddresses
import com.example.mileagetracker.domain.repository.TripEndpointAddressRepository
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class ResolveDriveLocationsUseCase @Inject constructor(
    private val tripEndpointAddressRepository: TripEndpointAddressRepository,
) {

    fun requiresResolution(drive: DriveEntity): Boolean =
        parseCoordinate(drive.startLocation) != null || parseCoordinate(drive.endLocation) != null

    suspend operator fun invoke(drive: DriveEntity): DriveEntity {
        val startCoordinate = parseCoordinate(drive.startLocation)
        val endCoordinate = parseCoordinate(drive.endLocation)
        val request = createLookupRequest(startCoordinate, endCoordinate)
        val resolved = request?.let { resolveAddresses(it) }

        return resolved?.let {
            drive.copy(
                startLocation = normalizeResolvedAddress(
                    resolvedAddress = it.startAddress,
                    coordinate = startCoordinate,
                    fallback = drive.startLocation,
                ),
                endLocation = normalizeResolvedAddress(
                    resolvedAddress = it.endAddress,
                    coordinate = endCoordinate,
                    fallback = drive.endLocation,
                ),
            )
        } ?: drive
    }

    /**
     * Returns the resolved endpoint addresses, or null when the lookup times
     * out or fails. [runCatching] keeps a single failed coordinate from
     * propagating; the surrounding [withTimeoutOrNull] caps the wall-clock
     * cost. Both failure modes are logged so the geocoding path is no longer
     * silent.
     */
    private suspend fun resolveAddresses(request: LookupRequest): TripEndpointAddresses? {
        val result = withTimeoutOrNull(ADDRESS_LOOKUP_TIMEOUT_MS) {
            runCatching {
                tripEndpointAddressRepository.reverseGeocodeTripEndpoints(
                    startLat = request.start.latitude,
                    startLng = request.start.longitude,
                    endLat = request.end.latitude,
                    endLng = request.end.longitude,
                )
            }.onFailure { error ->
                Log.w(
                    TAG,
                    "Reverse-geocode FAILED: ${error::class.simpleName}: ${error.message}",
                    error,
                )
            }.getOrNull()
        }

        if (result == null) {
            Log.w(
                TAG,
                "Reverse-geocode produced no address (timeout or failure) for " +
                    "start=${request.start.latitude},${request.start.longitude} " +
                    "end=${request.end.latitude},${request.end.longitude}",
            )
        } else {
            Log.d(
                TAG,
                "Reverse-geocode OK: start=${result.startAddress != null}, " +
                    "end=${result.endAddress != null}",
            )
        }
        return result
    }

    private fun createLookupRequest(
        startCoordinate: Coordinate?,
        endCoordinate: Coordinate?,
    ): LookupRequest? {
        val fallbackCoordinate = startCoordinate ?: endCoordinate
        return fallbackCoordinate?.let { fallback ->
            LookupRequest(
                start = startCoordinate ?: fallback,
                end = endCoordinate ?: fallback,
            )
        }
    }

    private fun normalizeResolvedAddress(
        resolvedAddress: String?,
        coordinate: Coordinate?,
        fallback: String,
    ): String =
        resolvedAddress
            ?.takeIf { it.isNotBlank() && coordinate != null }
            ?.let(CanadianAddressFormatter::normalizeRawLocationForExport)
            ?: fallback

    private fun parseCoordinate(raw: String): Coordinate? =
        COORDINATE_PATTERN.matchEntire(raw.trim())?.groupValues?.let { groups ->
            buildCoordinate(
                latitude = groups.getOrNull(1)?.toDoubleOrNull(),
                longitude = groups.getOrNull(2)?.toDoubleOrNull(),
            )
        }

    private fun buildCoordinate(
        latitude: Double?,
        longitude: Double?,
    ): Coordinate? =
        if (latitude != null && longitude != null && hasValidCoordinateRange(latitude, longitude)) {
            Coordinate(latitude = latitude, longitude = longitude)
        } else {
            null
        }

    private fun hasValidCoordinateRange(
        latitude: Double,
        longitude: Double,
    ): Boolean =
        latitude in -90.0..90.0 && longitude in -180.0..180.0

    private data class Coordinate(
        val latitude: Double,
        val longitude: Double,
    )

    private data class LookupRequest(
        val start: Coordinate,
        val end: Coordinate,
    )

    private companion object {
        const val TAG = "ResolveDriveLocations"
        const val ADDRESS_LOOKUP_TIMEOUT_MS = 10_000L
        val COORDINATE_PATTERN = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$")
    }
}
