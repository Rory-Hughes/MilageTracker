package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.repository.VehicleRepository
import com.example.mileagetracker.domain.validation.ValidationResult
import com.example.mileagetracker.domain.validation.VehicleValidator
import javax.inject.Inject

/**
 * Upsert a vehicle (create **or** update) with full validation.
 *
 * ## Create vs update
 * - If [vehicle.id] == `0` → [VehicleRepository.saveVehicle] (INSERT).
 * - If [vehicle.id] > `0`  → [VehicleRepository.updateVehicle] (UPDATE).
 *
 * ## Validation
 * Delegates to [VehicleValidator.validateAll], which runs the identity
 * and ownership rule sets.  Throws [IllegalArgumentException] if any
 * required field is missing or out of range.
 *
 * @return The row `id` of the saved vehicle (new or existing).
 */
class SaveVehicleUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
    private val vehicleValidator: VehicleValidator,
) {
    /**
     * @param vehicle The entity to persist.  Set `id = 0` for new vehicles.
     * @return The persisted vehicle's `id`.
     * @throws IllegalArgumentException if validation fails.
     */
    suspend operator fun invoke(vehicle: VehicleEntity): Int {
        val result = vehicleValidator.validateAll(vehicle)
        if (result is ValidationResult.Invalid) {
            throw IllegalArgumentException(
                result.errors.joinToString("; ") { it.message }
            )
        }
        return if (vehicle.id == 0) {
            vehicleRepository.saveVehicle(vehicle)
        } else {
            vehicleRepository.updateVehicle(vehicle)
            vehicle.id
        }
    }
}
