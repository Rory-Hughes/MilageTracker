package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.repository.AuditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for [TripAuditScreen].
 *
 * Loads the immutable audit trail for a single trip from [AuditRepository]
 * and exposes it as a [StateFlow<TripAuditUiState>].
 *
 * ## Navigation contract
 * The trip ID is supplied via [SavedStateHandle] under the key [KEY_TRIP_ID].
 * In the nav graph, declare:
 * ```
 * composable(
 *     route = "tripAudit/{tripId}",
 *     arguments = listOf(navArgument("tripId") { type = NavType.IntType })
 * ) { backStackEntry ->
 *     val vm: TripAuditViewModel = hiltViewModel()
 *     TripAuditScreen(uiState = vm.uiState.collectAsState().value, ...)
 * }
 * ```
 *
 * ## Audit ordering
 * Rows are delivered oldest-first by the [TripAuditDao] query and kept in
 * that order so the on-screen timeline reads chronologically top-to-bottom.
 */
@HiltViewModel
class TripAuditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val auditRepository: AuditRepository,
) : ViewModel() {

    private val tripId: Int = checkNotNull(savedStateHandle[KEY_TRIP_ID]) {
        "TripAuditViewModel requires a '$KEY_TRIP_ID' argument in SavedStateHandle."
    }

    /**
     * Reactive UI state derived from the live audit trail for [tripId].
     *
     * Starts as [TripAuditUiState.Loading] and transitions to
     * [TripAuditUiState.Success] on first database emission, or
     * [TripAuditUiState.Error] if the repository throws.
     */
    val uiState: StateFlow<TripAuditUiState> =
        auditRepository.getAuditTrailForTrip(tripId)
            .map<_, TripAuditUiState> { rows ->
                TripAuditUiState.Success(
                    tripId = tripId,
                    auditRows = rows,
                )
            }
            .catch { e ->
                emit(TripAuditUiState.Error(e.message ?: "Failed to load audit trail."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TripAuditUiState.Loading,
            )

    companion object {
        /** Navigation argument key used by the nav graph to supply the trip ID. */
        const val KEY_TRIP_ID = "tripId"
    }
}
