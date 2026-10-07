package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for the `route_points` table.
 *
 * All methods are suspend/Flow to enforce off-main-thread execution.
 * The batch [insertAll] is used exclusively by [DriveTrackingService] at trip end.
 */
@Dao
interface RoutePointDao {

    /**
     * Inserts a batch of route points in a single transaction.
     *
     * [OnConflictStrategy.IGNORE] is used so that a retry on a partially
     * persisted batch does not crash — duplicate rows (same auto-generated
     * PK = 0 on first insert) are simply skipped by SQLite.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<RoutePointEntity>)

    /**
     * Returns all GPS fixes for [tripId] ordered chronologically.
     * Emits a new list whenever the underlying rows change (e.g. after insert).
     */
    @Query("SELECT * FROM route_points WHERE trip_id = :tripId ORDER BY timestamp_ms ASC")
    fun getPointsForTrip(tripId: Long): Flow<List<RoutePointEntity>>

    /**
     * One-shot suspend fetch of all GPS fixes for [tripId] ordered chronologically.
     *
     * Use this for single-load contexts (e.g. building a [RouteMapUiState] in
     * a ViewModel) where a live [Flow] subscription is not required.  For
     * live updates during active tracking use [getPointsForTrip] instead.
     */
    @Query("SELECT * FROM route_points WHERE trip_id = :tripId ORDER BY timestamp_ms ASC")
    suspend fun getPointsForTripOnce(tripId: Long): List<RoutePointEntity>

    /**
     * Hard-deletes all route points for [tripId].
     * Normally handled automatically via the CASCADE FK on `drives.id`, but
     * exposed here for explicit cleanup in tests or admin tooling.
     */
    @Query("DELETE FROM route_points WHERE trip_id = :tripId")
    suspend fun deletePointsForTrip(tripId: Long)
}
