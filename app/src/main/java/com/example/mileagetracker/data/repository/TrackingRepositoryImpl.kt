package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.domain.repository.TrackingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [TrackingRepository] backed by [RoutePointDao].
 *
 * Injected as a [@Singleton][Singleton] so the same DAO reference is shared
 * across all consumers (service, view models, use cases).
 */
@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val routePointDao: RoutePointDao,
    private val syncRepository: SyncRepository,
) : TrackingRepository {
    private val syncEnabled = syncRepository !== NoOpSyncRepository

    override suspend fun saveRoutePoints(tripId: Long, points: List<RoutePoint>) {
        val entities = points.map { it.toEntity() }
        routePointDao.insertAll(entities)
        if (!syncEnabled) return
        syncRepository.enqueueUpsert(
            entityType = SyncEntityTypes.ROUTE_POINTS,
            entityId = tripId.toString(),
            payloadJson = routePointDao.getPointsForTripOnce(tripId).toRoutePointsPayloadJson(),
        )
    }

    override fun getRoutePoints(tripId: Long): Flow<List<RoutePoint>> =
        routePointDao.getPointsForTrip(tripId).map { list -> list.map { it.toDomain() } }

    // ── Mappers ────────────────────────────────────────────────────────────────────────

    private fun RoutePoint.toEntity() = RoutePointEntity(
        id = id,
        tripId = tripId,
        lat = lat,
        lng = lng,
        altitudeMetres = altitudeMetres,
        accuracyMetres = accuracyMetres,
        speedKph = speedKph,
        timestampMs = timestampMs,
    )

    private fun RoutePointEntity.toDomain() = RoutePoint(
        id = id,
        tripId = tripId,
        lat = lat,
        lng = lng,
        altitudeMetres = altitudeMetres,
        accuracyMetres = accuracyMetres,
        speedKph = speedKph,
        timestampMs = timestampMs,
    )

    constructor(routePointDao: RoutePointDao) : this(routePointDao, NoOpSyncRepository)
}
