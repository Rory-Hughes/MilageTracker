package com.example.mileagetracker.domain.usecase.drive

import android.util.Log
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.repository.DriveRepository
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject

/**
 * Re-resolves trip start/end addresses that were stored in an older, verbose
 * format (Nominatim `display_name`) or as raw coordinates, replacing them with
 * the clean `"Street, City, NB, Canada"` format produced by the current
 * geocoding path.
 *
 * ## How it re-resolves
 * A trip's `startLocation` / `endLocation` columns hold *strings*, and once a
 * verbose address was written the original coordinates were lost from those
 * columns. The authoritative coordinates still live in the trip's saved GPS
 * **route points**, so this use case:
 *
 * 1. Lists the user's drives whose stored endpoints look unclean.
 * 2. Reads the first and last [com.example.mileagetracker.domain.model.RoutePoint]
 *    for each (start and end of the recorded path).
 * 3. Rebuilds `"lat, lng"` strings from those points and runs them back through
 *    [ResolveDriveLocationsUseCase] — which now formats via the structured
 *    Nominatim parser.
 * 4. Writes the cleaned addresses back, but only when **both** endpoints
 *    successfully resolved (never downgrading a stored address to bare
 *    coordinates on a failed lookup).
 *
 * Trips without route points (e.g. some manually entered ones) are skipped —
 * there is no coordinate to re-resolve from.
 *
 * ## Cost
 * Each drive costs two reverse-geocode calls, throttled to ~1/sec by
 * [com.example.mileagetracker.data.repository.MapsCoTripEndpointAddressRepository].
 * The worker that drives this runs under a network constraint so it retries
 * when connectivity returns.
 */
class BackfillTripAddressesUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase,
    private val currentUserProvider: CurrentUserProvider,
) {

    suspend operator fun invoke(): Result {
        val driverUid = runCatching { currentUserProvider.requireUid() }.getOrNull()
            ?: run {
                Log.d(TAG, "Skipped: no signed-in user.")
                return Result.Skipped
            }

        val candidates = driveRepository.getAllDrives(driverUid).first()
            .filter(::needsReformatting)

        var updated = 0
        for (drive in candidates) {
            val reformatted = runCatching { reformatFromRoutePoints(drive) }
                .getOrElse { error ->
                    Log.w(
                        TAG,
                        "Drive id=${drive.id} re-resolve failed " +
                            "(${error::class.simpleName}): ${error.message}",
                        error,
                    )
                    null
                } ?: continue

            runCatching { driveRepository.updateDrive(reformatted) }
                .onSuccess { updated++ }
                .onFailure { error ->
                    Log.w(TAG, "Drive id=${drive.id} write-back failed: ${error.message}", error)
                }
        }

        Log.i(TAG, "Address backfill complete: scanned=${candidates.size}, updated=$updated")
        return Result.Completed(scanned = candidates.size, updated = updated)
    }

    /**
     * Returns a copy of [drive] with re-resolved endpoints, or `null` when it
     * can't be improved (no route points, or the lookup did not resolve both
     * endpoints, or nothing actually changed).
     */
    private suspend fun reformatFromRoutePoints(drive: DriveEntity): DriveEntity? {
        val points = driveRepository.getRoutePointsForDrive(drive.id)
        val first = points.firstOrNull()
        val last = points.lastOrNull()
        if (first == null || last == null) return null

        val resolved = resolveDriveLocationsUseCase(
            drive.copy(
                startLocation = formatCoordinate(first.lat, first.lng),
                endLocation = formatCoordinate(last.lat, last.lng),
            ),
        )

        // Drop failed lookups (still coordinate-shaped — never overwrite a stored
        // address with bare coordinates) and edits that wouldn't change anything.
        return resolved
            .takeUnless { resolveDriveLocationsUseCase.requiresResolution(it) }
            ?.let { drive.copy(startLocation = it.startLocation, endLocation = it.endLocation) }
            ?.takeIf {
                it.startLocation != drive.startLocation || it.endLocation != drive.endLocation
            }
    }

    private fun needsReformatting(drive: DriveEntity): Boolean =
        resolveDriveLocationsUseCase.requiresResolution(drive) ||
            looksVerbose(drive.startLocation) ||
            looksVerbose(drive.endLocation)

    /**
     * Heuristic markers of the old verbose Nominatim format. The clean format
     * has at most three commas ("Street, City, Prov, Country"); the verbose
     * breadcrumb has many more and often a bilingual province ("X / Y") or a
     * "… County" segment.
     */
    private fun looksVerbose(location: String): Boolean =
        location.contains(" / ") ||
            location.contains(" County", ignoreCase = true) ||
            location.count { it == ',' } > MAX_CLEAN_COMMAS

    private fun formatCoordinate(lat: Double, lng: Double): String =
        "%1$.5f, %2$.5f".format(Locale.US, lat, lng)

    sealed interface Result {
        data class Completed(val scanned: Int, val updated: Int) : Result
        data object Skipped : Result
    }

    private companion object {
        const val TAG = "TripAddressBackfill"
        const val MAX_CLEAN_COMMAS = 3
    }
}
