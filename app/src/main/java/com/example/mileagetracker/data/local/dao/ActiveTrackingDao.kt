package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mileagetracker.data.local.entity.ActiveRoutePointEntity
import com.example.mileagetracker.data.local.entity.ActiveTrackingSessionEntity

@Dao
interface ActiveTrackingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ActiveTrackingSessionEntity)

    @Insert
    suspend fun insertPoint(point: ActiveRoutePointEntity)

    @Query("SELECT * FROM active_tracking_sessions WHERE id = 1 LIMIT 1")
    suspend fun getSession(): ActiveTrackingSessionEntity?

    @Query("SELECT * FROM active_route_points WHERE session_id = 1 ORDER BY timestamp_ms ASC")
    suspend fun getPoints(): List<ActiveRoutePointEntity>

    @Query("DELETE FROM active_route_points")
    suspend fun deletePoints()

    @Query("DELETE FROM active_tracking_sessions")
    suspend fun deleteSession()

    @Query("SELECT COUNT(*) FROM active_tracking_sessions")
    suspend fun activeCount(): Int
}
