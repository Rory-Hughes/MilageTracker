package com.example.mileagetracker.ui.viewmodel

import com.example.mileagetracker.data.local.entity.TripAuditEntity

/**
 * UI state for [TripAuditScreen].
 *
 * Follows the same sealed-class Loading / Success / Error pattern used
 * throughout Phase 7 ViewModels.
 */
sealed interface TripAuditUiState {

    /** Audit rows are being fetched from the database. */
    data object Loading : TripAuditUiState

    /**
     * Rows loaded successfully.
     *
     * @param tripId    The trip whose history is displayed.
     * @param auditRows Chronologically ordered audit trail (oldest first).
     *                  Empty list is valid — renders the empty state.
     */
    data class Success(
        val tripId: Int,
        val auditRows: List<TripAuditEntity>,
    ) : TripAuditUiState

    /**
     * A database or repository error occurred.
     *
     * @param message Human-readable description for the error state view.
     */
    data class Error(val message: String) : TripAuditUiState
}
