package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.model.VehicleAnnualSummary
import com.example.mileagetracker.domain.usecase.rate.GetAnnualVehicleSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * UI state for [AnnualVehicleSummaryScreen].
 */
sealed interface AnnualVehicleSummaryUiState {
    data object Idle : AnnualVehicleSummaryUiState
    data object Loading : AnnualVehicleSummaryUiState
    data class Success(val summary: VehicleAnnualSummary) : AnnualVehicleSummaryUiState
    data class Error(val message: String) : AnnualVehicleSummaryUiState
}

/**
 * ViewModel for [AnnualVehicleSummaryScreen].
 *
 * ## Responsibilities
 * 1. Holds the selected [taxYear] and [vehicleId].
 * 2. Calls [GetAnnualVehicleSummaryUseCase] whenever either selection changes.
 * 3. Drives [uiState] through `Idle → Loading → Success | Error`.
 *
 * ## Year list
 * [availableYears] provides a sensible range of selectable years
 * (current year down to 2018) so the UI can populate a year picker
 * without querying the database.
 *
 * @param getAnnualVehicleSummary use case that assembles the summary.
 */
@HiltViewModel
class AnnualVehicleSummaryViewModel @Inject constructor(
    private val getAnnualVehicleSummary: GetAnnualVehicleSummaryUseCase,
) : ViewModel() {

    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    /** Selectable tax years shown in the year picker. */
    val availableYears: List<Int> = (currentYear downTo 2018).toList()

    private val _selectedYear = MutableStateFlow(currentYear)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<Int?>(null)
    val selectedVehicleId: StateFlow<Int?> = _selectedVehicleId.asStateFlow()

    private val _uiState = MutableStateFlow<AnnualVehicleSummaryUiState>(AnnualVehicleSummaryUiState.Idle)
    val uiState: StateFlow<AnnualVehicleSummaryUiState> = _uiState.asStateFlow()

    /**
     * Selects a new tax year and reloads the summary if a vehicle is already
     * selected.
     */
    fun selectYear(year: Int) {
        _selectedYear.value = year
        loadIfReady()
    }

    /**
     * Selects a vehicle and triggers a summary load.
     *
     * @param vehicleId the DB PK of the vehicle to display.
     */
    fun selectVehicle(vehicleId: Int) {
        _selectedVehicleId.value = vehicleId
        loadIfReady()
    }

    private fun loadIfReady() {
        val vehicleId = _selectedVehicleId.value ?: return
        val year = _selectedYear.value

        viewModelScope.launch {
            _uiState.value = AnnualVehicleSummaryUiState.Loading
            runCatching { getAnnualVehicleSummary(vehicleId, year) }
                .onSuccess { summary ->
                    _uiState.value = AnnualVehicleSummaryUiState.Success(summary)
                }
                .onFailure { error ->
                    _uiState.value = AnnualVehicleSummaryUiState.Error(
                        error.message ?: "Failed to load annual summary",
                    )
                }
        }
    }
}
