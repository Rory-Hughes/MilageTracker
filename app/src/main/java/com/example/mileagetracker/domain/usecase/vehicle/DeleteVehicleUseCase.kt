package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.repository.VehicleRepository
import javax.inject.Inject

/**
 * Hard-deletes a vehicle row if and only if it has zero associated trips.
 *
 * ## Business rule
 * A vehicle may only be permanently deleted when it has no drive records
 * (including soft-deleted drives).  Vehicles with trip history must be
 * deactivated instead so that historical reports and audit trails remain
 * intact.
 *
 * ## Throws
 * - [IllegalStateException] if [VehicleRepository.getTripCountForVehicle]
 *   returns a non-zero count.  The message is user-displayable.
 * - [IllegalArgumentException] if the vehicle row cannot be found.
 */
class DeleteVehicleUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    suspend operator fun invoke(vehicle: VehicleEntity) {
        val tripCount = vehicleRepository.getTripCountForVehicle(vehicle.id)
        if (tripCount > 0) {
            throw IllegalStateException(
                "\"${vehicle.displayName}\" has $tripCount recorded trip" +
                    (if (tripCount == 1) "" else "s") +
                    " and cannot be deleted. Deactivate it instead to preserve your history."
            )
        }
        vehicleRepository.deleteVehicle(vehicle)
    }
}
