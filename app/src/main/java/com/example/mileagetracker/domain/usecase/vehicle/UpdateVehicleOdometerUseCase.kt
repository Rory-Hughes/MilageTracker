package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.domain.repository.VehicleRepository
import javax.inject.Inject

/**
 * Updates the opening and/or closing odometer readings for a vehicle.
 *
 * Odometer updates are a distinct user action and carry validation rules that
 * differ from the general vehicle update path.
 */
class UpdateVehicleOdometerUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    suspend operator fun invoke(
        vehicleId: Int,
        openingKm: Float? = null,
        closingKm: Float? = null,
    ): Boolean {
        if (openingKm == null && closingKm == null) return true

        val vehicle = vehicleRepository.getVehicleById(vehicleId)
        return vehicle?.let {
            validateOdometerUpdate(it.openingOdometerKm, openingKm, closingKm)
            vehicleRepository.updateVehicle(
                it.copy(
                    openingOdometerKm = openingKm ?: it.openingOdometerKm,
                    closingOdometerKm = closingKm ?: it.closingOdometerKm,
                ),
            )
            true
        } ?: false
    }

    private fun validateOdometerUpdate(
        existingOpeningKm: Float?,
        openingKm: Float?,
        closingKm: Float?,
    ) {
        val effectiveOpening = openingKm ?: existingOpeningKm

        openingKm?.let { opening ->
            require(opening >= 0f) {
                "Opening odometer must be >= 0, got $opening."
            }
        }

        closingKm?.let { closing ->
            require(closing >= 0f) {
                "Closing odometer must be >= 0, got $closing."
            }
            effectiveOpening?.let { opening ->
                require(closing >= opening) {
                    "Closing odometer ($closing) must be >= opening odometer ($opening)."
                }
            }
        }
    }
}
