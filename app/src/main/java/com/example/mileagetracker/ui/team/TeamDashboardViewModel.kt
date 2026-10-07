package com.example.mileagetracker.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.ReimbursementForecast
import com.example.mileagetracker.domain.model.TeamMember
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.domain.usecase.GetReimbursementForecastUseCase
import com.example.mileagetracker.domain.usecase.GetTripExceptionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for [TeamDashboardScreen].
 *
 * @property currentUser       The signed-in manager/admin.
 * @property isRoleAuthorised  False when the user is not MANAGER or ADMIN; screen shows a guard.
 * @property forecasts         Per-driver reimbursement forecasts for the current month.
 * @property recentExceptions  Latest trip exceptions across all managed drivers (capped at 5).
 * @property pendingQueueCount Total pending approval requests across all drivers.
 * @property isLoading         True while initial data loads.
 * @property errorMessage      Non-null when an error has occurred.
 */
data class TeamDashboardUiState(
    val currentUser: TeamMember? = null,
    val isRoleAuthorised: Boolean = false,
    val forecasts: List<ReimbursementForecast> = emptyList(),
    val recentExceptions: List<TripException> = emptyList(),
    val pendingQueueCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class TeamDashboardViewModel @Inject constructor(
    private val approvalRepository: ApprovalRepository,
    private val getTripExceptionsUseCase: GetTripExceptionsUseCase,
    private val getReimbursementForecastUseCase: GetReimbursementForecastUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeamDashboardUiState())
    val uiState: StateFlow<TeamDashboardUiState> = _uiState.asStateFlow()

    /**
     * Initialise the dashboard for the given [currentUser].
     *
     * Call this once from the screen's LaunchedEffect when the user
     * session is established.  The function is idempotent.
     *
     * @param currentUser      The signed-in team member.
     * @param managedDrivers   All active [TeamRole.DRIVER] members this manager oversees.
     */
    fun initialise(currentUser: TeamMember, managedDrivers: List<TeamMember>) {
        val authorised = currentUser.role == TeamRole.MANAGER || currentUser.role == TeamRole.ADMIN
        if (!authorised) {
            _uiState.value = _uiState.value.copy(
                currentUser = currentUser,
                isRoleAuthorised = false,
                isLoading = false,
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            currentUser = currentUser,
            isRoleAuthorised = true,
        )

        // Observe pending approval count
        approvalRepository.observePending()
            .onEach { pending ->
                _uiState.value = _uiState.value.copy(pendingQueueCount = pending.size)
            }
            .launchIn(viewModelScope)

        // Load forecasts + exceptions for all managed drivers
        viewModelScope.launch {
            runCatching {
                val forecasts = getReimbursementForecastUseCase(managedDrivers)
                _uiState.value = _uiState.value.copy(
                    forecasts = forecasts,
                    isLoading = false,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message,
                    isLoading = false,
                )
            }
        }

        // Observe recent exceptions for all managed drivers (combine up to 5 most recent)
        if (managedDrivers.isNotEmpty()) {
            val exceptionFlows = managedDrivers.map { driver ->
                getTripExceptionsUseCase(driver.userId)
            }
            // Combine all driver exception flows into a single list
            exceptionFlows.reduce { acc, flow ->
                acc.combine(flow) { a, b -> a + b }
            }.onEach { allExceptions ->
                _uiState.value = _uiState.value.copy(
                    recentExceptions = allExceptions
                        .sortedByDescending { it.detectedAt }
                        .take(5),
                )
            }.launchIn(viewModelScope)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
