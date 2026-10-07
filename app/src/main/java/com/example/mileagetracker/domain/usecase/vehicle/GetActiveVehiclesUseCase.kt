package com.example.mileagetracker.domain.usecase.vehicle

import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Returns a live [Flow] of active vehicles for [ownerUid] in [fiscalYear],
 * ordered default-first then alphabetically by display name.
 *
 * ## Use
 * Collected by the vehicle picker ViewModel on the trip-creation and
 * trip-classification screens.  The [Flow] re-emits whenever a vehicle
 * is added, renamed, deactivated, or its default status changes — so the
 * picker list stays in sync without manual refresh calls.
 *
 * ## fiscalYear scoping
 * Vehicles are scoped per fiscal year so that a vehicle added for 2026
 * does not appear in a 2025 annual report picker.  The caller (ViewModel)
 * is responsible for supplying the correct fiscal year, typically derived
 * from the currently selected report year or the current calendar year.
 */
class GetActiveVehiclesUseCase @Inject constructor(
    private val vehicleRepository: VehicleRepository,
) {
    /**
     * @param ownerUid   UID of the vehicle owner.
     * @param fiscalYear the tax/fiscal year to scope the query to.
     * @return a cold [Flow] emitting the active vehicle list and
     *         re-emitting on any change.
     */
    operator fun invoke(ownerUid: String, fiscalYear: Int): Flow<List<VehicleEntity>> =
        vehicleRepository.getActiveVehicles(ownerUid, fiscalYear)
}
