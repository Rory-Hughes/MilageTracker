package com.example.mileagetracker.data.local

import androidx.room.withTransaction
import com.example.mileagetracker.data.local.dao.ActiveTrackingDao
import com.example.mileagetracker.data.local.entity.ActiveRoutePointEntity
import com.example.mileagetracker.data.local.entity.ActiveTrackingSessionEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable checkpoint store for an active GPS session.
 */
@Singleton
class ActiveTripStore @Inject constructor(
    private val database: MileageDatabase,
    private val dao: ActiveTrackingDao,
) {
    private val mutex = Mutex()

    suspend fun begin(session: ActiveTrackingSessionEntity) = mutex.withLock {
        database.withTransaction {
            dao.deletePoints()
            dao.deleteSession()
            dao.upsertSession(session)
        }
    }

    suspend fun append(point: ActiveRoutePointEntity) = mutex.withLock {
        dao.insertPoint(point)
    }

    suspend fun snapshot(): Snapshot? = mutex.withLock {
        val session = dao.getSession() ?: return@withLock null
        Snapshot(session, dao.getPoints())
    }

    suspend fun clear() = mutex.withLock {
        database.withTransaction {
            dao.deletePoints()
            dao.deleteSession()
        }
    }

    suspend fun hasActiveTrip(): Boolean = mutex.withLock { dao.activeCount() > 0 }

    data class Snapshot(
        val session: ActiveTrackingSessionEntity,
        val points: List<ActiveRoutePointEntity>,
    )
}
