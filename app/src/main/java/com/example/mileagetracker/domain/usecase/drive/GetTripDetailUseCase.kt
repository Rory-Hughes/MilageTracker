package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.TrackingRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Assembles all data needed by [TripDetailScreen] into a single reactive
 * [Flow<TripDetailResult>].
 *
 * ## What is combined
 * 1. [DriveEntity] — the trip record itself.
 * 2. [List<RoutePoint>] — GPS fixes for map rendering (via [TrackingRepository]).
 * 3. [VehicleEntity?] — the assigned vehicle, or null if none set yet.
 *
 * Keeping the combination here prevents [TripDetailViewModel] from managing
 * three independent StateFlows.
 *
 * ## Drive not found
 * If the drive is soft-deleted or the ID is invalid, emits
 * [TripDetailResult.NotFound] once and completes.
 */
class GetTripDetailUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val trackingRepository: TrackingRepository,
    private val vehicleRepository: VehicleRepository,
) {
    operator fun invoke(driveId: Int): Flow<TripDetailResult> = flow {
        val drive = driveRepository.getDriveById(driveId)
        if (drive == null) {
            emit(TripDetailResult.NotFound)
            return@flow
        }

        val routeFlow = trackingRepository.getRoutePoints(driveId.toLong())

        val vehicleFlow: Flow<VehicleEntity?> = flow {
            emit(
                drive.vehicleId?.let { vehicleRepository.getVehicleById(it) }
            )
        }

        combine(routeFlow, vehicleFlow) { points, vehicle ->
            TripDetailResult.Success(
                drive = drive,
                route = points,
                vehicle = vehicle,
            )
        }.collect { emit(it) }
    }
}

/** Result type returned by [GetTripDetailUseCase]. */
sealed interface TripDetailResult {
    data class Success(
        val drive: DriveEntity,
        val route: List<RoutePoint>,
        val vehicle: VehicleEntity?,
    ) : TripDetailResult

    /** Emitted when the drive ID is invalid or the row is soft-deleted. */
    data object NotFound : TripDetailResult
}
