package com.example.mileagetracker.data.repository

import androidx.room.withTransaction
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.AccountDataDao
import com.example.mileagetracker.data.local.dao.DriveDao
import com.example.mileagetracker.data.local.dao.OdometerCaptureDao
import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.dao.TripAttachmentDao
import com.example.mileagetracker.data.local.dao.TripAuditDao
import com.example.mileagetracker.data.local.dao.UserClassificationRuleDao
import com.example.mileagetracker.data.local.dao.VehicleDao
import com.example.mileagetracker.data.local.dao.VehicleExpenseDao
import com.example.mileagetracker.domain.repository.RemoteSyncRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRestoreApplier @Inject constructor(
    private val db: MileageDatabase,
    private val accountDataDao: AccountDataDao,
    private val driveDao: DriveDao,
    private val routePointDao: RoutePointDao,
    private val tripAuditDao: TripAuditDao,
    private val tripAttachmentDao: TripAttachmentDao,
    private val vehicleDao: VehicleDao,
    private val vehicleExpenseDao: VehicleExpenseDao,
    private val odometerCaptureDao: OdometerCaptureDao,
    private val userClassificationRuleDao: UserClassificationRuleDao,
    private val evidenceSyncStore: EvidenceSyncStore,
) {
    private val restoreContext = SyncRestoreContext(
        accountDataDao = accountDataDao,
        driveDao = driveDao,
        routePointDao = routePointDao,
        tripAuditDao = tripAuditDao,
        tripAttachmentDao = tripAttachmentDao,
        vehicleDao = vehicleDao,
        vehicleExpenseDao = vehicleExpenseDao,
        odometerCaptureDao = odometerCaptureDao,
        userClassificationRuleDao = userClassificationRuleDao,
        evidenceSyncStore = evidenceSyncStore,
    )

    suspend fun apply(ownerUid: String, record: RemoteSyncRecord): Boolean {
        val handler = restoreHandlers[record.entityType] ?: return false
        return db.withTransaction {
            handler(restoreContext, ownerUid, record)
            true
        }
    }

    suspend fun delete(ownerUid: String, record: RemoteSyncRecord): Boolean {
        val handler = deleteHandlers[record.entityType] ?: return false
        return db.withTransaction {
            handler(restoreContext, ownerUid, record)
            true
        }
    }
}

private typealias SyncRestoreHandler = suspend (
    SyncRestoreContext,
    String,
    RemoteSyncRecord,
) -> Unit

private data class SyncRestoreContext(
    val accountDataDao: AccountDataDao,
    val driveDao: DriveDao,
    val routePointDao: RoutePointDao,
    val tripAuditDao: TripAuditDao,
    val tripAttachmentDao: TripAttachmentDao,
    val vehicleDao: VehicleDao,
    val vehicleExpenseDao: VehicleExpenseDao,
    val odometerCaptureDao: OdometerCaptureDao,
    val userClassificationRuleDao: UserClassificationRuleDao,
    val evidenceSyncStore: EvidenceSyncStore,
)

private val restoreHandlers: Map<String, SyncRestoreHandler> = mapOf(
    SyncEntityTypes.DRIVES to { context, ownerUid, record ->
        context.driveDao.hardDeleteDrivesForDriver(ownerUid)
        record.payloadJson.toDriveEntities().forEach { drive ->
            context.driveDao.insertDrive(drive)
        }
    },
    SyncEntityTypes.ROUTE_POINTS to { context, _, record ->
        val tripId = record.entityId.toLong()
        context.routePointDao.deletePointsForTrip(tripId)
        context.routePointDao.insertAll(record.payloadJson.toRoutePointEntities())
    },
    SyncEntityTypes.TRIP_AUDIT to { context, _, record ->
        val tripId = record.entityId.toInt()
        context.tripAuditDao.deleteAuditRowsForTrip(tripId)
        record.payloadJson.toTripAuditEntities().forEach { auditRow ->
            context.tripAuditDao.insertAuditRow(auditRow)
        }
    },
    SyncEntityTypes.VEHICLES to { context, ownerUid, record ->
        context.vehicleDao.deleteAllForOwner(ownerUid)
        record.payloadJson.toVehicleEntities().forEach { vehicle ->
            context.vehicleDao.insertVehicle(vehicle)
        }
    },
    SyncEntityTypes.VEHICLE_EXPENSES to { context, _, record ->
        val vehicleId = record.entityId.toInt()
        context.vehicleExpenseDao.deleteForVehicle(vehicleId)
        record.payloadJson.toVehicleExpenseSyncItems().forEach { item ->
            context.vehicleExpenseDao.insert(
                item.entity.copy(
                    receiptAttachmentUri = localizeEvidence(
                        evidenceSyncStore = context.evidenceSyncStore,
                        remotePath = item.remotePath,
                        localDirectory = "expense_receipts/$vehicleId",
                        localFileName = item.remotePath?.substringAfterLast('/')
                            ?: "${item.entity.id}_receipt",
                    ) ?: item.entity.receiptAttachmentUri,
                ),
            )
        }
    },
    SyncEntityTypes.ODOMETER_CAPTURES to { context, _, record ->
        val vehicleId = record.entityId.toInt()
        context.odometerCaptureDao.deleteForVehicle(vehicleId)
        record.payloadJson.toOdometerCaptureSyncItems().forEach { item ->
            context.odometerCaptureDao.insert(
                item.entity.copy(
                    photoUri = localizeEvidence(
                        evidenceSyncStore = context.evidenceSyncStore,
                        remotePath = item.remotePath,
                        localDirectory = "odometer_photos",
                        localFileName = item.remotePath?.substringAfterLast('/')
                            ?: "${item.entity.id}.jpg",
                    ) ?: item.entity.photoUri,
                ),
            )
        }
    },
    SyncEntityTypes.TRIP_ATTACHMENTS to { context, _, record ->
        val tripId = record.entityId.toInt()
        context.tripAttachmentDao.deleteAllAttachmentsForTrip(tripId)
        record.payloadJson.toTripAttachmentSyncItems().forEach { item ->
            context.tripAttachmentDao.insertAttachment(
                item.entity.copy(
                    uri = localizeEvidence(
                        evidenceSyncStore = context.evidenceSyncStore,
                        remotePath = item.remotePath,
                        localDirectory = "attachments/$tripId",
                        localFileName = item.entity.fileName,
                    ) ?: item.entity.uri,
                ),
            )
        }
    },
    SyncEntityTypes.USER_CLASSIFICATION_RULES to { context, ownerUid, record ->
        context.accountDataDao.deleteClassificationRules(ownerUid)
        record.payloadJson.toUserClassificationRuleEntities().forEach { rule ->
            context.userClassificationRuleDao.upsert(rule)
        }
    },
)

private val deleteHandlers: Map<String, SyncRestoreHandler> = mapOf(
    SyncEntityTypes.DRIVES to { context, ownerUid, _ ->
        context.driveDao.hardDeleteDrivesForDriver(ownerUid)
    },
    SyncEntityTypes.ROUTE_POINTS to { context, _, record ->
        context.routePointDao.deletePointsForTrip(record.entityId.toLong())
    },
    SyncEntityTypes.TRIP_AUDIT to { context, _, record ->
        context.tripAuditDao.deleteAuditRowsForTrip(record.entityId.toInt())
    },
    SyncEntityTypes.VEHICLES to { context, ownerUid, _ ->
        context.vehicleDao.deleteAllForOwner(ownerUid)
    },
    SyncEntityTypes.VEHICLE_EXPENSES to { context, _, record ->
        context.vehicleExpenseDao.deleteForVehicle(record.entityId.toInt())
    },
    SyncEntityTypes.ODOMETER_CAPTURES to { context, _, record ->
        context.odometerCaptureDao.deleteForVehicle(record.entityId.toInt())
    },
    SyncEntityTypes.TRIP_ATTACHMENTS to { context, _, record ->
        context.tripAttachmentDao.deleteAllAttachmentsForTrip(record.entityId.toInt())
    },
    SyncEntityTypes.USER_CLASSIFICATION_RULES to { context, ownerUid, _ ->
        context.accountDataDao.deleteClassificationRules(ownerUid)
    },
)

private suspend fun localizeEvidence(
    evidenceSyncStore: EvidenceSyncStore,
    remotePath: String?,
    localDirectory: String,
    localFileName: String,
): String? {
    if (remotePath.isNullOrBlank()) return null
    return evidenceSyncStore.download(remotePath, localDirectory, localFileName)
}
