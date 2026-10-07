package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns ALL vehicles for a given user (active **and** inactive, every
 * fiscal year) as a reactive [Flow].
 *
 * This is distinct from [GetActiveVehiclesUseCase], which scopes to a
 * single fiscal year and hides deactivated rows.  [GetVehiclesUseCase]
 * is the source of truth for the Vehicle Management screen, where the
 * user must be able to see and potentially re-activate archived vehicles.
 *
 * ## Sort order
 * Delegated to [VehicleRepository.getAllVehiclesForUser]:
 * fiscal-year descending → display-name ascending.
 */
class GetVehiclesUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    operator fun invoke(ownerUid: String): Flow<List<VehicleEntity>> =
        vehicleRepository.getAllVehiclesForUser(ownerUid)
}
