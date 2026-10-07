package com.example.mileagetracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a trip that has been captured by [DriveTrackingService] but not
 * yet successfully committed to the main `drives` + `route_points` tables.
 *
 * ## Why a separate buffer table?
 * SQLite transactions can fail mid-write due to storage pressure, OOM kills,
 * or a corrupted DB file.  Writing to this lightweight table first gives us a
 * crash-safe "write-ahead" store: the trip data survives even if the main
 * persistence path throws.  [FlushTripBufferUseCase] / [TripBufferFlushWorker]
 * drain this table at the next safe opportunity.
 *
 * ## Serialisation format
 * [tripJson] and [routePointsJson] store JSON produced by [TripBufferStore].
 * Android's built-in `org.json` library is used — no extra serialisation
 * dependency required.
 *
 * ## Retry cap
 * [retryCount] is incremented by [FlushTripBufferUseCase] on each failed
 * flush attempt.  Rows that reach [MAX_RETRY_COUNT] are abandoned (logged
 * and deleted) to prevent a poison-pill row from blocking the queue forever.
 */
@Entity(tableName = "buffered_trips")
data class BufferedTripEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * JSON snapshot of [DriveEntity] fields at the time the trip ended.
     * Produced and consumed exclusively by [TripBufferStore].
     */
    @ColumnInfo(name = "trip_json")
    val tripJson: String,

    /**
     * JSON array of [RoutePointEntity] snapshots captured during the trip.
     * May be an empty array (`"[]"`) for manually-entered trips.
     */
    @ColumnInfo(name = "route_points_json")
    val routePointsJson: String,

    /** Unix epoch millis when this row was first written. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Number of times [FlushTripBufferUseCase] has attempted and failed to
     * flush this entry.  Rows with [retryCount] ≥ [MAX_RETRY_COUNT] are
     * dropped on the next flush pass.
     */
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
) {
    companion object {
        /** Maximum flush attempts before a buffered trip is abandoned. */
        const val MAX_RETRY_COUNT = 5
    }
}
