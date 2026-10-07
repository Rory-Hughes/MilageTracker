package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.repository.VehicleRepository
import javax.inject.Inject

/**
 * Creates a new vehicle and optionally promotes it to the default for
 * its (ownerUid, fiscalYear) pair.
 *
 * ## Validation
 * - [VehicleEntity.displayName] must not be blank.
 * - [VehicleEntity.fiscalYear] must be a plausible 4-digit year
 *   (between 2000 and 2100 inclusive).
 * - [VehicleEntity.openingOdometerKm], if provided, must be ≥ 0.
 * - [VehicleEntity.closingOdometerKm], if provided, must be ≥
 *   [VehicleEntity.openingOdometerKm] (when both are present).
 *
 * ## Default promotion
 * If [setAsDefault] is `true`, [VehicleRepository.setDefaultVehicle] is
 * called after the insert.  This is done in two separate operations
 * rather than a single database transaction because the insert returns
 * the new ID which is needed for the second call.  For Phase 1 this is
 * acceptable; a future refactor can move the pair into a single
 * `db.withTransaction` block if strict atomicity is required.
 */
class CreateVehicleUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    /**
     * @param vehicle      the fully-constructed entity to persist (id = 0).
     * @param setAsDefault if `true`, the new vehicle is immediately set as
     *                     the default for its (ownerUid, fiscalYear).
     * @return the generated database ID of the new vehicle.
     * @throws IllegalArgumentException if validation fails.
     */
    suspend operator fun invoke(
        vehicle: VehicleEntity,
        setAsDefault: Boolean = false,
    ): Int {
        validate(vehicle)

        val newId = vehicleRepository.saveVehicle(vehicle)

        if (setAsDefault) {
            vehicleRepository.setDefaultVehicle(
                vehicleId = newId,
                ownerUid = vehicle.ownerUid,
                fiscalYear = vehicle.fiscalYear,
            )
        }

        return newId
    }

    private fun validate(vehicle: VehicleEntity) {
        require(vehicle.displayName.isNotBlank()) {
            "Vehicle display name must not be blank."
        }
        require(vehicle.fiscalYear in 2000..2100) {
            "Vehicle fiscal year must be between 2000 and 2100, got ${vehicle.fiscalYear}."
        }
        vehicle.openingOdometerKm?.let { opening ->
            require(opening >= 0f) {
                "Opening odometer must be ≥ 0, got $opening."
            }
            vehicle.closingOdometerKm?.let { closing ->
                require(closing >= opening) {
                    "Closing odometer ($closing) must be ≥ opening odometer ($opening)."
                }
            }
        }
    }
}
