package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.seeder.CraRateCatalog
import com.example.mileagetracker.domain.model.CraRateRegionRequiredException
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.model.ReportPeriod
import com.example.mileagetracker.domain.model.TripSummary
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.rate.RateNotSeededException
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject

/**
 * Aggregates drives into a list of [TripSummary] buckets for the Reports screen.
 */
class GetTripSummaryUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    private val rateRepository: RateRepository,
    private val settingsRepository: SettingsRepository,
) {
    private data class SummaryRateConfig(
        val customRatePerKm: Float?,
        val firstTierRateCents: Int,
        val additionalRateCents: Int,
        val firstTierLimitKm: Int,
    )

    suspend operator fun invoke(
        driverUid: String,
        year: Int,
        period: ReportPeriod,
        vehicleId: Int? = null,
        categories: List<TripCategory> = listOf(TripCategory.BUSINESS),
    ): List<TripSummary> {
        val (yearStart, yearEnd) = yearBounds(year)
        val drives = loadDrives(driverUid, vehicleId, yearStart, yearEnd)
        val rateConfig = resolveRateConfig(year)

        return when (period) {
            ReportPeriod.MONTHLY -> aggregateMonthly(
                drives = drives,
                year = year,
                firstTierCents = rateConfig.firstTierRateCents,
                additionalCents = rateConfig.additionalRateCents,
                firstTierLimitKm = rateConfig.firstTierLimitKm,
                customRatePerKm = rateConfig.customRatePerKm,
                vehicleId = vehicleId,
                categories = categories,
            )

            ReportPeriod.QUARTERLY -> aggregateQuarterly(
                drives = drives,
                year = year,
                firstTierCents = rateConfig.firstTierRateCents,
                additionalCents = rateConfig.additionalRateCents,
                firstTierLimitKm = rateConfig.firstTierLimitKm,
                customRatePerKm = rateConfig.customRatePerKm,
                vehicleId = vehicleId,
                categories = categories,
            )

            ReportPeriod.ANNUAL -> aggregateAnnual(
                drives = drives,
                year = year,
                firstTierCents = rateConfig.firstTierRateCents,
                additionalCents = rateConfig.additionalRateCents,
                firstTierLimitKm = rateConfig.firstTierLimitKm,
                customRatePerKm = rateConfig.customRatePerKm,
                vehicleId = vehicleId,
                categories = categories,
            )
        }
    }

    suspend fun getDrivesForYear(
        driverUid: String,
        year: Int,
        vehicleId: Int? = null,
    ): List<DriveEntity> {
        val (yearStart, yearEnd) = yearBounds(year)
        return driveRepository
            .getDrivesForAnnualSummary(driverUid, yearStart, yearEnd)
            .let { all ->
                if (vehicleId != null) all.filter { it.vehicleId == vehicleId } else all
            }
    }

    private suspend fun loadDrives(
        driverUid: String,
        vehicleId: Int?,
        yearStart: Long,
        yearEnd: Long,
    ): List<DriveEntity> = driveRepository
        .getDrivesForAnnualSummary(driverUid, yearStart, yearEnd)
        .let { all ->
            if (vehicleId != null) all.filter { it.vehicleId == vehicleId } else all
        }

    private suspend fun resolveRateConfig(year: Int): SummaryRateConfig {
        val rateMode = settingsRepository.getRateMode().first()
        if (rateMode == RateMode.CUSTOM) {
            return SummaryRateConfig(
                customRatePerKm = settingsRepository
                    .getCustomRateDollars()
                    .first()
                    .takeIf { it > 0f },
                firstTierRateCents = 0,
                additionalRateCents = 0,
                firstTierLimitKm = 5_000,
            )
        }

        val region = requireCraRateRegion()
        ensureSeededRateExists(year)
        val catalog = requireCatalogRate(year, region)
        return SummaryRateConfig(
            customRatePerKm = null,
            firstTierRateCents = catalog.firstTier,
            additionalRateCents = catalog.additional,
            firstTierLimitKm = catalog.firstTierLimitKm,
        )
    }

    private suspend fun requireCraRateRegion() =
        settingsRepository.getCraRateRegion().first()
            ?: throw CraRateRegionRequiredException()

    private suspend fun ensureSeededRateExists(year: Int) {
        rateRepository.getRateForYearOnce(year)
            ?: throw RateNotSeededException(year)
    }

    private fun requireCatalogRate(
        year: Int,
        region: com.example.mileagetracker.domain.model.CraRateRegion,
    ) = CraRateCatalog.resolve(year, region)
        ?: throw RateNotSeededException(year)

    private fun aggregateMonthly(
        drives: List<DriveEntity>,
        year: Int,
        firstTierCents: Int,
        additionalCents: Int,
        firstTierLimitKm: Int,
        customRatePerKm: Float?,
        vehicleId: Int?,
        categories: List<TripCategory>,
    ): List<TripSummary> {
        val byMonth = drives.groupBy { drive ->
            Calendar.getInstance().apply { timeInMillis = drive.startedAt }.get(Calendar.MONTH)
        }
        var ytdBusinessKm = 0f
        return byMonth.entries
            .sortedBy { it.key }
            .map { (month, group) ->
                val summary = group.toSummary(
                    label = monthLabel(month, year),
                    period = ReportPeriod.MONTHLY,
                    year = year,
                    firstTierCents = firstTierCents,
                    additionalCents = additionalCents,
                    firstTierLimitKm = firstTierLimitKm,
                    customRatePerKm = customRatePerKm,
                    ytdBusinessKm = ytdBusinessKm,
                    vehicleId = vehicleId,
                    categories = categories,
                )
                ytdBusinessKm += summary.businessKm
                summary
            }
    }

    private fun aggregateQuarterly(
        drives: List<DriveEntity>,
        year: Int,
        firstTierCents: Int,
        additionalCents: Int,
        firstTierLimitKm: Int,
        customRatePerKm: Float?,
        vehicleId: Int?,
        categories: List<TripCategory>,
    ): List<TripSummary> {
        val byQuarter = drives.groupBy { drive ->
            val month = Calendar.getInstance()
                .apply { timeInMillis = drive.startedAt }
                .get(Calendar.MONTH)
            month / 3 + 1
        }
        var ytdBusinessKm = 0f
        return byQuarter.entries
            .sortedBy { it.key }
            .map { (quarter, group) ->
                val summary = group.toSummary(
                    label = "Q$quarter $year",
                    period = ReportPeriod.QUARTERLY,
                    year = year,
                    firstTierCents = firstTierCents,
                    additionalCents = additionalCents,
                    firstTierLimitKm = firstTierLimitKm,
                    customRatePerKm = customRatePerKm,
                    ytdBusinessKm = ytdBusinessKm,
                    vehicleId = vehicleId,
                    categories = categories,
                )
                ytdBusinessKm += summary.businessKm
                summary
            }
    }

    private fun aggregateAnnual(
        drives: List<DriveEntity>,
        year: Int,
        firstTierCents: Int,
        additionalCents: Int,
        firstTierLimitKm: Int,
        customRatePerKm: Float?,
        vehicleId: Int?,
        categories: List<TripCategory>,
    ): List<TripSummary> {
        if (drives.isEmpty()) return emptyList()
        return listOf(
            drives.toSummary(
                label = year.toString(),
                period = ReportPeriod.ANNUAL,
                year = year,
                firstTierCents = firstTierCents,
                additionalCents = additionalCents,
                firstTierLimitKm = firstTierLimitKm,
                customRatePerKm = customRatePerKm,
                ytdBusinessKm = 0f,
                vehicleId = vehicleId,
                categories = categories,
            ),
        )
    }

    private fun List<DriveEntity>.toSummary(
        label: String,
        period: ReportPeriod,
        year: Int,
        firstTierCents: Int,
        additionalCents: Int,
        firstTierLimitKm: Int,
        customRatePerKm: Float?,
        ytdBusinessKm: Float,
        vehicleId: Int?,
        categories: List<TripCategory>,
    ): TripSummary {
        val totalKm = sumOf { it.distanceKm.toDouble() }.toFloat()
        val businessKm = filter { it.tripCategory in categories }
            .sumOf { it.distanceKm.toDouble() }
            .toFloat()
        val personalKm = filter { it.tripCategory == TripCategory.PERSONAL }
            .sumOf { it.distanceKm.toDouble() }
            .toFloat()

        val reimbursement = if (customRatePerKm != null) {
            businessKm * customRatePerKm
        } else {
            twoTierReimbursement(
                businessKm = businessKm,
                ytdBefore = ytdBusinessKm,
                limitKm = firstTierLimitKm,
                firstTierCents = firstTierCents,
                additionalCents = additionalCents,
            )
        }

        return TripSummary(
            periodLabel = label,
            periodType = period,
            year = year,
            totalKm = totalKm,
            businessKm = businessKm,
            personalKm = personalKm,
            tripCount = size,
            estimatedReimbursement = reimbursement,
            vehicleId = vehicleId,
        )
    }
}
