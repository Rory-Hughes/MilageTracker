package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.domain.model.RoutePoint
import kotlinx.coroutines.flow.Flow

interface DriveWriteRepository {
    suspend fun saveDrive(drive: DriveEntity): Int

    suspend fun updateDrive(drive: DriveEntity)

    suspend fun deleteDrive(driveId: Int)
}

interface DriveReadRepository {
    suspend fun getDriveById(id: Int): DriveEntity?

    fun getAllDrives(driverUid: String): Flow<List<DriveEntity>>

    fun getUnclassifiedDrives(driverUid: String): Flow<List<DriveEntity>>

    fun getDrivesByVehicle(vehicleId: Int): Flow<List<DriveEntity>>
}

interface DriveReportingRepository {
    fun getBusinessDrivesByDateRange(
        driverUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<DriveEntity>>

    suspend fun getDrivesForAnnualSummary(
        driverUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): List<DriveEntity>

    fun getYearToDateBusinessDistance(
        driverUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<Float?>
}

interface DriveVehicleReportingRepository {
    suspend fun getBusinessDrivesForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): List<DriveEntity>

    suspend fun getAllDrivesForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): List<DriveEntity>
}

interface DriveDuplicateRepository {
    suspend fun getDrivesInTimeWindow(
        driverUid: String,
        windowStartMs: Long,
        windowEndMs: Long,
        excludeId: Int = 0,
    ): List<DriveEntity>
}

interface DriveRouteRepository {
    suspend fun getRoutePointsForDrive(driveId: Int): List<RoutePoint>
}

/**
 * Domain contract for drive-trip persistence operations.
 *
 * The composed parent contracts keep high-level consumers on the existing
 * [DriveRepository] type while allowing smaller implementations and focused
 * test doubles for read, write, reporting, duplicate, and route concerns.
 */
interface DriveRepository :
    DriveWriteRepository,
    DriveReadRepository,
    DriveReportingRepository,
    DriveVehicleReportingRepository,
    DriveDuplicateRepository,
    DriveRouteRepository
