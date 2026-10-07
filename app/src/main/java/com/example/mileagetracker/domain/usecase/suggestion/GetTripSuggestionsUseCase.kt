package com.example.mileagetracker.domain.usecase.suggestion

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.TripSuggestion
import com.example.mileagetracker.domain.repository.DriveRepository
import javax.inject.Inject
import kotlin.math.min

/**
 * Compares a candidate trip against recent history and returns a ranked list
 * of [TripSuggestion]s.
 *
 * ## Algorithm
 * 1. Fetch the last [HISTORY_TRIP_LIMIT] classified trips for [driverUid]
 *    within the trailing [HISTORY_WINDOW_DAYS] days.
 * 2. Keep only trips whose start/end locations are within [radiusMeters] of
 *    the candidate.  Location proximity is tested via a fast bounding-box
 *    check on the lat/lng encoded in "lat,lng" format; if the location is a
 *    human-readable address string, character-level similarity is used as a
 *    fallback.
 * 3. Group surviving trips by `(category, purposeText, vehicleId)` tuple.
 * 4. Score each group: `matchConfidence = groupSize / HISTORY_TRIP_LIMIT`,
 *    capped at 1.0.
 * 5. Return up to [MAX_SUGGESTIONS] groups sorted descending by confidence;
 *    return an empty list if no group reaches [MIN_CONFIDENCE_THRESHOLD].
 *
 * @param radiusMeters  Maximum origin/destination displacement to count as a
 *                      match.  Default 500 m, overridable for testing.
 */
class GetTripSuggestionsUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
) {
    suspend operator fun invoke(
        candidate: DriveEntity,
        driverUid: String,
        radiusMeters: Float = DEFAULT_RADIUS_METERS,
    ): List<TripSuggestion> {
        val windowEndMs = System.currentTimeMillis()
        val windowStartMs = windowEndMs - HISTORY_WINDOW_DAYS * DAY_MS

        val history = driveRepository.getDrivesForAnnualSummary(
            driverUid = driverUid,
            fromMillis = windowStartMs,
            toMillis = windowEndMs,
        ).filter { it.classificationStatus.name == "USER_CONFIRMED" }
            .filter { nearbyLocation(it.startLocation, candidate.startLocation, radiusMeters) }
            .filter { nearbyLocation(it.endLocation, candidate.endLocation, radiusMeters) }
            .takeLast(HISTORY_TRIP_LIMIT)

        if (history.isEmpty()) return emptyList()

        data class GroupKey(
            val category: TripCategory,
            val purpose: String?,
            val vehicleId: Int?,
        )

        return history
            .groupBy { GroupKey(it.tripCategory, it.purposeText, it.vehicleId) }
            .map { (key, trips) ->
                TripSuggestion(
                    suggestedCategory = key.category,
                    suggestedPurpose = key.purpose,
                    suggestedVehicleId = key.vehicleId,
                    matchConfidence = min(trips.size.toFloat() / HISTORY_TRIP_LIMIT, 1.0f),
                    basedOnTripIds = trips.map { it.id },
                )
            }
            .filter { it.matchConfidence >= MIN_CONFIDENCE_THRESHOLD }
            .sortedByDescending { it.matchConfidence }
            .take(MAX_SUGGESTIONS)
    }

    // ── Location proximity ────────────────────────────────────────────────────

    /**
     * Returns true if [a] and [b] are within [radiusMeters] of each other.
     *
     * Tries to parse both strings as "lat,lng"; falls back to a character
     * similarity check when parsing fails (e.g. reverse-geocoded addresses).
     */
    private fun nearbyLocation(a: String, b: String, radiusMeters: Float): Boolean {
        val aLatLng = parseLatLng(a)
        val bLatLng = parseLatLng(b)
        return if (aLatLng != null && bLatLng != null) {
            haversineMeters(aLatLng.first, aLatLng.second, bLatLng.first, bLatLng.second) <= radiusMeters
        } else {
            addressSimilarity(a, b) >= ADDRESS_SIMILARITY_THRESHOLD
        }
    }

    private fun parseLatLng(location: String): Pair<Double, Double>? {
        val parts = location.split(",")
        if (parts.size != 2) return null
        return try {
            Pair(parts[0].trim().toDouble(), parts[1].trim().toDouble())
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Haversine great-circle distance in metres between two lat/lng points.
     * Accurate enough for the 500 m proximity window.
     */
    private fun haversineMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double,
    ): Float {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) *
            Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2).let { it * it }
        return (2 * r * Math.asin(Math.sqrt(a))).toFloat()
    }

    /**
     * Rough token-overlap similarity for address strings.
     * Returns 0.0–1.0; 1.0 = identical.
     */
    private fun addressSimilarity(a: String, b: String): Float {
        if (a.isBlank() || b.isBlank()) return 0f
        val tokensA = a.lowercase().split(WORD_SPLIT_REGEX).toSet()
        val tokensB = b.lowercase().split(WORD_SPLIT_REGEX).toSet()
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union == 0) 0f else intersection.toFloat() / union.toFloat()
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val DEFAULT_RADIUS_METERS: Float = 500f
        const val HISTORY_TRIP_LIMIT: Int = 20
        const val MAX_SUGGESTIONS: Int = 3
        const val MIN_CONFIDENCE_THRESHOLD: Float = 0.15f
        const val ADDRESS_SIMILARITY_THRESHOLD: Float = 0.5f
        private const val HISTORY_WINDOW_DAYS: Long = 90L
        private const val DAY_MS: Long = 24 * 60 * 60 * 1_000L
        private val WORD_SPLIT_REGEX = Regex("[\\s,]+")
    }
}
