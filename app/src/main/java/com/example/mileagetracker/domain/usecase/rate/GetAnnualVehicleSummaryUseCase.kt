package com.example.mileagetracker.domain.usecase.rate

import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.seeder.CraRateCatalog
import com.example.mileagetracker.domain.model.CraRate
import com.example.mileagetracker.domain.model.VehicleAnnualSummary
import com.example.mileagetracker.domain.model.toDomain
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.OdometerRepository
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Assembles the annual mileage and CRA reimbursement summary for a
 * single vehicle in a single tax year.
 *
 * ## What it produces
 * A [VehicleAnnualSummary] containing:
 * - Opening and closing odometer readings from [OdometerRepository]
 * - Business / personal / total km from [DriveRepository]
 * - CRA two-tier reimbursement via [CalculateReimbursementUseCase]
 * - Business-use percentage for T2200 / T777 forms
 *
 * ## CRA two-tier reimbursement
 * Business trips are processed in chronological order (oldest first)
 * using a running YTD offset so each trip's reimbursement is computed
 * against the correct tier position:
 * ```
 * ytdKm = 0
 * for each businessDrive in ascending startedAt order:
 *     result = calculateReimbursement(year, drive.distanceKm, ytdKm)
 *     ytdKm += drive.distanceKm
 *     totalReimbursement += result.amountDollars
 * ```
 *
 * ## Year boundaries
 * The caller supplies [taxYear] as a four-digit integer; the use case
 * computes Jan 1 00:00:00 UTC → Dec 31 23:59:59 UTC boundaries
 * internally using [yearBoundaries].
 *
 * ## Rate-not-seeded handling
 * If no CRA rate exists for [taxYear], [reimbursementDollars] and
 * [craRateUsed] are `null` rather than throwing.  The UI can show
 * a "Rate not available" warning without crashing.
 *
 * ## Injection
 * `@Inject constructor` — resolved automatically by Hilt from the
 * singleton repository bindings in [RepositoryModule].  No entry in
 * [UseCaseModule] is required.
 */
class GetAnnualVehicleSummaryUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val odometerRepository: OdometerRepository,
    private val rateRepository: RateRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateReimbursement: CalculateReimbursementUseCase,
) {
    private data class VehicleDriveBreakdown(
        val allDrives: List<DriveEntity>,
        val businessDrives: List<DriveEntity>,
        val personalDrives: List<DriveEntity>,
    )

    /**
     * Builds the [VehicleAnnualSummary] for [vehicleId] in [taxYear].
     *
     * This is a suspend function safe to call from any coroutine;
     * all I/O is dispatched to [Dispatchers.IO] by the repository impls.
     *
     * @param vehicleId the database PK of the vehicle.
     * @param taxYear   the four-digit CRA tax year (e.g. 2026).
     * @return a fully populated [VehicleAnnualSummary].
     */
    suspend operator fun invoke(
        vehicleId: Int,
        taxYear: Int,
    ): VehicleAnnualSummary {
        val (fromMillis, toMillis) = yearBoundaries(taxYear)

        // ── Fetch raw data in parallel-friendly sequential calls ─────────────
        val drives = loadVehicleDrives(vehicleId, fromMillis, toMillis)

        val odometerOpen = odometerRepository.getOpeningEvidenceForVehicleYear(
            vehicleId = vehicleId,
            taxYear = taxYear,
            fromMillis = fromMillis,
            toMillis = toMillis,
        )
        val odometerClose = odometerRepository.getClosingEvidenceForVehicleYear(
            vehicleId = vehicleId,
            taxYear = taxYear,
            fromMillis = fromMillis,
            toMillis = toMillis,
        )

        val regionalRateEntity = resolveRegionalRate(taxYear)
        val craRate: CraRate? = regionalRateEntity?.toDomain()

        // ── Aggregate km ─────────────────────────────────────────────────────
        val businessKm = drives.businessDrives.sumOf { it.distanceKm.toDouble() }
        val personalKm = drives.personalDrives.sumOf { it.distanceKm.toDouble() }
        val totalLoggedKm = drives.allDrives.sumOf { it.distanceKm.toDouble() }

        // ── Business-use percentage ───────────────────────────────────────────
        val businessPercent: Double? = if (totalLoggedKm > 0.0) {
            (businessKm / totalLoggedKm) * 100.0
        } else {
            null
        }

        // ── CRA reimbursement (two-tier, running YTD offset) ──────────────────
        val reimbursementDollars = calculateReimbursementDollars(
            drives.businessDrives,
            regionalRateEntity,
        )

        // ── Odometer boundaries ───────────────────────────────────────────────
        val openKm = odometerOpen?.readingKm
        val closeKm = odometerClose?.readingKm
        val totalOdometerKm: Int? = if (openKm != null && closeKm != null && closeKm >= openKm) {
            closeKm - openKm
        } else {
            null
        }

        return VehicleAnnualSummary(
            vehicleId = vehicleId,
            taxYear = taxYear,
            odometerOpenKm = openKm,
            odometerCloseKm = closeKm,
            odometerTotalKm = totalOdometerKm,
            businessKm = businessKm,
            personalKm = personalKm,
            totalLoggedKm = totalLoggedKm,
            businessPercent = businessPercent,
            reimbursementDollars = reimbursementDollars,
            craRateUsed = craRate,
            tripCount = drives.allDrives.size,
            businessTripCount = drives.businessDrives.size,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun loadVehicleDrives(
        vehicleId: Int,
        fromMillis: Long,
        toMillis: Long,
    ): VehicleDriveBreakdown {
        val allDrives = driveRepository.getAllDrivesForVehicleInYear(vehicleId, fromMillis, toMillis)
        return VehicleDriveBreakdown(
            allDrives = allDrives,
            businessDrives = allDrives.filter { it.tripCategory == TripCategory.BUSINESS },
            personalDrives = allDrives.filter { it.tripCategory == TripCategory.PERSONAL },
        )
    }

    private suspend fun resolveRegionalRate(taxYear: Int): CraRateEntity? {
        val region = settingsRepository.getCraRateRegion().first()
        val catalogRate = region?.let { CraRateCatalog.resolve(taxYear, it) }
        val rateEntity = rateRepository.getRateForYearOnce(taxYear)
        return if (rateEntity != null && catalogRate != null) {
            rateEntity.copy(
                firstTierRateCents = catalogRate.firstTier,
                additionalKmRateCents = catalogRate.additional,
                firstTierLimitKm = catalogRate.firstTierLimitKm,
            )
        } else {
            null
        }
    }

    private fun calculateReimbursementDollars(
        businessDrives: List<DriveEntity>,
        regionalRateEntity: CraRateEntity?,
    ): Double? {
        if (regionalRateEntity == null) return null
        var ytdKm = 0f
        var totalCents = 0
        for (drive in businessDrives) {
            val result = calculateReimbursement.calculate(
                rate = regionalRateEntity,
                newDistanceKm = drive.distanceKm,
                ytdKmBefore = ytdKm,
            )
            totalCents += result.amountCents
            ytdKm += drive.distanceKm
        }
        return totalCents / 100.0
    }

    /**
     * Returns (Jan 1 00:00:00 UTC, Dec 31 23:59:59.999 UTC) epoch millis
     * for [year], computed without `java.time` for API 21 compatibility.
     */
    private fun yearBoundaries(year: Int): Pair<Long, Long> {
        val msPerDay = 24L * 60 * 60 * 1_000
        val baseYear = 1970
        var days = 0L
        for (y in baseYear until year) {
            days += if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 366L else 365L
        }
        val fromMillis = days * msPerDay
        val yearDays = if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 366L else 365L
        val toMillis = fromMillis + yearDays * msPerDay - 1L
        return Pair(fromMillis, toMillis)
    }
}
