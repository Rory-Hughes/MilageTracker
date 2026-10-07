package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.domain.model.RoutePoint
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for route-point persistence.
 *
 * Separates the domain layer from Room so that use cases and the service
 * depend only on this interface. The concrete implementation lives in
 * [TrackingRepositoryImpl] in the `data` layer.
 */
interface TrackingRepository {

    /**
     * Persists [points] for [tripId] in a single DB transaction.
     * Called once per trip from [DriveTrackingService.stopTracking].
     */
    suspend fun saveRoutePoints(tripId: Long, points: List<RoutePoint>)

    /**
     * Returns a [Flow] of all GPS fixes for [tripId] ordered by [RoutePoint.timestampMs].
     * Emits on every downstream insert (reactive for live-map rendering use cases).
     */
    fun getRoutePoints(tripId: Long): Flow<List<RoutePoint>>
}
