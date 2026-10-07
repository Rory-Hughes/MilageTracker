package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.usecase.vehicle.GetActiveVehiclesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class ExportScreenViewModel @Inject constructor(
    private val getActiveVehiclesUseCase: GetActiveVehiclesUseCase,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val ownerUid = currentUserProvider.requireUid()
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    private val _selectedYear = MutableStateFlow(currentYear)
    private val _selectedVehicleId = MutableStateFlow<Int?>(null)
    private val _selectedCategories = MutableStateFlow(setOf(TripCategory.BUSINESS))

    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()
    val selectedVehicleId: StateFlow<Int?> = _selectedVehicleId.asStateFlow()
    val selectedCategories: StateFlow<Set<TripCategory>> = _selectedCategories.asStateFlow()
    val availableYears: List<Int> = (currentYear - 5..currentYear + 1).toList()
    val filterableCategories: List<TripCategory> = TripCategory.craEligibleCategories

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeVehicles: StateFlow<List<VehicleEntity>> =
        _selectedYear
            .flatMapLatest { year -> getActiveVehiclesUseCase(ownerUid, year) }
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

    fun selectYear(year: Int) {
        _selectedYear.value = year
    }

    fun selectVehicle(vehicleId: Int?) {
        _selectedVehicleId.value = vehicleId
    }

    fun toggleCategory(category: TripCategory) {
        val current = _selectedCategories.value
        _selectedCategories.value = if (category in current) {
            if (current.size == 1) current else current - category
        } else {
            current + category
        }
    }
}
