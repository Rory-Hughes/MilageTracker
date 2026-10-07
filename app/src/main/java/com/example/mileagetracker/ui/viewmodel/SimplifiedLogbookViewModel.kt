package com.example.mileagetracker.ui.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.model.SimplifiedLogbookStatus
import com.example.mileagetracker.domain.usecase.export.CheckSimplifiedLogbookEligibilityUseCase
import com.example.mileagetracker.domain.usecase.export.CheckSimplifiedLogbookEligibilityUseCase.Companion.PREF_BASE_YEAR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for [SimplifiedLogbookGuidanceScreen]. */
sealed interface SimplifiedLogbookUiState {
    data object Loading : SimplifiedLogbookUiState
    data class Success(val status: SimplifiedLogbookStatus) : SimplifiedLogbookUiState
    data class Error(val message: String) : SimplifiedLogbookUiState
}

/**
 * ViewModel for [SimplifiedLogbookGuidanceScreen].
 *
 * Loads [SimplifiedLogbookStatus] via [CheckSimplifiedLogbookEligibilityUseCase]
 * and exposes it as [uiState].  Also handles base-year selection when the user
 * has multiple qualifying prior years, persisting the selection to [DataStore].
 *
 * @param checkEligibilityUseCase  Use case that computes eligibility.
 * @param dataStore                App-wide [DataStore<Preferences>] for persisting
 *                                 the selected base year and resolving the driver UID.
 */
@HiltViewModel
class SimplifiedLogbookViewModel @Inject constructor(
    private val checkEligibilityUseCase: CheckSimplifiedLogbookEligibilityUseCase,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val baseYearKey = intPreferencesKey(PREF_BASE_YEAR)
    private val driverUidKey = stringPreferencesKey("driver_uid")

    private val _uiState = MutableStateFlow<SimplifiedLogbookUiState>(
        SimplifiedLogbookUiState.Loading,
    )
    val uiState: StateFlow<SimplifiedLogbookUiState> = _uiState.asStateFlow()

    init {
        loadStatus()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Re-runs the eligibility check.  Called from the screen on pull-to-refresh
     * or after the user selects a different base year.
     */
    fun refresh() {
        loadStatus()
    }

    /**
     * Persists [year] as the user's preferred base year and re-evaluates
     * eligibility so the screen reflects the new selection immediately.
     */
    fun selectBaseYear(year: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[baseYearKey] = year }
            loadStatus()
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun loadStatus() {
        viewModelScope.launch {
            _uiState.value = SimplifiedLogbookUiState.Loading
            runCatching {
                val uid = resolveDriverUid()
                checkEligibilityUseCase(driverUid = uid)
            }.onSuccess { status ->
                _uiState.value = SimplifiedLogbookUiState.Success(status)
            }.onFailure { err ->
                _uiState.value = SimplifiedLogbookUiState.Error(
                    err.message ?: "Unable to check eligibility.",
                )
            }
        }
    }

    /**
     * Reads the signed-in driver UID from the app-wide DataStore.
     * Mirrors the key used by the rest of the app's auth/session layer.
     */
    private suspend fun resolveDriverUid(): String =
        dataStore.data
            .map { prefs -> prefs[driverUidKey] ?: "" }
            .first()
}
