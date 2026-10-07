package com.example.mileagetracker.data.repository

import androidx.room.withTransaction
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.DriveDao
import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.dao.SyncDao
import com.example.mileagetracker.data.local.dao.TripAuditDao
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.SyncMetadataEntity
import com.example.mileagetracker.data.local.entity.SyncOutboxEntity
import com.example.mileagetracker.data.local.entity.TripAuditEntity
import com.example.mileagetracker.domain.repository.SyncOperation
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.domain.usecase.drive.TrackedDriveAuditEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackedDrivePersistenceRepository @Inject constructor(
    private val db: MileageDatabase,
    private val driveDao: DriveDao,
    private val routePointDao: RoutePointDao,
    private val tripAuditDao: TripAuditDao,
    private val syncDao: SyncDao,
    private val syncRepository: SyncRepository,
) {
    private val syncEnabled = syncRepository !== NoOpSyncRepository

    suspend fun persistTrackedDrive(
        drive: DriveEntity,
        routePoints: List<RoutePointEntity>,
        actorUid: String,
        additionalAuditEvents: List<TrackedDriveAuditEvent> = emptyList(),
    ): Int = db.withTransaction {
        val driveId = driveDao.insertDrive(drive).toInt()
        val stampedRoutePoints = routePoints.map { it.copy(tripId = driveId.toLong()) }

        if (stampedRoutePoints.isNotEmpty()) {
            routePointDao.insertAll(stampedRoutePoints)
        }

        val changedAt = System.currentTimeMillis()
        val auditRows = buildList {
            add(
                TripAuditEntity(
                    tripId = driveId,
                    actorUid = actorUid,
                    eventType = AuditEventType.DRIVE_CREATED,
                    changedAt = changedAt,
                )
            )
            additionalAuditEvents.forEach { event ->
                add(
                    TripAuditEntity(
                        tripId = driveId,
                        actorUid = actorUid,
                        eventType = event.eventType,
                        fieldName = event.fieldName,
                        oldValue = event.oldValue,
                        newValue = event.newValue,
                        changedAt = changedAt,
                    )
                )
            }
        }
        auditRows.forEach { tripAuditDao.insertAuditRow(it) }

        if (syncEnabled) {
            val ownerUid = drive.driverUid ?: error("Tracked drive must have a driverUid for sync.")
            enqueueUpsertLocked(
                ownerUid = ownerUid,
                entityType = SyncEntityTypes.DRIVES,
                entityId = "all",
                payloadJson = driveDao
                    .getAllDrivesIncludingDeletedForSync(ownerUid)
                    .toDrivesPayloadJson(),
            )
            enqueueUpsertLocked(
                ownerUid = ownerUid,
                entityType = SyncEntityTypes.ROUTE_POINTS,
                entityId = driveId.toString(),
                payloadJson = routePointDao
                    .getPointsForTripOnce(driveId.toLong())
                    .toRoutePointsPayloadJson(),
            )
            enqueueUpsertLocked(
                ownerUid = ownerUid,
                entityType = SyncEntityTypes.TRIP_AUDIT,
                entityId = driveId.toString(),
                payloadJson = tripAuditDao
                    .getAuditRowsForTripOnce(driveId)
                    .toTripAuditPayloadJson(),
            )
        }

        driveId
    }

    private suspend fun enqueueUpsertLocked(
        ownerUid: String,
        entityType: String,
        entityId: String,
        payloadJson: String,
    ) {
        val metadata = syncDao.metadata(ownerUid, entityType, entityId)
            ?: SyncMetadataEntity(
                ownerUid = ownerUid,
                entityType = entityType,
                localEntityId = entityId,
            )
        val updatedMetadata = metadata.copy(
            syncState = "PENDING",
            updatedAt = System.currentTimeMillis(),
            deletedAt = null,
        )
        syncDao.upsertMetadata(updatedMetadata)
        syncDao.enqueue(
            SyncOutboxEntity(
                syncId = updatedMetadata.syncId,
                ownerUid = ownerUid,
                entityType = entityType,
                entityId = entityId,
                operation = SyncOperation.UPSERT.name,
                payloadJson = payloadJson,
                baseRevision = metadata.revision,
            )
        )
    }
}
