package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.TripAuditDao
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [AuditRepository].
 *
 * Injected as a singleton by Hilt via [RepositoryModule].
 *
 * ## Append-only guarantee
 * No update or delete operations are exposed.  Once a row is inserted
 * it is immutable for the lifetime of the database (barring a GDPR
 * hard-erasure cascade from the parent drive row).
 *
 * ## Dispatcher policy
 * Insert operations switch to [Dispatchers.IO].  Flow reads delegate
 * directly to the DAO and Room emits on its own IO executor.
 *
 * ## changedAt ownership
 * [recordEvent] sets [TripAuditEntity.changedAt] to the current wall-clock
 * time immediately before the insert.  This means the timestamp reflects
 * when the event was *persisted*, not when the caller constructed the
 * request.  For most use-cases these are the same instant; the distinction
 * only matters if a use-case builds a batch of events and inserts them
 * over several ticks (acceptable for Phase 1).
 */
@Singleton
class AuditRepositoryImpl @Inject constructor(
    private val tripAuditDao: TripAuditDao,
    private val syncRepository: SyncRepository,
) : AuditRepository {
    private val syncEnabled = syncRepository !== NoOpSyncRepository

    // ───────────────────────────────────────────────────────────────────────────
    // Write
    // ───────────────────────────────────────────────────────────────────────────

    override suspend fun insertAuditRow(row: TripAuditEntity): Unit =
        withContext(Dispatchers.IO) {
            tripAuditDao.insertAuditRow(row)
            syncTripAudit(row.tripId)
        }

    /**
     * Convenience overload: build and insert a [TripAuditEntity] from
     * its constituent parts.  [changedAt] is set to [System.currentTimeMillis]
     * at insert time so callers never need to supply a timestamp.
     *
     * Typical call sites:
     * ```kotlin
     * // On drive creation:
     * auditRepository.recordEvent(
     *     tripId    = newDriveId,
     *     actorUid  = driverUid,
     *     eventType = AuditEventType.DRIVE_CREATED,
     * )
     *
     * // On category change:
     * auditRepository.recordEvent(
     *     tripId    = driveId,
     *     actorUid  = userUid,
     *     eventType = AuditEventType.CATEGORY_CHANGED,
     *     fieldName = "trip_category",
     *     oldValue  = TripCategory.UNCLASSIFIED.name,
     *     newValue  = TripCategory.BUSINESS.name,
     * )
     * ```
     */
    override suspend fun recordEvent(
        tripId: Int,
        actorUid: String,
        eventType: AuditEventType,
        fieldName: String?,
        oldValue: String?,
        newValue: String?,
    ): Unit = withContext(Dispatchers.IO) {
        tripAuditDao.insertAuditRow(
            TripAuditEntity(
                tripId    = tripId,
                actorUid  = actorUid,
                eventType = eventType,
                fieldName = fieldName,
                oldValue  = oldValue,
                newValue  = newValue,
                changedAt = System.currentTimeMillis(),
            ),
        )
        syncTripAudit(tripId)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Read
    // ───────────────────────────────────────────────────────────────────────────

    override fun getAuditTrailForTrip(tripId: Int): Flow<List<TripAuditEntity>> =
        tripAuditDao.getAuditRowsForTrip(tripId)

    override suspend fun getAuditTrailForTripOnce(tripId: Int): List<TripAuditEntity> =
        withContext(Dispatchers.IO) {
            tripAuditDao.getAuditRowsForTripOnce(tripId)
        }

    override suspend fun getAuditRowsByDateRange(
        fromMillis: Long,
        toMillis: Long,
    ): List<TripAuditEntity> =
        withContext(Dispatchers.IO) {
            tripAuditDao.getAuditRowsByDateRange(fromMillis, toMillis)
        }

    private suspend fun syncTripAudit(tripId: Int) {
        if (!syncEnabled) return
        syncRepository.enqueueUpsert(
            entityType = SyncEntityTypes.TRIP_AUDIT,
            entityId = tripId.toString(),
            payloadJson = tripAuditDao.getAuditRowsForTripOnce(tripId).toTripAuditPayloadJson(),
        )
    }

    constructor(tripAuditDao: TripAuditDao) : this(tripAuditDao, NoOpSyncRepository)
}
