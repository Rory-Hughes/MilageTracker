package com.example.mileagetracker.worker

import com.example.mileagetracker.data.local.dao.VehicleDao
import com.example.mileagetracker.domain.repository.OdometerRepository
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

class OdometerEvidenceReminderChecker @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val odometerRepository: OdometerRepository,
) {
    suspend fun countVehiclesMissingOpeningEvidence(today: LocalDate): Int {
        if (today.monthValue != JANUARY) return 0

        val taxYear = today.year
        val (fromMillis, toMillis) = yearBoundaries(taxYear)
        return vehicleDao.getActiveVehiclesForFiscalYearOnce(taxYear)
            .count { vehicle ->
                odometerRepository.getOpeningEvidenceForVehicleYear(
                    vehicleId = vehicle.id,
                    taxYear = taxYear,
                    fromMillis = fromMillis,
                    toMillis = toMillis,
                ) == null
            }
    }

    private fun yearBoundaries(year: Int): Pair<Long, Long> {
        val fromMillis = LocalDate.of(year, 1, 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val toMillis = LocalDate.of(year + 1, 1, 1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli() - 1L
        return fromMillis to toMillis
    }

    private companion object {
        const val JANUARY = 1
    }
}
