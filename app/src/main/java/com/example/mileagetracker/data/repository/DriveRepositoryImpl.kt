package com.example.mileagetracker.data.repository

import com.example.mileagetracker.data.local.dao.DriveDao
import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.toDomain
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.repository.DriveDuplicateRepository
import com.example.mileagetracker.domain.repository.DriveReadRepository
import com.example.mileagetracker.domain.repository.DriveReportingRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.DriveRouteRepository
import com.example.mileagetracker.domain.repository.DriveVehicleReportingRepository
import com.example.mileagetracker.domain.repository.DriveWriteRepository
import com.example.mileagetracker.domain.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepositoryImpl @Inject constructor(
    driveDao: DriveDao,
    routePointDao: RoutePointDao,
    currentUserProvider: CurrentUserProvider,
    syncRepository: SyncRepository,
) : DriveRepository,
    DriveWriteRepository by RoomDriveWriteRepository(
        driveDao,
        DriveSyncCoordinator(driveDao, currentUserProvider, syncRepository),
    ),
    DriveReadRepository by RoomDriveReadRepository(driveDao),
    DriveReportingRepository by RoomDriveReportingRepository(driveDao),
    DriveVehicleReportingRepository by RoomDriveVehicleReportingRepository(driveDao),
    DriveDuplicateRepository by RoomDriveDuplicateRepository(driveDao),
    DriveRouteRepository by RoomDriveRouteRepository(routePointDao) {

    private val assignmentRepository = RoomDriveAssignmentRepository(
        driveDao = driveDao,
        syncCoordinator = DriveSyncCoordinator(driveDao, currentUserProvider, syncRepository),
    )

    suspend fun assignVehicleToDrive(driveId: Int, vehicleId: Int?): Boolean =
        assignmentRepository.assignVehicleToDrive(driveId, vehicleId)

    constructor(
        driveDao: DriveDao,
        routePointDao: RoutePointDao,
    ) : this(
        driveDao = driveDao,
        routePointDao = routePointDao,
        currentUserProvider = object : CurrentUserProvider {
            override fun currentUser() = CurrentUser("test-user", null, null, null)
        },
        syncRepository = NoOpSyncRepository,
    )
}

private class RoomDriveWriteRepository(
    private val driveDao: DriveDao,
    private val syncCoordinator: DriveSyncCoordinator,
) : DriveWriteRepository {
    override suspend fun saveDrive(drive: DriveEntity): Int =
        withContext(Dispatchers.IO) {
            val savedId = driveDao.insertDrive(drive).toInt()
            syncCoordinator.syncAllDrives(drive.driverUid)
            savedId
        }

    override suspend fun updateDrive(drive: DriveEntity): Unit =
        withContext(Dispatchers.IO) {
            val updated = drive.copy(updatedAt = System.currentTimeMillis())
            driveDao.updateDrive(updated)
            syncCoordinator.syncAllDrives(updated.driverUid)
        }

    override suspend fun deleteDrive(driveId: Int): Unit =
        withContext(Dispatchers.IO) {
            val ownerUid = syncCoordinator.ownerForExistingDrive(driveId)
            driveDao.softDeleteDrive(
                id = driveId,
                deletedAt = System.currentTimeMillis(),
            )
            ownerUid?.let { syncCoordinator.syncAllDrives(it) }
        }
}

private class RoomDriveReadRepository(
    private val driveDao: DriveDao,
) : DriveReadRepository {
    override suspend fun getDriveById(id: Int): DriveEntity? =
        withContext(Dispatchers.IO) { driveDao.getDriveById(id) }

    override fun getAllDrives(driverUid: String): Flow<List<DriveEntity>> =
        driveDao.getAllDrives(driverUid)

    override fun getUnclassifiedDrives(driverUid: String): Flow<List<DriveEntity>> =
        driveDao.getUnclassifiedDrives(driverUid)

    override fun getDrivesByVehicle(vehicleId: Int): Flow<List<DriveEntity>> =
        driveDao.getDrivesByVehicle(vehicleId)
}

private class RoomDriveReportingRepository(
    private val driveDao: DriveDao,
) : DriveReportingRepository {
    override fun getBusinessDrivesByDateRange(
        driverUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<DriveEntity>> =
        driveDao.getBusinessDrivesByDateRange(driverUid, fromMillis, toMillis)

    override suspend fun getDrivesForAnnualSummary(
        driverUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): List<DriveEntity> =
        withContext(Dispatchers.IO) {
            driveDao.getDrivesForAnnualSummary(driverUid, fromMillis, toMillis)
        }

    override fun getYearToDateBusinessDistance(
        driverUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<Float?> =
        driveDao.getYearToDateBusinessDistance(driverUid, fromMillis, toMillis)
}

private class RoomDriveVehicleReportingRepository(
    private val driveDao: DriveDao,
) : DriveVehicleReportingRepository {
    override suspend fun getBusinessDrivesForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): List<DriveEntity> = withContext(Dispatchers.IO) {
        driveDao.getBusinessDrivesForVehicleInYear(vehicleId, fromMillis, toMillis)
    }

    override suspend fun getAllDrivesForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): List<DriveEntity> = withContext(Dispatchers.IO) {
        driveDao.getAllDrivesForVehicleInYear(vehicleId, fromMillis, toMillis)
    }
}

private class RoomDriveDuplicateRepository(
    private val driveDao: DriveDao,
) : DriveDuplicateRepository {
    override suspend fun getDrivesInTimeWindow(
        driverUid: String,
        windowStartMs: Long,
        windowEndMs: Long,
        excludeId: Int,
    ): List<DriveEntity> =
        withContext(Dispatchers.IO) {
            driveDao.getDrivesInTimeWindow(driverUid, windowStartMs, windowEndMs, excludeId)
        }
}

private class RoomDriveRouteRepository(
    private val routePointDao: RoutePointDao,
) : DriveRouteRepository {
    override suspend fun getRoutePointsForDrive(driveId: Int): List<RoutePoint> =
        withContext(Dispatchers.IO) {
            routePointDao.getPointsForTripOnce(driveId.toLong()).map { it.toDomain() }
        }
}

private class RoomDriveAssignmentRepository(
    private val driveDao: DriveDao,
    private val syncCoordinator: DriveSyncCoordinator,
) {
    suspend fun assignVehicleToDrive(driveId: Int, vehicleId: Int?): Boolean =
        withContext(Dispatchers.IO) {
            val existing = driveDao.getDriveById(driveId) ?: return@withContext false
            driveDao.updateDrive(
                existing.copy(
                    vehicleId = vehicleId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            syncCoordinator.syncAllDrives(existing.driverUid)
            true
        }
}

private class DriveSyncCoordinator(
    private val driveDao: DriveDao,
    private val currentUserProvider: CurrentUserProvider,
    private val syncRepository: SyncRepository,
) {
    private val syncEnabled = syncRepository !== NoOpSyncRepository

    suspend fun ownerForExistingDrive(driveId: Int): String? =
        if (syncEnabled) {
            driveDao.getDriveById(driveId)?.driverUid ?: currentUserProvider.requireUid()
        } else {
            null
        }

    suspend fun syncAllDrives(ownerUid: String?) {
        if (!syncEnabled) return
        val uid = ownerUid ?: currentUserProvider.requireUid()
        val drives = driveDao.getAllDrivesIncludingDeletedForSync(uid)
        syncRepository.enqueueUpsert(
            entityType = SyncEntityTypes.DRIVES,
            entityId = "all",
            payloadJson = drives.toDrivesPayloadJson(),
        )
    }
}
