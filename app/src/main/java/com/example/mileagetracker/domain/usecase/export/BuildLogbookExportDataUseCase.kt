package com.example.mileagetracker.domain.usecase.export

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.LogbookExportRequest
import com.example.mileagetracker.domain.model.displayLabel
import com.example.mileagetracker.domain.model.resolvedVehicleLabel
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import java.util.Calendar
import javax.inject.Inject

class BuildLogbookExportDataUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val vehicleRepository: VehicleRepository,
    currentUserProvider: CurrentUserProvider,
) {
    private val driverUid = currentUserProvider.requireUid()

    suspend operator fun invoke(request: LogbookExportRequest): PreparedLogbookExport {
        val categories = normalizeCategories(request.categories)
        val requestedVehicle = resolveRequestedVehicle(request.vehicleId)
        val driveScope = loadDriveScope(request, categories)
        val vehiclesById = loadVehiclesById(
            requestedVehicleId = request.vehicleId,
            requestedVehicle = requestedVehicle,
            exportedDrives = driveScope.exportedDrives,
        )
        val sections = buildSections(
            requestedVehicleId = request.vehicleId,
            scopedDrives = driveScope.scopedDrives,
            exportedDrives = driveScope.exportedDrives,
            vehiclesById = vehiclesById,
        )

        return PreparedLogbookExport(
            vehicleId = request.vehicleId,
            taxYear = request.taxYear,
            categories = categories,
            includeRouteDetails = request.includeRouteDetails,
            scopeLabel = requestedVehicle?.displayLabel() ?: "All vehicles",
            scopeToken = request.vehicleId?.let { "vehicle_$it" } ?: "all_vehicles",
            sections = sections,
            totals = buildTotals(
                allDrives = driveScope.scopedDrives.sortedBy { it.startedAt },
                exportedDrives = driveScope.exportedDrives.sortedBy { it.startedAt },
            ),
        )
    }

    private suspend fun resolveRequestedVehicle(vehicleId: Int?): VehicleEntity? =
        vehicleId?.let { requestedId ->
            vehicleRepository.getVehicleById(requestedId)
                ?: throw IllegalArgumentException("Vehicle $requestedId not found.")
        }

    private suspend fun loadDriveScope(
        request: LogbookExportRequest,
        categories: List<TripCategory>,
    ): ExportDriveScope {
        val (fromMillis, toMillis) = yearBounds(request.taxYear)
        val allYearDrives = driveRepository.getDrivesForAnnualSummary(driverUid, fromMillis, toMillis)
        val scopedDrives = scopeDrives(allYearDrives, request.vehicleId)
        val exportedDrives = scopedDrives.filter { it.tripCategory in categories }
        return ExportDriveScope(
            scopedDrives = scopedDrives,
            exportedDrives = exportedDrives,
        )
    }

    private suspend fun loadVehiclesById(
        requestedVehicleId: Int?,
        requestedVehicle: VehicleEntity?,
        exportedDrives: List<DriveEntity>,
    ): Map<Int, VehicleEntity?> {
        val vehiclesById = mutableMapOf<Int, VehicleEntity?>()
        relevantVehicleIds(requestedVehicleId, exportedDrives).forEach { vehicleId ->
            vehiclesById[vehicleId] = if (requestedVehicle?.id == vehicleId) {
                requestedVehicle
            } else {
                vehicleRepository.getVehicleById(vehicleId)
            }
        }
        return vehiclesById
    }

    private fun buildSections(
        requestedVehicleId: Int?,
        scopedDrives: List<DriveEntity>,
        exportedDrives: List<DriveEntity>,
        vehiclesById: Map<Int, VehicleEntity?>,
    ): List<PreparedLogbookSection> = resolveSectionKeys(
        requestedVehicleId = requestedVehicleId,
        exportedDrives = exportedDrives,
        vehiclesById = vehiclesById,
    ).map { vehicleId ->
        buildSection(
            vehicleId = vehicleId,
            scopedDrives = scopedDrives,
            exportedDrives = exportedDrives,
            vehiclesById = vehiclesById,
        )
    }

    private fun buildSection(
        vehicleId: Int?,
        scopedDrives: List<DriveEntity>,
        exportedDrives: List<DriveEntity>,
        vehiclesById: Map<Int, VehicleEntity?>,
    ): PreparedLogbookSection {
        val allSectionDrives = scopedDrives
            .filter { it.vehicleId == vehicleId }
            .sortedBy { it.startedAt }
        val exportedSectionDrives = exportedDrives
            .filter { it.vehicleId == vehicleId }
            .sortedBy { it.startedAt }
        val vehicle = vehicleId?.let(vehiclesById::get)
        val label = resolvedVehicleLabel(vehicleId, vehicle)

        return PreparedLogbookSection(
            vehicleId = vehicleId,
            vehicle = vehicle,
            label = label,
            trips = exportedSectionDrives.map { drive -> drive.toPreparedTrip(label) },
            totals = buildTotals(
                allDrives = allSectionDrives,
                exportedDrives = exportedSectionDrives,
            ),
        )
    }

    private fun normalizeCategories(categories: Set<TripCategory>): List<TripCategory> {
        val selected = if (categories.isEmpty()) {
            setOf(TripCategory.BUSINESS)
        } else {
            categories
        }
        return TripCategory.entries.filter { it in selected }
    }
}

private data class ExportDriveScope(
    val scopedDrives: List<DriveEntity>,
    val exportedDrives: List<DriveEntity>,
)

private fun scopeDrives(
    drives: List<DriveEntity>,
    vehicleId: Int?,
): List<DriveEntity> = vehicleId?.let { requestedId ->
    drives.filter { it.vehicleId == requestedId }
} ?: drives

private fun relevantVehicleIds(
    requestedVehicleId: Int?,
    exportedDrives: List<DriveEntity>,
): Set<Int> = buildSet {
    requestedVehicleId?.let(::add)
    exportedDrives.mapNotNullTo(this) { it.vehicleId }
}

private fun resolveSectionKeys(
    requestedVehicleId: Int?,
    exportedDrives: List<DriveEntity>,
    vehiclesById: Map<Int, VehicleEntity?>,
): List<Int?> = when {
    requestedVehicleId != null -> listOf(requestedVehicleId)
    exportedDrives.isEmpty() -> emptyList()
    else -> exportedDrives
        .map { it.vehicleId }
        .distinct()
        .sortedWith(
            compareBy<Int?>(
                { it == null },
                { resolvedVehicleLabel(it, it?.let(vehiclesById::get)) },
            )
        )
}

private fun buildTotals(
    allDrives: List<DriveEntity>,
    exportedDrives: List<DriveEntity>,
): PreparedLogbookTotals {
    val totalKm = allDrives.sumOf { it.distanceKm.toDouble() }
    val selectedKm = exportedDrives.sumOf { it.distanceKm.toDouble() }
    val selectedUsePercent = if (totalKm > 0.0) {
        (selectedKm / totalKm) * 100.0
    } else {
        0.0
    }
    return PreparedLogbookTotals(
        totalTripCount = allDrives.size,
        exportedTripCount = exportedDrives.size,
        totalKm = totalKm,
        selectedKm = selectedKm,
        selectedUsePercent = selectedUsePercent,
    )
}

private fun yearBounds(year: Int): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(year, Calendar.JANUARY, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = Calendar.getInstance().apply {
        set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
    return start to end
}

private fun DriveEntity.toPreparedTrip(vehicleLabel: String) = PreparedLogbookTrip(
    tripId = id,
    startedAt = startedAt,
    startLocation = startLocation,
    endLocation = endLocation,
    distanceKm = distanceKm.toDouble(),
    tripCategory = tripCategory,
    notes = notes.orEmpty(),
    vehicleLabel = vehicleLabel,
)
