package com.example.mileagetracker.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.model.TeamRole
import com.example.mileagetracker.domain.usecase.InviteTeamMemberUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverInviteUiState(
    val email: String = "",
    val emailError: Boolean = false,
    val selectedRole: TeamRole = TeamRole.DRIVER,
    val selectedPolicyId: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null
)

/**
 * ViewModel for [DriverInviteScreen].
 */
@HiltViewModel
class DriverInviteViewModel @Inject constructor(
    private val inviteUseCase: InviteTeamMemberUseCase,
    policyRepository: PolicyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverInviteUiState())
    val uiState: StateFlow<DriverInviteUiState> = _uiState.asStateFlow()

    val policies: StateFlow<List<PolicyTemplate>> =
        policyRepository.observePolicies()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun onEmailChange(email: String) =
        _uiState.update { it.copy(email = email, emailError = false) }

    fun onRoleChange(role: TeamRole) =
        _uiState.update { it.copy(selectedRole = role) }

    fun onPolicyChange(policyId: String?) =
        _uiState.update { it.copy(selectedPolicyId = policyId) }

    fun clearStatus() =
        _uiState.update { it.copy(statusMessage = null) }

    fun sendInvite() {
        val state = _uiState.value
        val emailValid = state.email.isNotBlank() && state.email.contains('@')
        if (!emailValid) {
            _uiState.update { it.copy(emailError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching {
                inviteUseCase(
                    email = state.email.trim(),
                    role = state.selectedRole,
                    policyTemplateId = state.selectedPolicyId
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        email = "",
                        selectedPolicyId = null,
                        selectedRole = TeamRole.DRIVER,
                        statusMessage = "Invite sent to ${state.email.trim()}."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to send invite: ${error.message}"
                    )
                }
            }
        }
    }
}
