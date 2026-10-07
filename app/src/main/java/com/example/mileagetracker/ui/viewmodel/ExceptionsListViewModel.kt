package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.ReportReadiness
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.domain.usecase.GetReportReadinessUseCase
import com.example.mileagetracker.domain.usecase.GetTripExceptionsUseCase
import com.example.mileagetracker.domain.usecase.drive.GetTripSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel for [ExceptionsListScreen].
 *
 * Combines two data sources into a single reactive UI state:
 * 1. **[incompleteTrips]** — trips with `readinessScore < 1.0` produced by
 *    [GetReportReadinessUseCase] over the current calendar year for the
 *    signed-in driver.  (Task 7.3 baseline.)
 * 2. **[exceptions]** — active [TripException] objects produced by
 *    [GetTripExceptionsUseCase], covering NEEDS_REVIEW, INCOMPLETE,
 *    DUPLICATE_FLAGGED, IMPOSSIBLE_ROUTE, and MANUAL_OVERRIDE types.
 *    (Task 7.5 addition.)
 *
 * Both flows are kept live so the screen updates automatically if the user
 * fixes a trip from the detail screen and returns to this list.
 *
 * ## Exception badge count
 * [activeExceptionCount] is a derived [StateFlow] that emits the total
 * number of unresolved exceptions.  It is observed by [MainAppShell] to
 * drive the badge on the Reports drawer item.
 */
@HiltViewModel
class ExceptionsListViewModel @Inject constructor(
    private val getTripSummaryUseCase: GetTripSummaryUseCase,
    private val getReportReadinessUseCase: GetReportReadinessUseCase,
    private val getTripExceptionsUseCase: GetTripExceptionsUseCase,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val driverUid: String =
        currentUserProvider.requireUid()

    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    // ── Incomplete trips (Task 7.3) ────────────────────────────────────────

    /**
     * Live list of trips missing one or more required CRA fields,
     * scoped to the current calendar year and sorted worst-score-first.
     * Emits an empty list while data loads or if no incomplete trips exist.
     */
    val incompleteTrips: StateFlow<List<ReportReadiness>> = flow {
        val yearStart = Calendar.getInstance().apply {
            set(currentYear, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yearEnd = Calendar.getInstance().apply {
            set(currentYear, Calendar.DECEMBER, 31, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val drives = getTripSummaryUseCase.getDrivesForYear(
            driverUid = driverUid,
            year = currentYear,
            vehicleId = null,
        )
        val readiness = getReportReadinessUseCase(drives)
        emit(readiness.filter { !it.isReady })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    // ── Exceptions (Task 7.5) ─────────────────────────────────────────────

    /**
     * Live list of [TripException] objects across all of the driver's trips,
     * sorted by [TripException.detectedAt] descending (most-recent first).
     */
    val exceptions: StateFlow<List<TripException>> =
        getTripExceptionsUseCase(driverUid)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    // ── Badge count (Task 7.5) ────────────────────────────────────────────

    /**
     * Total count of unresolved exceptions across both sources.
     *
     * Combines [incompleteTrips] (readiness failures) and [exceptions]
     * (flagged exceptions) into a deduplicated count by trip ID.  Observed
     * by [MainAppShell] to render the badge on the Reports drawer item.
     */
    val activeExceptionCount: StateFlow<Int> =
        combine(incompleteTrips, exceptions) { incomplete, exList ->
            val incompleteIds = incomplete.map { it.tripId }.toSet()
            val exceptionIds = exList.filter { it.isActive }.map { it.tripId }.toSet()
            (incompleteIds + exceptionIds).size
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )
}
