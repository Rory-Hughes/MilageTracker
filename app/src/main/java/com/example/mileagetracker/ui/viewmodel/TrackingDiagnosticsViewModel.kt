package com.example.mileagetracker.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.model.PermissionDiagnostic
import com.example.mileagetracker.domain.usecase.tracking.ValidateTrackingPermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.diagPrefs by preferencesDataStore(name = "tracking_diag_prefs")
private val KEY_BATTERY_DISMISSED = booleanPreferencesKey("battery_warning_dismissed")

/**
 * ViewModel for [com.example.mileagetracker.ui.screen.TrackingDiagnosticsScreen]
 * and the [com.example.mileagetracker.ui.screen.DashboardScreen] banner.
 *
 * ## Re-check on resume
 * [refresh] is cheap (pure in-memory checks — no IO) so it is designed to
 * be called from `LaunchedEffect(lifecycleState)` inside the screen/banner
 * composable whenever the lifecycle returns to RESUMED.  This ensures the
 * UI reflects the latest state after the user returns from system Settings.
 *
 * ## Sharing between screens
 * Both [com.example.mileagetracker.ui.screen.DashboardScreen] and
 * [com.example.mileagetracker.ui.screen.TrackingDiagnosticsScreen] use the same
 * ViewModel instance (scoped to the nav back-stack entry via
 * `hiltViewModel()`) so they always display the same data without a
 * redundant second check.
 *
 * ## Dismiss behaviour
 * The battery optimization warning can be dismissed by the user via
 * [dismissBatteryWarning]. The dismissal is persisted in DataStore so it
 * survives process death. Calling [clearBatteryDismissal] (e.g. when the
 * user taps "Fix") resets the flag so the banner re-evaluates on return
 * from system Settings.
 */
@HiltViewModel
class TrackingDiagnosticsViewModel @Inject constructor(
    private val validateTrackingPermissionsUseCase: ValidateTrackingPermissionsUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _diagnostics =
        MutableStateFlow<List<PermissionDiagnostic>>(emptyList())

    /**
     * Latest list of [PermissionDiagnostic]s.
     *
     * - Empty list while the first [refresh] has not yet been called.
     * - `listOf(AllClear)` when all checks pass.
     * - One or more non-AllClear entries when action is needed.
     */
    val diagnostics: StateFlow<List<PermissionDiagnostic>> = _diagnostics.asStateFlow()

    /**
     * Persisted flag indicating the user has chosen to dismiss the battery
     * optimization warning. When `true` the dashboard banner for that specific
     * issue is hidden even if
     * [com.example.mileagetracker.domain.model.PermissionDiagnostic.BatteryOptimizationActive] is still present in
     * [diagnostics].
     */
    // Change the StateFlow type to Boolean? — null means "not yet read from disk"
    val batteryWarningDismissed: StateFlow<Boolean?> = context.diagPrefs.data
        .map { prefs -> prefs[KEY_BATTERY_DISMISSED] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /**
     * Re-runs [ValidateTrackingPermissionsUseCase] and updates [diagnostics].
     *
     * Safe to call from the Main dispatcher — the use-case performs only
     * in-memory checks with no IO.
     */
    fun refresh() {
        _diagnostics.value = validateTrackingPermissionsUseCase()
    }

    /**
     * Persists the user's choice to dismiss the battery optimization banner.
     * The banner will no longer be shown on the dashboard until the user
     * navigates to the diagnostics screen and the issue is resolved, or until
     * [clearBatteryDismissal] is called.
     */
    fun dismissBatteryWarning() {
        viewModelScope.launch {
            context.diagPrefs.edit { prefs ->
                prefs[KEY_BATTERY_DISMISSED] = true
            }
        }
    }

    /**
     * Clears the battery-warning dismissal flag. Call this before navigating
     * to the system settings fix screen so the banner re-appears if the issue
     * is still present when the user returns.
     */
    fun clearBatteryDismissal() {
        viewModelScope.launch {
            context.diagPrefs.edit { prefs ->
                prefs[KEY_BATTERY_DISMISSED] = false
            }
        }
    }

    init {
        refresh()
    }
}
