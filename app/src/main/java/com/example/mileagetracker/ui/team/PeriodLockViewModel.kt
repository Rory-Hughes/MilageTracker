package com.example.mileagetracker.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.usecase.LockPeriodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PeriodRow(
    val year: Int,
    val month: Int?,
    val isLocked: Boolean,
    val lockedAt: Long? = null,
)

data class PeriodLockUiState(
    val periodRows: List<PeriodRow> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class PeriodLockViewModel @Inject constructor(
    private val approvalRepository: ApprovalRepository,
    private val lockPeriodUseCase: LockPeriodUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeriodLockUiState())
    val uiState: StateFlow<PeriodLockUiState> = _uiState.asStateFlow()

    init {
        approvalRepository.observeAllLocks()
            .onEach { locks ->
                val today = LocalDate.now()
                val rows = buildList {
                    for (i in 0..11) {
                        val d = today.minusMonths(i.toLong())
                        val existingLock = locks.firstOrNull {
                            it.periodYear == d.year && it.periodMonth == d.monthValue
                        }
                        add(PeriodRow(d.year, d.monthValue, existingLock != null, existingLock?.lockedAt))
                    }
                }
                _uiState.value = _uiState.value.copy(periodRows = rows, isLoading = false)
            }
            .launchIn(viewModelScope)
    }

    fun lock(year: Int, month: Int?, managerUserId: Long) {
        viewModelScope.launch {
            lockPeriodUseCase(year, month, managerUserId)
                .onSuccess { _uiState.value = _uiState.value.copy(message = "Period locked.") }
                .onFailure { _uiState.value = _uiState.value.copy(message = it.message) }
        }
    }

    fun unlock(year: Int, month: Int?) {
        viewModelScope.launch {
            runCatching { approvalRepository.unlockPeriod(year, month) }
                .onSuccess { _uiState.value = _uiState.value.copy(message = "Period unlocked.") }
                .onFailure { _uiState.value = _uiState.value.copy(message = it.message) }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
