package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.domain.repository.VehicleRepository
import javax.inject.Inject

/**
 * Promotes a vehicle to the default for a given `(ownerUid, fiscalYear)` pair.
 *
 * Atomicity is guaranteed by [VehicleRepository.setDefaultVehicle], which
 * wraps the clear + set in a single Room transaction.
 *
 * Throws [IllegalArgumentException] if the target vehicle does not exist
 * or is currently inactive — the ViewModel must surface this as a Snackbar
 * error rather than letting it crash silently.
 *
 * @param vehicleId  Row id of the vehicle to promote.
 * @param ownerUid   Firebase UID (or local UID) of the vehicle owner.
 * @param fiscalYear The tax year in which to set this vehicle as default.
 */
class SetDefaultVehicleUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    suspend operator fun invoke(
        vehicleId: Int,
        ownerUid: String,
        fiscalYear: Int,
    ) = vehicleRepository.setDefaultVehicle(vehicleId, ownerUid, fiscalYear)
}
