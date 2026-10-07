package com.example.mileagetracker.domain.usecase.rate

import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.domain.repository.RateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Exposes the active CRA reimbursement rate for a given tax year.
 *
 * Two access patterns are provided:
 * - [invoke] – returns a reactive [Flow] suitable for ViewModel `stateIn`
 *   collection; the UI updates automatically when rates are seeded on
 *   first launch.
 * - [once] – one-shot suspend function for use in calculations and
 *   report-generation coroutines where a cold [Flow] would be wasteful.
 *
 * Both return `null` when no rate has been seeded for [taxYear], letting
 * the caller decide between showing a loading state (UI) or throwing a
 * meaningful error (report generation).
 */
class GetCraRateForYearUseCase @Inject constructor(
    private val rateRepository: RateRepository,
) {
    /**
     * Reactive: returns a [Flow] that emits the resolved [CraRateEntity]
     * for [taxYear] and re-emits whenever the underlying data changes.
     *
     * Emits `null` if no rate has been seeded yet.
     */
    operator fun invoke(taxYear: Int): Flow<CraRateEntity?> =
        rateRepository.getRateForYear(taxYear)

    /**
     * One-shot: suspends and returns the resolved [CraRateEntity] for
     * [taxYear], or `null` if not seeded.
     *
     * Prefer this in [CalculateReimbursementUseCase] and report coroutines.
     */
    suspend fun once(taxYear: Int): CraRateEntity? =
        rateRepository.getRateForYearOnce(taxYear)
}
