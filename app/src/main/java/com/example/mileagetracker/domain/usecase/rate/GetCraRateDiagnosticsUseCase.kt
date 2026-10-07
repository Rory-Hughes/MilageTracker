package com.example.mileagetracker.domain.usecase.rate

import com.example.mileagetracker.data.seeder.CraRateCatalog
import com.example.mileagetracker.domain.model.CraRateDiagnostics
import com.example.mileagetracker.domain.repository.RateRepository
import java.time.LocalDate
import javax.inject.Inject

class GetCraRateDiagnosticsUseCase @Inject constructor(
    private val rateRepository: RateRepository,
) {
    suspend operator fun invoke(): CraRateDiagnostics {
        val catalogYears = CraRateCatalog.provincialRates.map { it.taxYear }.sorted()
        val seededYears = rateRepository.getSeededYears().sorted()
        return CraRateDiagnostics(
            currentYear = LocalDate.now().year,
            catalogStartYear = catalogYears.first(),
            catalogEndYear = catalogYears.last(),
            seededYears = seededYears,
            unseededYears = catalogYears.filterNot(seededYears::contains),
            sourceUrl = CraRateCatalog.SOURCE_URL,
        )
    }
}
