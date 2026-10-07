package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.LogbookEntry
import com.example.mileagetracker.domain.model.LogbookSummary
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Calendar
import javax.inject.Inject

/**
 * Produces the data for [CraLogbookScreen].
 *
 * ## What it returns
 * A [Pair] of:
 * - `List<LogbookEntry>` — one entry per trip in [categories], sorted
 *   chronologically (oldest first, matching CRA logbook convention).
 * - [LogbookSummary] — footer totals including the business-use percentage.
 *
 * ## Categories parameter
 * Defaults to `[TripCategory.BUSINESS]` for backward compatibility.
 * Pass [TripCategory.craEligibleCategories] to include Medical and Charity
 * trips in the logbook view.
 *
 * ## Business-use percentage
 * The denominator for [LogbookSummary.businessUsePercent] is the **total km**
 * for *all* trip categories matching the year + vehicleId filter — not just
 * the filtered categories.  This matches the CRA's definition:
 *
 *   `filteredKm / totalKm * 100`
 *
 * ## Threading
 * All I/O is delegated to the repository implementations and is safe to
 * call from any dispatcher.
 *
 * @param driverUid  Authenticated Firebase UID.
 * @param year       Tax year to filter (e.g. 2026).
 * @param vehicleId  Optional vehicle filter; null → all vehicles.
 * @param categories Trip categories to include; defaults to Business only.
 */
class GetCraLogbookTripsUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val vehicleRepository: VehicleRepository,
    private val resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase,
) {
    suspend operator fun invoke(
        driverUid: String,
        year: Int,
        vehicleId: Int? = null,
        categories: List<TripCategory> = listOf(TripCategory.BUSINESS),
        month: Int? = null,
    ): Pair<List<LogbookEntry>, LogbookSummary> {
        val (yearStart, yearEnd) = periodBounds(year, month)

        val rawDrives = driveRepository
            .getDrivesForAnnualSummary(driverUid, yearStart, yearEnd)
            .let { drives ->
                if (vehicleId != null) drives.filter { it.vehicleId == vehicleId } else drives
            }

        val allDrives = backfillResolvedLocations(rawDrives)

        val vehicleCache = mutableMapOf<Int, String>()

        val filteredDrives = allDrives
            .filter { it.tripCategory in categories }
            .sortedBy { it.startedAt }

        val entries = filteredDrives.map { drive ->
            val vName = drive.vehicleId?.let { vid ->
                vehicleCache.getOrPut(vid) {
                    vehicleRepository.getVehicleById(vid)
                        ?.let { v ->
                            v.displayName.ifBlank {
                                listOfNotNull(v.make, v.model).joinToString(" ")
                                    .ifBlank { "Vehicle $vid" }
                            }
                        } ?: "—"
                }
            } ?: "—"

            LogbookEntry(
                tripId = drive.id,
                date = drive.startedAt,
                origin = drive.startLocation,
                destination = drive.endLocation,
                category = drive.tripCategory,
                businessKm = drive.distanceKm,
                vehicleName = vName,
                vehicleId = drive.vehicleId,
            )
        }

        val totalKm = allDrives.sumOf { it.distanceKm.toDouble() }.toFloat()
        val businessKm = filteredDrives.sumOf { it.distanceKm.toDouble() }.toFloat()
        val pct = if (totalKm > 0f) (businessKm / totalKm * 100f).coerceIn(0f, 100f) else 0f

        val summary = LogbookSummary(
            totalKm = totalKm,
            businessKm = businessKm,
            businessUsePercent = pct,
            vehicleId = vehicleId,
            year = year,
        )

        return entries to summary
    }

    /**
     * Reverse-geocodes drives whose start/end is still stored as raw
     * "lat, lng" coordinates and persists the resolved address back.
     *
     * Drives that already have an address pass through untouched. Per-drive
     * resolution failures fall back to the original coordinates so the
     * logbook still renders.
     */
    private suspend fun backfillResolvedLocations(drives: List<DriveEntity>): List<DriveEntity> {
        val needsResolution = drives.filter(resolveDriveLocationsUseCase::requiresResolution)
        if (needsResolution.isEmpty()) return drives

        val resolvedById: Map<Int, DriveEntity> = coroutineScope {
            needsResolution.map { drive ->
                async {
                    val resolved = runCatching { resolveDriveLocationsUseCase(drive) }
                        .getOrDefault(drive)
                    if (resolved !== drive) {
                        runCatching { driveRepository.updateDrive(resolved) }
                    }
                    drive.id to resolved
                }
            }.awaitAll().toMap()
        }

        return drives.map { drive -> resolvedById[drive.id] ?: drive }
    }

    /**
     * Inclusive epoch-millis bounds for the reporting period.
     *
     * @param month 1–12 to narrow to a single calendar month, or null for the
     *              whole year. Out-of-range values fall back to the full year.
     */
    private fun periodBounds(year: Int, month: Int?): Pair<Long, Long> {
        if (month == null || month !in 1..MONTHS_IN_YEAR) {
            return bounds(year, Calendar.JANUARY, Calendar.DECEMBER)
        }
        val zeroBasedMonth = month - 1
        return bounds(year, zeroBasedMonth, zeroBasedMonth)
    }

    private fun bounds(year: Int, startMonth: Int, endMonth: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            set(year, startMonth, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, endMonth)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return start to end
    }

    private companion object {
        const val MONTHS_IN_YEAR = 12
    }
}
