package com.example.mileagetracker.domain.repository

import com.example.mileagetracker.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for vehicle persistence operations.
 *
 * ## Default-vehicle invariant
 * At most one vehicle per (ownerUid, fiscalYear) may have
 * [VehicleEntity.isDefault] == true.  This invariant is enforced by
 * [setDefaultVehicle], which wraps [VehicleDao.clearDefaultForYear] +
 * update in a single database transaction.
 */
interface VehicleRepository {

    // ───────────────────────────────────────────────────────────────────────────
    // Write
    // ───────────────────────────────────────────────────────────────────────────

    /** Persist a new vehicle and return its generated database ID. */
    suspend fun saveVehicle(vehicle: VehicleEntity): Int

    /** Persist all changed fields on an existing vehicle. */
    suspend fun updateVehicle(vehicle: VehicleEntity)

    /**
     * Deactivate (archive) a vehicle.  The row is never deleted so
     * historical drive records that reference it continue to render.
     */
    suspend fun deactivateVehicle(vehicleId: Int)

    /**
     * Permanently delete a vehicle row.
     *
     * Callers MUST verify [getTripCountForVehicle] == 0 before calling;
     * this is enforced by [DeleteVehicleUseCase].  Deleting a vehicle that
     * has trips would orphan those drive records.
     */
    suspend fun deleteVehicle(vehicle: VehicleEntity)

    /**
     * Set [vehicleId] as the default for [ownerUid]/[fiscalYear].
     * Atomically clears the current default before setting the new one,
     * guaranteeing the at-most-one-default invariant.
     */
    suspend fun setDefaultVehicle(vehicleId: Int, ownerUid: String, fiscalYear: Int)

    // ───────────────────────────────────────────────────────────────────────────
    // Reads
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * Fetch a vehicle by its primary key regardless of active status.
     * Returns null if the row does not exist.
     */
    suspend fun getVehicleById(id: Int): VehicleEntity?

    /**
     * Fetch the current default vehicle for [ownerUid]/[fiscalYear].
     * Returns null if no default has been set.
     */
    suspend fun getDefaultVehicle(ownerUid: String, fiscalYear: Int): VehicleEntity?

    /**
     * Return the total number of drive rows (including soft-deleted) that
     * reference [vehicleId].  Used by [DeleteVehicleUseCase] to block deletion
     * of vehicles that have trip history.
     */
    suspend fun getTripCountForVehicle(vehicleId: Int): Int

    /**
     * Stream of active vehicles for [ownerUid]/[fiscalYear],
     * default-first then alphabetical.  Used by the vehicle picker.
     */
    fun getActiveVehicles(ownerUid: String, fiscalYear: Int): Flow<List<VehicleEntity>>

    /**
     * Stream of all vehicles for [ownerUid] across all years,
     * including inactive ones.  Used by the Settings / vehicle
     * management screen.
     */
    fun getAllVehiclesForUser(ownerUid: String): Flow<List<VehicleEntity>>
}
