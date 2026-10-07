package com.example.mileagetracker.data.local

import com.example.mileagetracker.data.local.dao.BufferedTripDao
import com.example.mileagetracker.data.local.entity.BufferedTripEntity
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingMetadataEmbedded
import com.example.mileagetracker.domain.model.DetectionSource
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write-ahead buffer for in-progress or failed trip persists.
 *
 * [TripBufferStore] serialises a [DriveEntity] + its [RoutePointEntity] list
 * into JSON and writes them to the `buffered_trips` table **before** the main
 * DB write is attempted.  This guarantees that a trip is never silently lost
 * due to an OOM kill, storage error, or interrupted coroutine.
 *
 * ## Serialisation
 * Android's built-in `org.json` library is used throughout to avoid adding a
 * third-party serialisation dependency.  The JSON schema is internal to this
 * class; callers interact only with [DriveEntity] and [RoutePointEntity].
 *
 * ## Lifecycle
 * ```
 * DriveTrackingService.stopTracking()
 *   └─ TripBufferStore.enqueue(drive, points)      // write-ahead
 *   └─ CreateDriveUseCase(drive)                   // attempt main persist
 *       success → TripBufferStore.remove(bufferId)  // clear buffer entry
 *       failure → leave in buffer for FlushTripBufferUseCase
 * ```
 */
@Singleton
class TripBufferStore @Inject constructor(
    private val dao: BufferedTripDao,
) {

    /**
     * Serialises [drive] and [routePoints] and writes a new row to
     * `buffered_trips`.  Returns the generated row ID so the caller can
     * remove it on success.
     */
    suspend fun enqueue(drive: DriveEntity, routePoints: List<RoutePointEntity>): Long {
        val entity = BufferedTripEntity(
            tripJson = drive.toJson().toString(),
            routePointsJson = routePoints.toJson().toString(),
        )
        return dao.insert(entity)
    }

    /** Removes the buffer entry identified by [bufferId] after a successful flush. */
    suspend fun remove(bufferId: Long) = dao.deleteById(bufferId)

    /** Returns all pending buffer entries, oldest first. */
    suspend fun getPending(): List<BufferedTripEntity> = dao.getAll()

    /** Updates a row (used by [FlushTripBufferUseCase] to increment retryCount). */
    suspend fun update(entity: BufferedTripEntity) = dao.update(entity)

    /** Deletes rows that have exhausted [BufferedTripEntity.MAX_RETRY_COUNT]. */
    suspend fun pruneExhausted() = dao.deleteExhausted(BufferedTripEntity.MAX_RETRY_COUNT)

    /** Returns the number of entries currently in the buffer. */
    suspend fun pendingCount(): Int = dao.count()

    // ── Serialisation helpers ───────────────────────────────────────────────────────────

    private fun DriveEntity.toJson(): JSONObject = JSONObject().apply {
        put("startedAt", startedAt)
        put("endedAt", endedAt ?: JSONObject.NULL)
        put("driverUid", driverUid ?: JSONObject.NULL)
        put("startLocation", startLocation)
        put("endLocation", endLocation)
        put("distanceKm", distanceKm)
        put("trackingSource", trackingSource.name)
        put("tripCategory", tripCategory.name)
        put("classificationStatus", classificationStatus.name)
        put("purposeText", purposeText ?: JSONObject.NULL)
        put("vehicleId", vehicleId ?: JSONObject.NULL)
        put("appliedRateSnapshot", appliedRateSnapshot)
        put("classificationConfidence", classificationConfidence ?: JSONObject.NULL)
        put("notes", notes ?: JSONObject.NULL)
        // TrackingMetadata
        trackingMetadata?.let { m ->
            put("meta_detectionSource", m.detectionSource?.name ?: JSONObject.NULL)
            put("meta_confidenceScore", m.confidenceScore ?: JSONObject.NULL)
            put("meta_batteryLevelAtStart", m.batteryLevelAtStart ?: JSONObject.NULL)
            put("meta_batteryLevelAtEnd", m.batteryLevelAtEnd ?: JSONObject.NULL)
            put("meta_wasBatterySaverActive", m.wasBatterySaverActive)
        }
    }

    private fun List<RoutePointEntity>.toJson(): JSONArray = JSONArray().also { arr ->
        forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("tripId", p.tripId)
                    put("lat", p.lat)
                    put("lng", p.lng)
                    put("altitudeMetres", p.altitudeMetres ?: JSONObject.NULL)
                    put("accuracyMetres", p.accuracyMetres ?: JSONObject.NULL)
                    put("speedKph", p.speedKph ?: JSONObject.NULL)
                    put("timestampMs", p.timestampMs)
                }
            )
        }
    }

    // ── Deserialisation ───────────────────────────────────────────────────────────────────

    /**
     * Reconstructs a [DriveEntity] from a [BufferedTripEntity.tripJson] string.
     * Throws [org.json.JSONException] if the JSON is malformed — callers
     * ([FlushTripBufferUseCase]) should catch and handle this as a hard failure.
     */
    fun deserializeDrive(tripJson: String): DriveEntity {
        val j = JSONObject(tripJson)
        return DriveEntity(
            startedAt = j.getLong("startedAt"),
            endedAt = j.optLong("endedAt", -1L).takeIf { it > 0 },
            driverUid = j.optString("driverUid").ifEmpty { null },
            startLocation = j.getString("startLocation"),
            endLocation = j.getString("endLocation"),
            distanceKm = j.getDouble("distanceKm").toFloat(),
            trackingSource = enumValueOf(j.getString("trackingSource")),
            tripCategory = enumValueOf(j.getString("tripCategory")),
            classificationStatus = enumValueOf(j.getString("classificationStatus")),
            purposeText = j.optString("purposeText").ifEmpty { null },
            vehicleId = j.optInt("vehicleId", -1).takeIf { it > 0 },
            appliedRateSnapshot = j.getDouble("appliedRateSnapshot").toFloat(),
            classificationConfidence = j.optDouble("classificationConfidence", Double.NaN)
                .takeUnless { it.isNaN() }?.toFloat(),
            notes = j.optString("notes").ifEmpty { null },
            trackingMetadata = deserializeMetadata(j),
        )
    }

    /**
     * Reconstructs a list of [RoutePointEntity] from a
     * [BufferedTripEntity.routePointsJson] string.
     * The [tripId] parameter is the real DB row ID assigned after the drive
     * row is created; callers must stamp it here before inserting.
     */
    fun deserializeRoutePoints(
        routePointsJson: String,
        tripId: Int,
    ): List<RoutePointEntity> {
        val arr = JSONArray(routePointsJson)
        return (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            RoutePointEntity(
                tripId = tripId.toLong(),
                lat = p.getDouble("lat"),
                lng = p.getDouble("lng"),
                altitudeMetres = p.optDouble("altitudeMetres", Double.NaN).takeUnless { it.isNaN() },
                accuracyMetres = p.optDouble("accuracyMetres", Double.NaN).takeUnless { it.isNaN() }?.toFloat(),
                speedKph = p.optDouble("speedKph", Double.NaN).takeUnless { it.isNaN() }?.toFloat(),
                timestampMs = p.getLong("timestampMs"),
            )
        }
    }

    private fun deserializeMetadata(
        j: JSONObject,
    ): TrackingMetadataEmbedded? {
        val sourceName = j.optString("meta_detectionSource").ifEmpty { null } ?: return null
        return TrackingMetadataEmbedded(
            detectionSource = runCatching {
                DetectionSource.valueOf(sourceName)
            }.getOrNull(),
            confidenceScore = j.optDouble("meta_confidenceScore", Double.NaN).takeUnless { it.isNaN() }?.toFloat(),
            batteryLevelAtStart = j.optInt("meta_batteryLevelAtStart", -1).takeIf { it >= 0 },
            batteryLevelAtEnd = j.optInt("meta_batteryLevelAtEnd", -1).takeIf { it >= 0 },
            wasBatterySaverActive = j.optBoolean("meta_wasBatterySaverActive", false),
        )
    }
}
