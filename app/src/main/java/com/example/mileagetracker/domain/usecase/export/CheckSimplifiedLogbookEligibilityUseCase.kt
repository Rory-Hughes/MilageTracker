package com.example.mileagetracker.domain.usecase.export

import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.SimplifiedLogbookStatus
import com.example.mileagetracker.domain.model.SimplifiedLogbookStatus.Companion.MIN_BASE_YEAR_TRIPS
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Determines whether the authenticated user qualifies for the CRA simplified
 * logbook method and returns the full guidance payload.
 */
class CheckSimplifiedLogbookEligibilityUseCase @Inject constructor(
    private val driveRepository: DriveRepository,
    @Suppress("UnusedPrivateMember")
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(driverUid: String): SimplifiedLogbookStatus {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearStats = loadYearStats(driverUid = driverUid, currentYear = currentYear)
        val fullBaseYears = yearStats.filter { it.tripCount >= MIN_BASE_YEAR_TRIPS }
        val currentSampleKm = loadCurrentSampleKm(
            driverUid = driverUid,
            currentYear = currentYear,
        )

        if (fullBaseYears.isEmpty()) {
            return SimplifiedLogbookStatus.ineligible(currentSampleKm)
        }

        val availableYears = fullBaseYears.map { it.year }.sortedDescending()
        val baseStats = fullBaseYears.maxByOrNull { it.year }!!
        val businessUse = calculateBusinessUse(baseStats)
        val requiredSampleKm = baseStats.businessKm * SAMPLE_RATIO
        val sampleProgress = calculateSampleProgress(
            currentSampleKm = currentSampleKm,
            requiredSampleKm = requiredSampleKm,
        )

        return SimplifiedLogbookStatus(
            isEligible = true,
            baseYear = baseStats.year,
            baseYearBusinessUsePercent = businessUse,
            baseYearTripCount = baseStats.tripCount,
            currentSampleKm = currentSampleKm,
            requiredSampleKm = requiredSampleKm,
            sampleProgressPercent = sampleProgress,
            availableBaseYears = availableYears,
            guidanceText = buildGuidanceText(
                baseStats = baseStats,
                businessUse = businessUse,
                sampleProgress = sampleProgress,
                requiredSampleKm = requiredSampleKm,
                currentYear = currentYear,
            ),
        )
    }

    private suspend fun loadYearStats(
        driverUid: String,
        currentYear: Int,
    ): List<YearStats> = (EARLIEST_YEAR until currentYear).mapNotNull { year ->
        val (fromMs, toMs) = yearBounds(year)
        val drives = driveRepository.getDrivesForAnnualSummary(driverUid, fromMs, toMs)
        drives.takeIf { it.isNotEmpty() }?.let { nonEmptyDrives ->
            YearStats(
                year = year,
                tripCount = nonEmptyDrives.size,
                businessKm = nonEmptyDrives
                    .filter { it.tripCategory == TripCategory.BUSINESS }
                    .sumOf { it.distanceKm.toDouble() },
                totalKm = nonEmptyDrives.sumOf { it.distanceKm.toDouble() },
            )
        }
    }

    private suspend fun loadCurrentSampleKm(
        driverUid: String,
        currentYear: Int,
    ): Double {
        val (fromMs, toMs) = yearBounds(currentYear)
        return driveRepository.getDrivesForAnnualSummary(driverUid, fromMs, toMs)
            .filter { it.tripCategory == TripCategory.BUSINESS }
            .sumOf { it.distanceKm.toDouble() }
    }

    private fun calculateBusinessUse(baseStats: YearStats): Double =
        if (baseStats.totalKm > 0) {
            (baseStats.businessKm / baseStats.totalKm) * 100.0
        } else {
            0.0
        }

    private fun calculateSampleProgress(
        currentSampleKm: Double,
        requiredSampleKm: Double,
    ): Double = if (requiredSampleKm > 0) {
        min((currentSampleKm / requiredSampleKm) * 100.0, 100.0)
    } else {
        100.0
    }

    private fun buildGuidanceText(
        baseStats: YearStats,
        businessUse: Double,
        sampleProgress: Double,
        requiredSampleKm: Double,
        currentYear: Int,
    ): String {
        val progressPct = sampleProgress.roundToInt()
        return buildString {
            append("Your ${baseStats.year} logbook (${baseStats.tripCount} trips, ")
            append("${businessUse.roundToInt()}% business use) qualifies as your base year. ")
            if (sampleProgress >= 100.0) {
                append("Your sample period is complete. You may apply the simplified method ")
                append("for $currentYear.")
            } else {
                append("Record at least ${requiredSampleKm.roundToInt()} business km in a ")
                append("continuous 3-month window this year to complete your sample. ")
                append("You're $progressPct% of the way there.")
            }
        }
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

    companion object {
        private const val EARLIEST_YEAR = 2019
        private const val SAMPLE_RATIO = 0.25

        const val PREF_BASE_YEAR = "simplified_logbook_base_year"
    }
}

private data class YearStats(
    val year: Int,
    val tripCount: Int,
    val businessKm: Double,
    val totalKm: Double,
)
