package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.model.PrivacyDashboardSnapshot
import com.example.mileagetracker.domain.usecase.account.GetPrivacyDashboardSnapshotUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PrivacyDashboardViewModel @Inject constructor(
    private val getPrivacyDashboardSnapshotUseCase: GetPrivacyDashboardSnapshotUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PrivacyDashboardUiState>(PrivacyDashboardUiState.Loading)
    val uiState: StateFlow<PrivacyDashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = PrivacyDashboardUiState.Loading
            _uiState.value = runCatching {
                PrivacyDashboardUiState.Success(getPrivacyDashboardSnapshotUseCase())
            }.getOrElse { error ->
                PrivacyDashboardUiState.Error(
                    error.message ?: "Unable to load privacy dashboard.",
                )
            }
        }
    }
}

sealed interface PrivacyDashboardUiState {
    object Loading : PrivacyDashboardUiState
    data class Success(val snapshot: PrivacyDashboardSnapshot) : PrivacyDashboardUiState
    data class Error(val message: String) : PrivacyDashboardUiState
}
