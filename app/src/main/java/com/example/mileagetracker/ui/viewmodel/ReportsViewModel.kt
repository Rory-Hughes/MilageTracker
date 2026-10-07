package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.ReportPeriod
import com.example.mileagetracker.domain.usecase.GetReportReadinessUseCase
import com.example.mileagetracker.domain.usecase.drive.GetTripSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel for [ReportsScreen].
 *
 * ## State
 * - [selectedPeriod]    — bucketing granularity; toggled via [selectPeriod].
 * - [selectedYear]      — calendar year; changed via [selectYear].
 * - [selectedVehicleId] — optional vehicle filter; changed via [selectVehicle].
 * - [uiState]           — derived [ReportsUiState] computed whenever any
 *                         of the three filters change.
 *
 * ## Data flow
 * Filters are combined with [combine] into a single trigger flow; each
 * emission triggers [GetTripSummaryUseCase] and [GetReportReadinessUseCase]
 * in parallel, then the results are merged into [ReportsUiState.Success].
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val getTripSummaryUseCase:     GetTripSummaryUseCase,
    private val getReportReadinessUseCase: GetReportReadinessUseCase,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    /** Resolved once at construction; safe for the ViewModel lifetime. */
    private val driverUid: String =
        currentUserProvider.requireUid()

    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    private val _selectedPeriod    = MutableStateFlow(ReportPeriod.MONTHLY)
    private val _selectedYear      = MutableStateFlow(currentYear)
    private val _selectedVehicleId = MutableStateFlow<Int?>(null)

    val selectedPeriod:    StateFlow<ReportPeriod> = _selectedPeriod.asStateFlow()
    val selectedYear:      StateFlow<Int>          = _selectedYear.asStateFlow()
    val selectedVehicleId: StateFlow<Int?>         = _selectedVehicleId.asStateFlow()

    /** Range of selectable years: 5 years back through next year. */
    val availableYears: List<Int> = (currentYear - 5..currentYear + 1).toList()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ReportsUiState> =
        combine(_selectedPeriod, _selectedYear, _selectedVehicleId) {
            period, year, vehicleId -> Triple(period, year, vehicleId)
        }.flatMapLatest { (period, year, vehicleId) ->
            flow {
                emit(ReportsUiState.Loading)
                runCatching {
                    val summaries = getTripSummaryUseCase(
                        driverUid = driverUid,
                        year      = year,
                        period    = period,
                        vehicleId = vehicleId,
                    )

                    val allDrives = getTripSummaryUseCase.getDrivesForYear(
                        driverUid = driverUid,
                        year      = year,
                        vehicleId = vehicleId,
                    )
                    val readinessList = getReportReadinessUseCase(allDrives)

                    emit(ReportsUiState.Success(
                        summaries     = summaries,
                        readinessList = readinessList,
                        reportTripIds = allDrives.mapTo(mutableSetOf()) { it.id },
                    ))
                }.onFailure { error ->
                    emit(ReportsUiState.Error(error.message ?: "Failed to load report data."))
                }
            }
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReportsUiState.Loading,
        )

    fun selectPeriod(period: ReportPeriod) { _selectedPeriod.value = period }
    fun selectYear(year: Int)              { _selectedYear.value   = year   }
    fun selectVehicle(vehicleId: Int?)     { _selectedVehicleId.value = vehicleId }

}
