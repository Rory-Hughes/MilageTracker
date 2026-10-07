package com.example.mileagetracker.ui.policy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.repository.PolicyRepository
import com.example.mileagetracker.domain.model.PolicyTemplate
import com.example.mileagetracker.domain.usecase.SavePolicyTemplateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [PolicyTemplateScreen].
 *
 * Exposes all policy templates as a [StateFlow] and provides
 * create/edit/delete operations backed by [SavePolicyTemplateUseCase] and [PolicyRepository].
 */
@HiltViewModel
class PolicyTemplateViewModel @Inject constructor(
    private val repository: PolicyRepository,
    private val savePolicyUseCase: SavePolicyTemplateUseCase
) : ViewModel() {

    /** All stored policy templates, updated reactively from Room. */
    val policies: StateFlow<List<PolicyTemplate>> =
        repository.observePolicies()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /** Create a new policy or update an existing one. Validation happens inside the use case. */
    fun savePolicy(policy: PolicyTemplate) {
        viewModelScope.launch {
            savePolicyUseCase(policy)
        }
    }

    /** Permanently delete a policy by its id. */
    fun deletePolicy(id: String) {
        viewModelScope.launch {
            repository.deletePolicy(id)
        }
    }
}
