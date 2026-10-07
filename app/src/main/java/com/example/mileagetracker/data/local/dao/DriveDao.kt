package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mileagetracker.data.local.entity.DriveEntity
import kotlinx.coroutines.flow.Flow

interface DriveWriteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDrive(drive: DriveEntity): Long

    @Update
    suspend fun updateDrive(drive: DriveEntity)

    @Query("UPDATE drives SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id")
    suspend fun softDeleteDrive(id: Int, deletedAt: Long)

    @Query("DELETE FROM drives WHERE id = :id")
    suspend fun hardDeleteDrive(id: Int)

    @Query("DELETE FROM drives WHERE driver_uid = :driverUid")
    suspend fun hardDeleteDrivesForDriver(driverUid: String)
}

interface DriveLookupDao {
    @Query("SELECT * FROM drives WHERE id = :id AND deleted_at IS NULL")
    suspend fun getDriveById(id: Int): DriveEntity?

    @Query(
        """
        SELECT * FROM drives
        WHERE driver_uid = :driverUid
          AND deleted_at IS NULL
        ORDER BY started_at DESC
    """
    )
    fun getAllDrives(driverUid: String): Flow<List<DriveEntity>>

    @Query(
        """
        SELECT * FROM drives
        WHERE driver_uid = :driverUid
          AND deleted_at IS NULL
        ORDER BY started_at DESC
    """
    )
    suspend fun getAllActiveDrivesOnce(driverUid: String): List<DriveEntity>

    @Query(
        """
        SELECT * FROM drives
        WHERE driver_uid = :driverUid
        ORDER BY started_at DESC
    """
    )
    suspend fun getAllDrivesIncludingDeletedForSync(driverUid: String): List<DriveEntity>
}

interface DriveClassificationDao {
    @Query(
        """
        SELECT * FROM drives
        WHERE driver_uid   = :driverUid
          AND trip_category = 'UNCLASSIFIED'
          AND deleted_at   IS NULL
        ORDER BY started_at DESC
    """
    )
    fun getUnclassifiedDrives(driverUid: String): Flow<List<DriveEntity>>

    @Query(
        """
        SELECT * FROM drives
        WHERE driver_uid  = :driverUid
          AND trip_category = 'BUSINESS'
          AND started_at   >= :fromMillis
          AND started_at   <= :toMillis
          AND deleted_at   IS NULL
        ORDER BY started_at DESC
    """
    )
    fun getBusinessDrivesByDateRange(
        driverUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<DriveEntity>>
}

interface DriveVehicleDao {
    @Query(
        """
        SELECT * FROM drives
        WHERE vehicle_id = :vehicleId
          AND deleted_at IS NULL
        ORDER BY started_at DESC
    """
    )
    fun getDrivesByVehicle(vehicleId: Int): Flow<List<DriveEntity>>

    @Query(
        """
        SELECT * FROM drives
        WHERE vehicle_id   = :vehicleId
          AND trip_category = 'BUSINESS'
          AND started_at   >= :fromMillis
          AND started_at   <= :toMillis
          AND deleted_at   IS NULL
        ORDER BY started_at ASC
    """
    )
    suspend fun getBusinessDrivesForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): List<DriveEntity>

    @Query(
        """
        SELECT * FROM drives
        WHERE vehicle_id = :vehicleId
          AND started_at >= :fromMillis
          AND started_at <= :toMillis
          AND deleted_at IS NULL
        ORDER BY started_at ASC
    """
    )
    suspend fun getAllDrivesForVehicleInYear(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): List<DriveEntity>
}

interface DriveAnnualDao {
    @Query(
        """
        SELECT SUM(distance_km) FROM drives
        WHERE driver_uid    = :driverUid
          AND trip_category = 'BUSINESS'
          AND started_at   >= :fromMillis
          AND started_at   <= :toMillis
          AND deleted_at   IS NULL
    """
    )
    fun getYearToDateBusinessDistance(
        driverUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<Float?>

    @Query(
        """
        SELECT * FROM drives
        WHERE driver_uid = :driverUid
          AND started_at >= :fromMillis
          AND started_at <= :toMillis
          AND deleted_at IS NULL
        ORDER BY started_at ASC
    """
    )
    suspend fun getDrivesForAnnualSummary(
        driverUid: String,
        fromMillis: Long,
        toMillis: Long,
    ): List<DriveEntity>
}

interface DriveDuplicateLookupDao {
    @Query(
        """
        SELECT * FROM drives
        WHERE driver_uid = :driverUid
          AND started_at <= :windowEndMs
          AND (ended_at IS NULL OR ended_at >= :windowStartMs)
          AND deleted_at IS NULL
          AND id != :excludeId
        ORDER BY started_at ASC
    """
    )
    suspend fun getDrivesInTimeWindow(
        driverUid: String,
        windowStartMs: Long,
        windowEndMs: Long,
        excludeId: Int = 0,
    ): List<DriveEntity>
}

@Dao
interface DriveDao :
    DriveWriteDao,
    DriveLookupDao,
    DriveClassificationDao,
    DriveVehicleDao,
    DriveAnnualDao,
    DriveDuplicateLookupDao
