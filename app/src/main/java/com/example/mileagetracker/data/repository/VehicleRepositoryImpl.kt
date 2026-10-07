package com.example.mileagetracker.data.repository

import androidx.room.withTransaction
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.VehicleDao
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.repository.SyncRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [VehicleRepository].
 *
 * Injected as a singleton by Hilt via [RepositoryModule].
 *
 * ## Dispatcher policy
 * All suspend write operations switch to [Dispatchers.IO] via
 * [withContext].  [Flow]-returning reads delegate directly to the DAO
 * (Room emits on its own IO executor).
 *
 * ## Default-vehicle invariant
 * At most one vehicle per `(ownerUid, fiscalYear)` may carry
 * `isDefault = true`.  [setDefaultVehicle] enforces this by wrapping
 * [VehicleDao.clearDefaultForYear] and the subsequent [VehicleDao.updateVehicle]
 * inside a single [androidx.room.withTransaction] block.
 *
 * ## Hard-delete policy
 * [deleteVehicle] permanently removes a vehicle row.  It must only be
 * called when [getTripCountForVehicle] returns 0, which is enforced by
 * [DeleteVehicleUseCase].  The repository itself does not re-check the
 * count to avoid a TOCTOU race; the use-case layer is the authoritative guard.
 */
@Singleton
class VehicleRepositoryImpl @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val db: MileageDatabase,
    private val syncRepository: SyncRepository,
) : VehicleRepository {
    private val syncEnabled = syncRepository !== NoOpSyncRepository

    // ───────────────────────────────────────────────────────────────────────────
    // Write
    // ───────────────────────────────────────────────────────────────────────────

    override suspend fun saveVehicle(vehicle: VehicleEntity): Int =
        withContext(Dispatchers.IO) {
            val savedId = vehicleDao.insertVehicle(vehicle).toInt()
            syncAllVehicles(vehicle.ownerUid)
            savedId
        }

    override suspend fun updateVehicle(vehicle: VehicleEntity): Unit =
        withContext(Dispatchers.IO) {
            val updated = vehicle.copy(updatedAt = System.currentTimeMillis())
            vehicleDao.updateVehicle(updated)
            syncAllVehicles(updated.ownerUid)
        }

    override suspend fun deactivateVehicle(vehicleId: Int): Unit =
        withContext(Dispatchers.IO) {
            val ownerUid = if (syncEnabled) vehicleDao.getVehicleById(vehicleId)?.ownerUid else null
            vehicleDao.deactivateVehicle(
                id = vehicleId,
                updatedAt = System.currentTimeMillis(),
            )
            ownerUid?.let { syncAllVehicles(it) }
        }

    override suspend fun deleteVehicle(vehicle: VehicleEntity): Unit =
        withContext(Dispatchers.IO) {
            vehicleDao.deleteVehicle(vehicle)
            syncAllVehicles(vehicle.ownerUid)
        }

    /**
     * Atomically promote [vehicleId] to default for the given
     * `(ownerUid, fiscalYear)` pair.
     *
     * Steps (inside a single transaction):
     * 1. Clear `is_default` on every vehicle for this owner + year.
     * 2. Load the target vehicle.
     * 3. Set `is_default = true` and stamp `updated_at`.
     *
     * Throws [IllegalArgumentException] if [vehicleId] does not exist or
     * is inactive.
     */
    override suspend fun setDefaultVehicle(
        vehicleId: Int,
        ownerUid: String,
        fiscalYear: Int,
    ): Unit = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = System.currentTimeMillis()

            vehicleDao.clearDefaultForYear(
                ownerUid = ownerUid,
                fiscalYear = fiscalYear,
                updatedAt = now,
            )

            val vehicle = vehicleDao.getVehicleById(vehicleId)
                ?: throw IllegalArgumentException(
                    "Cannot set default: vehicle $vehicleId not found."
                )

            require(vehicle.isActive) {
                "Cannot set default: vehicle $vehicleId is inactive."
            }

            vehicleDao.updateVehicle(
                vehicle.copy(
                    isDefault = true,
                    updatedAt = now,
                ),
            )
        }
        syncAllVehicles(ownerUid)
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Read
    // ───────────────────────────────────────────────────────────────────────────

    override suspend fun getVehicleById(id: Int): VehicleEntity? =
        withContext(Dispatchers.IO) {
            vehicleDao.getVehicleById(id)
        }

    override suspend fun getDefaultVehicle(
        ownerUid: String,
        fiscalYear: Int,
    ): VehicleEntity? =
        withContext(Dispatchers.IO) {
            vehicleDao.getDefaultVehicle(ownerUid, fiscalYear)
        }

    override suspend fun getTripCountForVehicle(vehicleId: Int): Int =
        withContext(Dispatchers.IO) {
            vehicleDao.countDrivesForVehicle(vehicleId)
        }

    override fun getActiveVehicles(
        ownerUid: String,
        fiscalYear: Int,
    ): Flow<List<VehicleEntity>> =
        vehicleDao.getActiveVehicles(ownerUid, fiscalYear)

    override fun getAllVehiclesForUser(ownerUid: String): Flow<List<VehicleEntity>> =
        vehicleDao.getAllVehiclesForUser(ownerUid)

    private suspend fun syncAllVehicles(ownerUid: String) {
        if (!syncEnabled) return
        syncRepository.enqueueUpsert(
            entityType = SyncEntityTypes.VEHICLES,
            entityId = "all",
            payloadJson = vehicleDao.getAllVehiclesForUserOnce(ownerUid).toVehiclesPayloadJson(),
        )
    }

    constructor(
        vehicleDao: VehicleDao,
        db: MileageDatabase,
    ) : this(vehicleDao, db, NoOpSyncRepository)
}
