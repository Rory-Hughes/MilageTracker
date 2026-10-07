package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.domain.model.ReportReadiness
import com.example.mileagetracker.domain.model.TripSummary

/**
 * UI state for [ReportsScreen].
 *
 * Emitted by [ReportsViewModel.uiState] as a [kotlinx.coroutines.flow.StateFlow].
 */
sealed interface ReportsUiState {
    /** Data is being loaded or filters are being applied. */
    data object Loading : ReportsUiState

    /**
     * Summaries loaded successfully.
     *
     * @param summaries      Non-empty list of [TripSummary] buckets sorted
     *                       chronologically.  May be empty if no trips exist
     *                       for the selected year / vehicle.
     * @param readinessList  Full [ReportReadiness] list for the current
     *                       period, produced by [GetReportReadinessUseCase].
     *                       Sorted lowest-score-first so the card and the
     *                       [ExceptionsListScreen] always show worst offenders
     *                       first.  Empty when no trips exist.
     * @param reportTripIds  Active trip IDs in the selected report scope.
     */
    data class Success(
        val summaries:     List<TripSummary>,
        val readinessList: List<ReportReadiness> = emptyList(),
        val reportTripIds: Set<Int> = emptySet(),
    ) : ReportsUiState

    /** An error occurred while loading or aggregating data. */
    data class Error(val message: String) : ReportsUiState
}
