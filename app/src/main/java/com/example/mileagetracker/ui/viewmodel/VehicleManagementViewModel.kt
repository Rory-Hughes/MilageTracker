package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.OdometerBoundaryType
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.model.OdometerCaptureType
import com.example.mileagetracker.domain.repository.VehicleDefaultSettingsRepository
import com.example.mileagetracker.domain.usecase.odometer.OdometerSaveResult
import com.example.mileagetracker.domain.usecase.odometer.SaveOdometerCaptureUseCase
import com.example.mileagetracker.domain.usecase.vehicle.DeleteVehicleUseCase
import com.example.mileagetracker.domain.usecase.vehicle.GetActiveVehiclesUseCase
import com.example.mileagetracker.domain.usecase.vehicle.GetVehiclesUseCase
import com.example.mileagetracker.domain.usecase.vehicle.SaveVehicleUseCase
import com.example.mileagetracker.domain.usecase.vehicle.SetDefaultVehicleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel for [VehicleManagementScreen].
 *
 * ## State
 * - [vehicles]       — reactive list of **all** vehicles (active + inactive);
 *   used by the management screen to show/deactivate all vehicles.
 * - [activeVehicles] — reactive list of **active-only** vehicles for the
 *   current fiscal year; hoisted by [MainAppShell] and passed to
 *   [TripDetailScreen]'s vehicle picker (Task 3.1).
 * - [events]         — one-shot [VehicleUiEvent]s for save-success, errors, etc.
 *
 * ## Owner UID
 * Vehicle ownership is always scoped to the signed-in Firebase account.
 */
@HiltViewModel
class VehicleManagementViewModel @Inject constructor(
    private val getVehiclesUseCase: GetVehiclesUseCase,
    private val getActiveVehiclesUseCase: GetActiveVehiclesUseCase,
    private val saveVehicleUseCase: SaveVehicleUseCase,
    private val saveOdometerCaptureUseCase: SaveOdometerCaptureUseCase,
    private val setDefaultVehicleUseCase: SetDefaultVehicleUseCase,
    private val deleteVehicleUseCase: DeleteVehicleUseCase,
    private val vehicleDefaultSettingsRepository: VehicleDefaultSettingsRepository,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    val ownerUid = currentUserProvider.requireUid()
    private val fiscalYear = Calendar.getInstance().get(Calendar.YEAR)

    /** All vehicles (active + inactive). Used by the management list screen. */
    val vehicles: StateFlow<List<VehicleEntity>> =
        getVehiclesUseCase(ownerUid)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * Active-only vehicles for the current fiscal year, default-first.
     *
     * Collected by [MainAppShell] and passed as `activeVehicles` to
     * [TripDetailScreen]'s [VehiclePickerDropdown].
     */
    val activeVehicles: StateFlow<List<VehicleEntity>> =
        getActiveVehiclesUseCase(ownerUid, fiscalYear)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    private val _events = Channel<VehicleUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Actions ──────────────────────────────────────────────────────────────

    fun saveVehicle(vehicle: VehicleEntity) {
        saveVehicle(VehicleSaveRequest(vehicle = vehicle))
    }

    fun saveVehicle(request: VehicleSaveRequest) {
        viewModelScope.launch {
            try {
                val savedVehicleId = saveVehicleUseCase(request.vehicle)
                saveOpeningCaptureIfNeeded(
                    request = request,
                    savedVehicleId = savedVehicleId,
                )
                _events.send(VehicleUiEvent.SaveSuccess)
            } catch (e: IllegalArgumentException) {
                _events.send(VehicleUiEvent.Error(e.message ?: "Validation failed"))
            }
        }
    }

    private suspend fun saveOpeningCaptureIfNeeded(
        request: VehicleSaveRequest,
        savedVehicleId: Int,
    ) {
        val photoUri = request.openingOdometerPhotoUri ?: return
        val readingKm = request.vehicle.openingOdometerKm?.toInt() ?: return
        val capture = OdometerCapture(
            vehicleId = savedVehicleId,
            captureDate = System.currentTimeMillis(),
            readingKm = readingKm,
            taxYear = request.vehicle.fiscalYear,
            photoUri = photoUri,
            captureType = OdometerCaptureType.PHOTO_ASSISTED,
            boundaryType = OdometerBoundaryType.OPENING,
            notes = "Opening odometer evidence for ${request.vehicle.fiscalYear}",
        )
        when (val result = saveOdometerCaptureUseCase(capture)) {
            is OdometerSaveResult.InvalidReading -> throw IllegalArgumentException(result.reason)
            is OdometerSaveResult.RegressionWarning -> Unit
            is OdometerSaveResult.Success -> Unit
        }
    }

    fun setDefault(vehicleId: Int) {
        viewModelScope.launch {
            try {
                setDefaultVehicleUseCase(
                    vehicleId = vehicleId,
                    ownerUid = ownerUid,
                    fiscalYear = fiscalYear,
                )
                vehicleDefaultSettingsRepository.setDefaultVehicleId(vehicleId)
                _events.send(VehicleUiEvent.DefaultSet)
            } catch (e: IllegalArgumentException) {
                _events.send(VehicleUiEvent.Error(e.message ?: "Could not set default"))
            }
        }
    }

    fun deactivate(vehicle: VehicleEntity) {
        viewModelScope.launch {
            runCatching {
                saveVehicleUseCase(
                    vehicle.copy(
                        isActive = false,
                        isDefault = false,
                    )
                )
                if (vehicle.isDefault) {
                    vehicleDefaultSettingsRepository.setDefaultVehicleId(null)
                }
                _events.send(VehicleUiEvent.DeactivateSuccess)
            }.onFailure { error ->
                _events.send(VehicleUiEvent.Error(error.message ?: "Could not deactivate"))
            }
        }
    }

    fun reactivate(vehicle: VehicleEntity) {
        viewModelScope.launch {
            runCatching {
                saveVehicleUseCase(vehicle.copy(isActive = true))
                _events.send(VehicleUiEvent.ReactivateSuccess)
            }.onFailure { error ->
                _events.send(VehicleUiEvent.Error(error.message ?: "Could not reactivate"))
            }
        }
    }

    /**
     * Permanently delete [vehicle] if it has no associated trips.
     *
     * Emits [VehicleUiEvent.DeleteSuccess] on success, or
     * [VehicleUiEvent.Error] if the vehicle has trips or deletion fails.
     */
    fun deleteVehicle(vehicle: VehicleEntity) {
        viewModelScope.launch {
            runCatching {
                deleteVehicleUseCase(vehicle)
                _events.send(VehicleUiEvent.DeleteSuccess)
            }.onFailure { error ->
                val message = if (error is IllegalStateException) {
                    error.message ?: "Cannot delete vehicle"
                } else {
                    error.message ?: "Could not delete vehicle"
                }
                _events.send(VehicleUiEvent.Error(message))
            }
        }
    }
}

/** One-shot UI events emitted by [VehicleManagementViewModel]. */
sealed interface VehicleUiEvent {
    data object SaveSuccess : VehicleUiEvent
    data object DefaultSet : VehicleUiEvent
    data object DeactivateSuccess : VehicleUiEvent
    data object ReactivateSuccess : VehicleUiEvent
    data object DeleteSuccess : VehicleUiEvent
    data class Error(val message: String) : VehicleUiEvent
}

data class VehicleSaveRequest(
    val vehicle: VehicleEntity,
    val openingOdometerPhotoUri: String? = null,
)
