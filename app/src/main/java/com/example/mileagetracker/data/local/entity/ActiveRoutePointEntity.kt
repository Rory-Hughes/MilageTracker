package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mileagetracker.domain.model.RoutePoint

/**
 * GPS point checkpointed while a trip is still being recorded.
 */
@Entity(
    tableName = "active_route_points",
    foreignKeys = [
        ForeignKey(
            entity = ActiveTrackingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["session_id", "timestamp_ms"])],
)
data class ActiveRoutePointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long = ActiveTrackingSessionEntity.ACTIVE_SESSION_ID,

    @ColumnInfo(name = "lat")
    val lat: Double,

    @ColumnInfo(name = "lng")
    val lng: Double,

    @ColumnInfo(name = "altitude_metres")
    val altitudeMetres: Double?,

    @ColumnInfo(name = "accuracy_metres")
    val accuracyMetres: Float?,

    @ColumnInfo(name = "speed_kph")
    val speedKph: Float?,

    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
)

fun ActiveRoutePointEntity.toDomain(): RoutePoint = RoutePoint(
    id = id,
    tripId = 0,
    lat = lat,
    lng = lng,
    altitudeMetres = altitudeMetres,
    accuracyMetres = accuracyMetres,
    speedKph = speedKph,
    timestampMs = timestampMs,
)
