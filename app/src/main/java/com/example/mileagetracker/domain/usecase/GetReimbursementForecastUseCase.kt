package com.example.mileagetracker.domain.usecase

import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.data.seeder.CraRateCatalog
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.model.CraRateRegionRequiredException
import com.example.mileagetracker.domain.model.RateMode
import com.example.mileagetracker.domain.model.ReimbursementForecast
import com.example.mileagetracker.domain.model.ReportPeriod
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.drive.GetTripSummaryUseCase
import com.example.mileagetracker.domain.usecase.rate.RateNotSeededException
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject

class GetReimbursementForecastUseCase @Inject constructor(
    private val getTripSummaryUseCase: GetTripSummaryUseCase,
    private val approvalRepository: ApprovalRepository,
    private val policyRepository: PolicyRepository,
    private val rateRepository: RateRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(
        drivers: List<TeamMember>,
        year: Int = currentYear(),
        month: Int = currentMonth(),
    ): List<ReimbursementForecast> {
        val context = loadForecastContext(year = year, month = month)
        val pendingApprovalCounts = loadPendingApprovalCounts()
        return drivers.map { driver ->
            buildForecastForDriver(
                driver = driver,
                year = year,
                context = context,
                pendingApprovalCounts = pendingApprovalCounts,
            )
        }
    }

    private suspend fun loadForecastContext(year: Int, month: Int): ForecastContext {
        val craRegion = requireCraRegion(settingsRepository)
        val craRate = requireCraRate(
            year = year,
            craRegion = craRegion,
            rateRepository = rateRepository,
        )
        return ForecastContext(
            fractionElapsed = fractionOfMonthElapsed(year, month),
            periodLabel = monthLabel(month, year),
            craRatePerKmDollars = craRate.firstTier / 100f,
            craRateLabel = "$%.2f / km (CRA prescribed allowance estimate)"
                .format(craRate.firstTier / 100.0),
            globalCustomRate = loadGlobalCustomRate(),
        )
    }

    private suspend fun loadGlobalCustomRate(): Float? {
        val globalRateMode = settingsRepository.getRateMode().first()
        return if (globalRateMode == RateMode.CUSTOM) {
            settingsRepository.getCustomRateDollars().first().takeIf { it > 0f }
        } else {
            null
        }
    }

    private suspend fun loadPendingApprovalCounts(): Map<Long, Int> =
        approvalRepository.observePending()
            .first()
            .groupingBy { it.submittedByUserId }
            .eachCount()

    private suspend fun buildForecastForDriver(
        driver: TeamMember,
        year: Int,
        context: ForecastContext,
        pendingApprovalCounts: Map<Long, Int>,
    ): ReimbursementForecast {
        val rateSelection = resolveRateSelection(driver, context)
        val actualBusinessKm = loadActualBusinessKm(
            driverUid = driver.userId,
            year = year,
            periodLabel = context.periodLabel,
        )
        val projectedBusinessKm = projectBusinessKm(
            actualBusinessKm = actualBusinessKm,
            fractionElapsed = context.fractionElapsed,
        )
        return ReimbursementForecast(
            driverUid = driver.userId,
            driverDisplayName = driver.displayName,
            periodLabel = context.periodLabel,
            actualBusinessKm = actualBusinessKm,
            projectedBusinessKm = projectedBusinessKm,
            actualReimbursement = calculateReimbursement(actualBusinessKm, rateSelection.ratePerKm),
            projectedReimbursement = calculateReimbursement(
                projectedBusinessKm,
                rateSelection.ratePerKm,
            ),
            pendingApprovalCount = pendingApprovalCounts[driver.userId.toLongOrNull()] ?: 0,
            rateLabel = rateSelection.label,
        )
    }

    private suspend fun resolveRateSelection(
        driver: TeamMember,
        context: ForecastContext,
    ): RateSelection {
        val policy = driver.policyTemplateId?.let { policyRepository.getById(it) }
        val customRate = when {
            policy?.rateMode == RateMode.CUSTOM ->
                policy.customRateCentsPerKm?.div(100f)

            else -> context.globalCustomRate
        }
        return customRate?.let { rate ->
            RateSelection(
                ratePerKm = rate,
                label = "Custom: $%.2f / km".format(rate),
            )
        } ?: RateSelection(
            ratePerKm = context.craRatePerKmDollars,
            label = context.craRateLabel,
        )
    }

    private suspend fun loadActualBusinessKm(
        driverUid: String,
        year: Int,
        periodLabel: String,
    ): Float = getTripSummaryUseCase(
        driverUid = driverUid,
        year = year,
        period = ReportPeriod.MONTHLY,
    ).firstOrNull { it.periodLabel == periodLabel }?.businessKm ?: 0f
}

private data class ForecastContext(
    val fractionElapsed: Float,
    val periodLabel: String,
    val craRatePerKmDollars: Float,
    val craRateLabel: String,
    val globalCustomRate: Float?,
)

private data class RateSelection(
    val ratePerKm: Float,
    val label: String,
)

private fun calculateReimbursement(
    businessKm: Float,
    ratePerKm: Float,
): Float = businessKm * ratePerKm

private fun projectBusinessKm(
    actualBusinessKm: Float,
    fractionElapsed: Float,
): Float = if (fractionElapsed > 0f) {
    actualBusinessKm / fractionElapsed
} else {
    actualBusinessKm
}

private fun fractionOfMonthElapsed(year: Int, month: Int): Float {
    val now = Calendar.getInstance()
    return if (now.get(Calendar.YEAR) == year && now.get(Calendar.MONTH) == month) {
        val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        dayOfMonth.toFloat() / daysInMonth.toFloat()
    } else {
        1f
    }
}

private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

private fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH)

private fun monthLabel(month: Int, year: Int): String {
    val names = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    return "${names[month]} $year"
}

private suspend fun requireCraRegion(
    settingsRepository: SettingsRepository,
) = settingsRepository.getCraRateRegion().first()
    ?: throw CraRateRegionRequiredException()

private suspend fun requireCraRate(
    year: Int,
    craRegion: CraRateRegion,
    rateRepository: RateRepository,
) = rateRepository.getRateForYearOnce(year)
    ?.let { CraRateCatalog.resolve(year, craRegion) }
    ?: throw RateNotSeededException(year)
