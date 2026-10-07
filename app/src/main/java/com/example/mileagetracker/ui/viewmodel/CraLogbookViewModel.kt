package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.LogbookEntry
import com.example.mileagetracker.domain.model.LogbookSummary
import com.example.mileagetracker.domain.usecase.drive.GetCraLogbookTripsUseCase
import com.example.mileagetracker.domain.usecase.vehicle.GetActiveVehiclesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel for [CraLogbookScreen].
 *
 * ## State
 * - [selectedYear]       — tax year filter; changed via [selectYear].
 * - [selectedVehicleId]  — optional vehicle filter; changed via [selectVehicle].
 * - [selectedCategories] — set of CRA-eligible categories to include;
 *                          defaults to Business only; toggled via [toggleCategory].
 * - [uiState]            — derived [CraLogbookUiState] recomputed whenever
 *                          any filter changes.
 *
 * ## Data flow
 * The three filter flows are combined into a single trigger; each emission
 * calls [GetCraLogbookTripsUseCase] and maps the result to the appropriate
 * [CraLogbookUiState] variant.
 *
 * ## Driver UID
 * Resolved via [CurrentUserProvider] at construction time. Authenticated solo
 * flows never read legacy local rows until the user explicitly claims them.
 */
@HiltViewModel
class CraLogbookViewModel @Inject constructor(
    private val getCraLogbookTripsUseCase: GetCraLogbookTripsUseCase,
    private val getActiveVehiclesUseCase: GetActiveVehiclesUseCase,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val driverUid: String =
        currentUserProvider.requireUid()

    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    private val _selectedYear = MutableStateFlow(currentYear)
    private val _selectedVehicleId = MutableStateFlow<Int?>(null)
    private val _selectedCategories = MutableStateFlow(
        setOf(TripCategory.BUSINESS)
    )

    /** Selected calendar month (1–12), or null for "All months" (default). */
    private val _selectedMonth = MutableStateFlow<Int?>(null)

    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()
    val selectedVehicleId: StateFlow<Int?> = _selectedVehicleId.asStateFlow()
    val selectedCategories: StateFlow<Set<TripCategory>> = _selectedCategories.asStateFlow()
    val selectedMonth: StateFlow<Int?> = _selectedMonth.asStateFlow()

    /** Range of selectable years — matches [ReportsViewModel.availableYears]. */
    val availableYears: List<Int> = (currentYear - 5..currentYear + 1).toList()

    /** CRA-eligible categories available for the filter chips. */
    val filterableCategories: List<TripCategory> = TripCategory.craEligibleCategories

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeVehicles: StateFlow<List<VehicleEntity>> =
        _selectedYear
            .flatMapLatest { year -> getActiveVehiclesUseCase(driverUid, year) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    init {
        viewModelScope.launch {
            activeVehicles.collect { vehicles ->
                val selectedVehicleId = _selectedVehicleId.value ?: return@collect
                if (vehicles.none { it.id == selectedVehicleId }) {
                    _selectedVehicleId.value = null
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CraLogbookUiState> =
        combine(
            _selectedYear,
            _selectedVehicleId,
            _selectedCategories,
            _selectedMonth,
        ) { year, vehicleId, cats, month ->
            LogbookFilters(year, vehicleId, cats, month)
        }.flatMapLatest { filters ->
            flow {
                emit(CraLogbookUiState.Loading)
                runCatching {
                    val (entries, summary) = getCraLogbookTripsUseCase(
                        driverUid = driverUid,
                        year = filters.year,
                        vehicleId = filters.vehicleId,
                        categories = filters.categories.toList()
                            .ifEmpty { listOf(TripCategory.BUSINESS) },
                        month = filters.month,
                    )
                    CraLogbookUiState.Success(entries, summary)
                }.fold(
                    onSuccess = { emit(it) },
                    onFailure = { emit(CraLogbookUiState.Error(it.message ?: "Failed to load logbook.")) },
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CraLogbookUiState.Loading,
        )

    private data class LogbookFilters(
        val year: Int,
        val vehicleId: Int?,
        val categories: Set<TripCategory>,
        val month: Int?,
    )

    fun selectYear(year: Int) {
        _selectedYear.value = year
    }

    /** @param month 1–12 to filter to a single month, or null for all months. */
    fun selectMonth(month: Int?) {
        _selectedMonth.value = month
    }

    fun selectVehicle(vehicleId: Int?) {
        _selectedVehicleId.value = vehicleId
    }

    /**
     * Toggles [category] in/out of [selectedCategories].
     * At least one category is always kept selected; toggling the last one
     * selected is a no-op.
     */
    fun toggleCategory(category: TripCategory) {
        val current = _selectedCategories.value
        val updated = if (category in current) {
            if (current.size == 1) {
                current // keep at least one
            } else {
                current - category
            }
        } else {
            current + category
        }
        _selectedCategories.value = updated
    }
}

// ── UI state ────────────────────────────────────────────────────────────────────────────────

sealed interface CraLogbookUiState {
    data object Loading : CraLogbookUiState

    data class Success(
        val entries: List<LogbookEntry>,
        val summary: LogbookSummary,
    ) : CraLogbookUiState

    data class Error(val message: String) : CraLogbookUiState
}
