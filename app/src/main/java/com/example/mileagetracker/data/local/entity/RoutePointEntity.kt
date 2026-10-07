package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mileagetracker.domain.model.RoutePoint

/**
 * Room entity that persists a single GPS fix belonging to a recorded trip.
 *
 * ## Foreign key
 * [tripId] is a hard FK to `drives.id`. `ON DELETE CASCADE` ensures that
 * route points are automatically removed when their parent drive is deleted,
 * keeping the database consistent without manual cleanup queries.
 *
 * ## Index strategy
 * An index on `trip_id` is required for the FK and makes
 * [RoutePointDao.getPointsForTrip] a fast range scan rather than a full
 * table scan. A composite index on `(trip_id, timestamp_ms)` is added so
 * that ordered-route queries (needed for map rendering) execute in one pass.
 */
@Entity(
    tableName = "route_points",
    foreignKeys = [
        ForeignKey(
            entity = DriveEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("trip_id"),
        Index(value = ["trip_id", "timestamp_ms"]),
    ],
)
data class RoutePointEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "trip_id")
    val tripId: Long,

    @ColumnInfo(name = "lat")
    val lat: Double,

    @ColumnInfo(name = "lng")
    val lng: Double,

    /** Altitude above WGS-84 ellipsoid in metres. Null when the fix has no altitude. */
    @ColumnInfo(name = "altitude_metres")
    val altitudeMetres: Double?,

    /** Horizontal accuracy radius reported by the OS, in metres. Null when unavailable. */
    @ColumnInfo(name = "accuracy_metres")
    val accuracyMetres: Float?,

    /** Instantaneous speed in km/h derived from [android.location.Location.speed] × 3.6. */
    @ColumnInfo(name = "speed_kph")
    val speedKph: Float?,

    /** Unix epoch millis of this fix — used for ordering the route polyline. */
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
)

/**
 * Maps a [RoutePointEntity] (data layer) to a [RoutePoint] domain model.
 *
 * Called in the repository layer so that the domain and ViewModel layers
 * remain free of any Room/data-layer types.
 */
fun RoutePointEntity.toDomain(): RoutePoint = RoutePoint(
    id             = id,
    tripId         = tripId,
    lat            = lat,
    lng            = lng,
    altitudeMetres = altitudeMetres,
    accuracyMetres = accuracyMetres,
    speedKph       = speedKph,
    timestampMs    = timestampMs,
)
