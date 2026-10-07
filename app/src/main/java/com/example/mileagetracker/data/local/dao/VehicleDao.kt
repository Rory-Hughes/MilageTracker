package com.example.mileagetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mileagetracker.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

interface VehicleWriteDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVehicle(vehicle: VehicleEntity): Long

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity)

    @Query(
        """
        UPDATE vehicles
        SET    is_active  = 0,
               is_default = 0,
               updated_at = :updatedAt
        WHERE  id = :id
    """
    )
    suspend fun deactivateVehicle(id: Int, updatedAt: Long)

    @Query(
        """
        UPDATE vehicles
        SET    is_default = 0,
               updated_at = :updatedAt
        WHERE  owner_uid   = :ownerUid
          AND  fiscal_year = :fiscalYear
    """
    )
    suspend fun clearDefaultForYear(ownerUid: String, fiscalYear: Int, updatedAt: Long)
}

interface VehicleLookupDao {
    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicleById(id: Int): VehicleEntity?

    @Query(
        """
        SELECT * FROM vehicles
        WHERE  owner_uid   = :ownerUid
          AND  fiscal_year = :fiscalYear
          AND  is_default  = 1
          AND  is_active   = 1
        LIMIT 1
    """
    )
    suspend fun getDefaultVehicle(ownerUid: String, fiscalYear: Int): VehicleEntity?

    @Query("SELECT COUNT(*) FROM drives WHERE vehicle_id = :vehicleId")
    suspend fun countDrivesForVehicle(vehicleId: Int): Int
}

interface VehicleListDao {
    @Query(
        """
        SELECT * FROM vehicles
        WHERE  owner_uid   = :ownerUid
          AND  fiscal_year = :fiscalYear
          AND  is_active   = 1
        ORDER BY is_default DESC, display_name ASC
    """
    )
    fun getActiveVehicles(ownerUid: String, fiscalYear: Int): Flow<List<VehicleEntity>>

    @Query(
        """
        SELECT * FROM vehicles
        WHERE  owner_uid = :ownerUid
        ORDER BY fiscal_year DESC, display_name ASC
    """
    )
    fun getAllVehiclesForUser(ownerUid: String): Flow<List<VehicleEntity>>

    @Query(
        """
        SELECT * FROM vehicles
        WHERE owner_uid = :ownerUid
        ORDER BY fiscal_year DESC, display_name ASC
    """
    )
    suspend fun getAllVehiclesForUserOnce(ownerUid: String): List<VehicleEntity>

    @Query(
        """
        SELECT * FROM vehicles
        WHERE fiscal_year = :fiscalYear
          AND is_active = 1
        ORDER BY owner_uid ASC, display_name ASC
        """
    )
    suspend fun getActiveVehiclesForFiscalYearOnce(fiscalYear: Int): List<VehicleEntity>

    @Query("DELETE FROM vehicles WHERE owner_uid = :ownerUid")
    suspend fun deleteAllForOwner(ownerUid: String)
}

@Dao
interface VehicleDao :
    VehicleWriteDao,
    VehicleLookupDao,
    VehicleListDao
