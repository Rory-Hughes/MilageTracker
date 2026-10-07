package com.example.mileagetracker.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus
import com.example.mileagetracker.domain.usecase.ApproveRejectTripUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApprovalQueueUiState(
    val pendingRequests: List<ApprovalRequest> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class ApprovalQueueViewModel @Inject constructor(
    private val approvalRepository: ApprovalRepository,
    private val approveRejectTripUseCase: ApproveRejectTripUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApprovalQueueUiState())
    val uiState: StateFlow<ApprovalQueueUiState> = _uiState.asStateFlow()

    init {
        approvalRepository.observePending()
            .onEach { requests ->
                _uiState.value = _uiState.value.copy(
                    pendingRequests = requests,
                    isLoading = false,
                )
            }
            .launchIn(viewModelScope)
    }

    fun approve(requestId: Long, reviewerUserId: Long, comment: String? = null) {
        viewModelScope.launch {
            approveRejectTripUseCase(
                requestId = requestId,
                reviewerUserId = reviewerUserId,
                newStatus = ApprovalStatus.APPROVED,
                comment = comment,
            ).onSuccess {
                _uiState.value = _uiState.value.copy(successMessage = "Request approved.")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun reject(requestId: Long, reviewerUserId: Long, comment: String) {
        viewModelScope.launch {
            approveRejectTripUseCase(
                requestId = requestId,
                reviewerUserId = reviewerUserId,
                newStatus = ApprovalStatus.REJECTED,
                comment = comment,
            ).onSuccess {
                _uiState.value = _uiState.value.copy(successMessage = "Request rejected.")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun returnForRevision(requestId: Long, reviewerUserId: Long, comment: String) {
        viewModelScope.launch {
            approveRejectTripUseCase(
                requestId = requestId,
                reviewerUserId = reviewerUserId,
                newStatus = ApprovalStatus.RETURNED,
                comment = comment,
            ).onSuccess {
                _uiState.value = _uiState.value.copy(successMessage = "Returned for revision.")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(errorMessage = e.message)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
