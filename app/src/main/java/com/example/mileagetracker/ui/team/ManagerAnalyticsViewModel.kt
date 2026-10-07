package com.example.mileagetracker.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.model.ReimbursementForecast
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.domain.usecase.GetReimbursementForecastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * UI state for [ManagerAnalyticsScreen].
 *
 * @property isRoleAuthorised  False when the user is not MANAGER or ADMIN.
 * @property selectedYear      The currently displayed year.
 * @property selectedMonth     Zero-based month index for the displayed period.
 * @property forecasts         Per-driver forecasts for [selectedYear]/[selectedMonth].
 * @property totalProjectedReimbursement  Fleet-wide sum of [ReimbursementForecast.projectedReimbursement].
 * @property totalActualReimbursement     Fleet-wide sum of [ReimbursementForecast.actualReimbursement].
 * @property isLoading         True while data is being fetched.
 * @property errorMessage      Non-null on error.
 */
data class ManagerAnalyticsUiState(
    val isRoleAuthorised: Boolean = false,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH),
    val forecasts: List<ReimbursementForecast> = emptyList(),
    val totalProjectedReimbursement: Float = 0f,
    val totalActualReimbursement: Float = 0f,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class ManagerAnalyticsViewModel @Inject constructor(
    private val getReimbursementForecastUseCase: GetReimbursementForecastUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManagerAnalyticsUiState())
    val uiState: StateFlow<ManagerAnalyticsUiState> = _uiState.asStateFlow()

    private var cachedDrivers: List<TeamMember> = emptyList()

    /**
     * Initialise analytics for the given [currentUser] and list of [managedDrivers].
     *
     * @param currentUser    The signed-in manager or admin.
     * @param managedDrivers Active [TeamRole.DRIVER] members this manager oversees.
     */
    fun initialise(currentUser: TeamMember, managedDrivers: List<TeamMember>) {
        val authorised = currentUser.role == TeamRole.MANAGER || currentUser.role == TeamRole.ADMIN
        if (!authorised) {
            _uiState.value = _uiState.value.copy(isRoleAuthorised = false, isLoading = false)
            return
        }
        cachedDrivers = managedDrivers
        _uiState.value = _uiState.value.copy(isRoleAuthorised = true)
        loadForecasts()
    }

    /** Navigate to the previous month. */
    fun previousMonth() {
        val current = _uiState.value
        val newMonth = if (current.selectedMonth == 0) 11 else current.selectedMonth - 1
        val newYear = if (current.selectedMonth == 0) current.selectedYear - 1 else current.selectedYear
        _uiState.value = current.copy(selectedYear = newYear, selectedMonth = newMonth)
        loadForecasts()
    }

    /** Navigate to the next month (capped at the current calendar month). */
    fun nextMonth() {
        val current = _uiState.value
        val cal = Calendar.getInstance()
        val nowYear = cal.get(Calendar.YEAR)
        val nowMonth = cal.get(Calendar.MONTH)
        if (current.selectedYear == nowYear && current.selectedMonth == nowMonth) return
        val newMonth = if (current.selectedMonth == 11) 0 else current.selectedMonth + 1
        val newYear = if (current.selectedMonth == 11) current.selectedYear + 1 else current.selectedYear
        _uiState.value = current.copy(selectedYear = newYear, selectedMonth = newMonth)
        loadForecasts()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun loadForecasts() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val year = _uiState.value.selectedYear
        val month = _uiState.value.selectedMonth
        viewModelScope.launch {
            runCatching {
                val forecasts = getReimbursementForecastUseCase(
                    drivers = cachedDrivers,
                    year = year,
                    month = month,
                )
                _uiState.value = _uiState.value.copy(
                    forecasts = forecasts,
                    totalProjectedReimbursement = forecasts.sumOf { it.projectedReimbursement.toDouble() }.toFloat(),
                    totalActualReimbursement = forecasts.sumOf { it.actualReimbursement.toDouble() }.toFloat(),
                    isLoading = false,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message,
                    isLoading = false,
                )
            }
        }
    }
}
