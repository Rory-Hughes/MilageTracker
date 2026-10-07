package com.example.mileagetracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.places.PlacePrediction
import com.example.mileagetracker.domain.places.PlacesService
import com.example.mileagetracker.domain.repository.DirectionsRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.drive.CreateManualTripUseCase
import com.example.mileagetracker.domain.usecase.drive.UpdateDriveUseCase
import com.example.mileagetracker.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ManualTripEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val driveRepository: DriveRepository,
    private val createManualTripUseCase: CreateManualTripUseCase,
    private val updateDriveUseCase: UpdateDriveUseCase,
    private val placesService: PlacesService,
    private val directionsRepository: DirectionsRepository,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val driverUid: String =
        currentUserProvider.requireUid()

    private val repairDriveId: Int? = savedStateHandle.get<Int>(Screen.ARG_DRIVE_ID)?.takeIf { it > 0 }

    private val _formState = MutableStateFlow(ManualTripFormState(repairDriveId = repairDriveId))
    val formState: StateFlow<ManualTripFormState> = _formState.asStateFlow()

    private val _events = Channel<ManualTripEntryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _startSuggestions = MutableStateFlow<List<PlacePrediction>>(emptyList())
    val startSuggestions: StateFlow<List<PlacePrediction>> = _startSuggestions.asStateFlow()

    private val _endSuggestions = MutableStateFlow<List<PlacePrediction>>(emptyList())
    val endSuggestions: StateFlow<List<PlacePrediction>> = _endSuggestions.asStateFlow()

    // Per-field debounce handles so a burst of keystrokes issues a single
    // autocomplete request and a newer query cancels an in-flight older one
    // (preventing both over-fetching and out-of-order results).
    private var startSearchJob: Job? = null
    private var endSearchJob: Job? = null

    init {
        if (repairDriveId != null) {
            viewModelScope.launch {
                val drive = driveRepository.getDriveById(repairDriveId)
                if (drive != null) {
                    _formState.update {
                        it.copy(
                            startLocation = drive.startLocation,
                            endLocation = drive.endLocation,
                            distanceKmText = drive.distanceKm.toString(),
                            startedAtMs = drive.startedAt,
                            endedAtMs = drive.endedAt ?: 0L,
                            category = drive.tripCategory,
                            purposeText = drive.purposeText ?: "",
                            notes = drive.notes ?: "",
                        ).revalidated()
                    }
                } else {
                    _events.send(ManualTripEntryEvent.Error("Trip not found — it may have been deleted."))
                }
            }
        }
    }

    // ── Field updaters ─────────────────────────────────────────────────────────

    val updateStartLocation: (String) -> Unit = { value ->
        _formState.update {
            it.copy(startLocation = value, startPlaceId = null, isDistanceFromMaps = false).revalidated()
        }
        startSearchJob?.cancel()
        if (value.length >= 3) {
            startSearchJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                searchPredictions(value, isStart = true)
            }
        } else {
            _startSuggestions.value = emptyList()
        }
    }

    val updateEndLocation: (String) -> Unit = { value ->
        _formState.update {
            it.copy(endLocation = value, endPlaceId = null, isDistanceFromMaps = false).revalidated()
        }
        endSearchJob?.cancel()
        if (value.length >= 3) {
            endSearchJob = viewModelScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                searchPredictions(value, isStart = false)
            }
        } else {
            _endSuggestions.value = emptyList()
        }
    }

    val selectStartSuggestion: (PlacePrediction) -> Unit = { suggestion ->
        _formState.update {
            it.copy(startLocation = suggestion.fullText, startPlaceId = suggestion.placeId).revalidated()
        }
        _startSuggestions.value = emptyList()
        val endId = _formState.value.endPlaceId
        if (endId != null) calculateDistanceIfReady(suggestion.placeId, endId)
    }

    val selectEndSuggestion: (PlacePrediction) -> Unit = { suggestion ->
        _formState.update {
            it.copy(endLocation = suggestion.fullText, endPlaceId = suggestion.placeId).revalidated()
        }
        _endSuggestions.value = emptyList()
        val startId = _formState.value.startPlaceId
        if (startId != null) calculateDistanceIfReady(startId, suggestion.placeId)
    }

    val clearStartSuggestions: () -> Unit = { _startSuggestions.value = emptyList() }
    val clearEndSuggestions: () -> Unit = { _endSuggestions.value = emptyList() }

    val updateDistanceKm: (String) -> Unit = { value ->
        _formState.update { it.copy(distanceKmText = value, isDistanceFromMaps = false).revalidated() }
    }

    val updateStartedAt: (Long) -> Unit = { epochMs ->
        _formState.update { it.copy(startedAtMs = epochMs).revalidated() }
    }

    val updateEndedAt: (Long) -> Unit = { epochMs ->
        _formState.update { it.copy(endedAtMs = epochMs).revalidated() }
    }

    val updateCategory: (TripCategory) -> Unit = { category ->
        _formState.update { it.copy(category = category).revalidated() }
    }

    val updatePurposeText: (String) -> Unit = { value ->
        _formState.update { it.copy(purposeText = value).revalidated() }
    }

    val updateVehicle: (VehicleEntity?) -> Unit = { vehicle ->
        _formState.update { it.copy(selectedVehicle = vehicle).revalidated() }
    }

    val updateNotes: (String) -> Unit = { value ->
        _formState.update { it.copy(notes = value) }
    }

    // ── Places autocomplete ────────────────────────────────────────────────────

    private suspend fun searchPredictions(query: String, isStart: Boolean) {
        runCatching {
            val predictions = placesService.autocomplete(query)
            if (isStart) _startSuggestions.value = predictions else _endSuggestions.value = predictions
        }.onFailure { error ->
            Log.e(TAG, "Address autocomplete error", error)
            if (isStart) _startSuggestions.value = emptyList() else _endSuggestions.value = emptyList()
            _events.send(ManualTripEntryEvent.Error("Could not load address suggestions."))
        }
    }

    // ── Distance calculation ───────────────────────────────────────────────────

    private fun calculateDistanceIfReady(startId: String, endId: String) {
        viewModelScope.launch {
            runCatching {
                // Launch both geocoding lookups before awaiting so they run
                // concurrently — roughly halves the round-trip latency.
                val startDeferred = async { placesService.fetchCoordinates(startId) }
                val endDeferred = async { placesService.fetchCoordinates(endId) }
                val start = startDeferred.await()
                val end = endDeferred.await()
                val km = directionsRepository.getDrivingDistanceKm(
                    start.latitude,
                    start.longitude,
                    end.latitude,
                    end.longitude,
                )
                _formState.update {
                    it.copy(
                        // Locale.ROOT keeps a '.' decimal separator so the value
                        // round-trips through the field's toFloatOrNull() parsing.
                        distanceKmText = String.format(Locale.ROOT, "%.1f", km),
                        isDistanceFromMaps = true,
                    ).revalidated()
                }
            }.onFailure { error ->
                Log.e(TAG, "Directions distance error", error)
                _events.send(ManualTripEntryEvent.Error("Could not calculate route distance from Google Maps."))
            }
        }
    }

    // ── Save ───────────────────────────────────────────────────────────────────

    val save: () -> Unit = {
        val state = _formState.value
        if (state.isSaveEnabled && !state.isSaving) {
            _formState.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                runCatching {
                    if (state.isRepairMode) saveRepair(state) else saveNewTrip(state)
                }.onFailure { error ->
                    val message = if (error is IllegalArgumentException) {
                        error.message ?: "Validation failed."
                    } else {
                        "Unexpected error: ${error.message}"
                    }
                    _events.send(ManualTripEntryEvent.Error(message))
                }.also {
                    _formState.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    private suspend fun saveRepair(state: ManualTripFormState) {
        val distanceKm = state.distanceKmText.trim().toFloatOrNull() ?: 0f
        val existing = driveRepository.getDriveById(state.repairDriveId!!)
            ?: run {
                _events.send(ManualTripEntryEvent.Error("Trip no longer exists."))
                return
            }
        val repaired = existing.copy(
            startLocation = state.startLocation.trim(),
            endLocation = state.endLocation.trim(),
            distanceKm = distanceKm,
            startedAt = state.startedAtMs,
            endedAt = state.endedAtMs.takeIf { it > 0L },
            tripCategory = state.category,
            purposeText = state.purposeText.trim().ifBlank { null },
            vehicleId = state.selectedVehicle?.id,
            notes = state.notes.trim().ifBlank { null },
            trackingSource = TrackingSource.MANUAL_REPAIR,
            classificationStatus = if (state.category.isClassified()) {
                ClassificationStatus.USER_CONFIRMED
            } else {
                existing.classificationStatus
            },
            classificationConfidence = 1.0f,
            updatedAt = System.currentTimeMillis(),
        )
        if (updateDriveUseCase(repaired, driverUid)) {
            _events.send(ManualTripEntryEvent.SaveSuccess(repaired.id, isRepair = true))
        } else {
            _events.send(ManualTripEntryEvent.Error("Could not save repair — trip may have been deleted."))
        }
    }

    private suspend fun saveNewTrip(state: ManualTripFormState) {
        val distanceKm = state.distanceKmText.trim().toFloatOrNull() ?: 0f
        val draft = DriveEntity(
            startLocation = state.startLocation.trim(),
            endLocation = state.endLocation.trim(),
            distanceKm = distanceKm,
            startedAt = state.startedAtMs,
            endedAt = state.endedAtMs.takeIf { it > 0L },
            tripCategory = state.category,
            purposeText = state.purposeText.trim().ifBlank { null },
            vehicleId = state.selectedVehicle?.id,
            notes = state.notes.trim().ifBlank { null },
            driverUid = driverUid,
            trackingSource = TrackingSource.MANUAL_ENTRY,
        )
        val newId = createManualTripUseCase(draft, driverUid)
        _events.send(ManualTripEntryEvent.SaveSuccess(newId, isRepair = false))
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private fun ManualTripFormState.revalidated(): ManualTripFormState {
        val errors = mutableMapOf<String, String>()
        if (startLocation.isBlank()) errors[ManualTripFormState.FIELD_START_LOCATION] = "Start location is required."
        if (endLocation.isBlank()) errors[ManualTripFormState.FIELD_END_LOCATION] = "End location is required."
        val km = distanceKmText.trim().toFloatOrNull()
        if (km == null || km <= 0f) errors[ManualTripFormState.FIELD_DISTANCE] = "Enter a distance greater than 0 km."
        if (startedAtMs <= 0L) errors[ManualTripFormState.FIELD_START_TIME] = "Start date and time are required."
        if (endedAtMs > 0L && endedAtMs <= startedAtMs) {
            errors[ManualTripFormState.FIELD_END_TIME] = "End time must be after start time."
        }
        if (category == TripCategory.CUSTOM && purposeText.isBlank()) {
            errors[ManualTripFormState.FIELD_PURPOSE] = "Purpose is required for Custom category."
        }
        return copy(fieldErrors = errors, isSaveEnabled = errors.isEmpty())
    }

    companion object {
        private const val TAG = "ManualTripVM"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}

private fun TripCategory.isClassified(): Boolean = this != TripCategory.UNCLASSIFIED

sealed interface ManualTripEntryEvent {
    data class SaveSuccess(val driveId: Int, val isRepair: Boolean) : ManualTripEntryEvent
    data class Error(val message: String) : ManualTripEntryEvent
}
