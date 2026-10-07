package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.CalendarHint
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.model.TripException
import com.example.mileagetracker.domain.model.TripSuggestion
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.repository.VehicleRepository
import com.example.mileagetracker.domain.usecase.GetTripExceptionsUseCase
import com.example.mileagetracker.domain.usecase.calendar.GetCalendarHintsForTripUseCase
import com.example.mileagetracker.domain.usecase.drive.ClassifyTripUseCase
import com.example.mileagetracker.domain.usecase.drive.ResolveDriveLocationsUseCase
import com.example.mileagetracker.domain.usecase.drive.UpdateDriveUseCase
import com.example.mileagetracker.domain.usecase.suggestion.ApplySuggestionUseCase
import com.example.mileagetracker.domain.usecase.suggestion.GetTripSuggestionsUseCase
import com.example.mileagetracker.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [com.example.mileagetracker.ui.screen.TripDetailScreen].
 *
 * Reads [Screen.ARG_DRIVE_ID] from [SavedStateHandle] to identify the
 * trip being viewed / classified.
 *
 * ## Task 4.1 additions
 * After the drive loads, if [ClassificationStatus] is [ClassificationStatus.PENDING] or
 * [UNCLASSIFIED], [GetTripSuggestionsUseCase] is invoked and the top
 * result is exposed as [topSuggestion].  The UI renders a
 * [com.example.mileagetracker.ui.component.SuggestionBannerCard] while this is non-null.
 *
 * ## Task 7.4 additions
 * After loading an unclassified trip, [GetCalendarHintsForTripUseCase] is
 * invoked on an IO coroutine **only if** the user has not disabled calendar
 * hints via Settings.  Results are exposed as [calendarHints].
 *
 * ## Task 7.5 additions
 * [GetTripExceptionsUseCase] is queried for the current [driveId] and the
 * first matching active exception is exposed as [activeException].  The UI
 * renders [com.example.mileagetracker.ui.audit.ExceptionFlagBanner] while this is non-null.  The banner is
 * session-dismissed via [dismissExceptionBanner] which sets
 * [_bannerDismissed] to true without mutating the underlying data.
 *
 * ## Phase 2 (Route Map) additions — tasks 2.1 / 2.3
 * [route] is now a dedicated top-level [StateFlow] independent of [uiState].
 * [routeMapState] derives [RouteMapUiState] from [route]:
 * emitting [com.example.mileagetracker.ui.viewmodel.RouteMapUiState.NoRoute]
 * for 0–1 points and [com.example.mileagetracker.ui.viewmodel.RouteMapUiState.Ready]
 * for ≥2 points once the bounds can be computed.
 */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val driveRepository: DriveRepository,
    private val vehicleRepository: VehicleRepository,
    private val settingsRepository: SettingsRepository,
    private val updateDriveUseCase: UpdateDriveUseCase,
    private val resolveDriveLocationsUseCase: ResolveDriveLocationsUseCase,
    private val classifyTripUseCase: ClassifyTripUseCase,
    private val getTripSuggestionsUseCase: GetTripSuggestionsUseCase,
    private val applySuggestionUseCase: ApplySuggestionUseCase,
    private val getCalendarHintsUseCase: GetCalendarHintsForTripUseCase,
    private val getTripExceptionsUseCase: GetTripExceptionsUseCase,
    currentUserProvider: CurrentUserProvider,
) : ViewModel() {

    private val driveId: Int? = savedStateHandle[Screen.ARG_DRIVE_ID]

    private val driverUid: String =
        currentUserProvider.requireUid()

    private val _uiState = MutableStateFlow<TripDetailUiState>(TripDetailUiState.Loading)
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<TripDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Phase 2, Task 2.1: dedicated route StateFlow ──────────────────────

    /**
     * Ordered list of GPS fixes for the current trip.  Starts empty and is
     * populated after [loadDrive] fetches points from the repository.
     * Kept independent of [uiState] so map recomposition is isolated from
     * form-field state changes.
     */
    private val _route = MutableStateFlow<List<RoutePoint>>(emptyList())
    val route: StateFlow<List<RoutePoint>> = _route.asStateFlow()

    // ── Phase 2, Task 2.3: derive RouteMapUiState from route ──────────────

    /**
     * Current map render state derived from [route].
     *
     * - Empty list or single point → [RouteMapUiState.NoRoute]
     * - ≥2 points → [RouteMapUiState.Ready] with bounds and markers computed
     *
     * Starts as [RouteMapUiState.Loading] until the first emission from
     * [_route] is processed.
     */
    val routeMapState: StateFlow<RouteMapUiState> = _route
        .map { points -> points.toRouteMapUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RouteMapUiState.Loading,
        )

    // ── Task 4.1: suggestion state ─────────────────────────────────────────

    private val _topSuggestion = MutableStateFlow<TripSuggestion?>(null)
    val topSuggestion: StateFlow<TripSuggestion?> = _topSuggestion.asStateFlow()

    // ── Task 7.4: calendar hints state ────────────────────────────────────

    private val _calendarHints = MutableStateFlow<List<CalendarHint>>(emptyList())
    val calendarHints: StateFlow<List<CalendarHint>> = _calendarHints.asStateFlow()

    // ── Task 7.5: exception banner state ──────────────────────────────────

    /**
     * True once the user has tapped "Dismiss" on [ExceptionFlagBanner] in
     * this screen session.  Reset to false if the screen is recreated.
     * Does not persist; the exception itself remains active in the database.
     */
    private val _bannerDismissed = MutableStateFlow(false)

    /**
     * The first active [TripException] for this trip, or null when none
     * exist or the banner has been session-dismissed.
     *
     * Derived from [GetTripExceptionsUseCase] filtered to [driveId].  The
     * upstream flow is combined with [_bannerDismissed] so the banner hides
     * immediately on dismiss without waiting for a DB re-emission.
     */
    val activeException: StateFlow<TripException?> =
        combine(
            getTripExceptionsUseCase(driverUid),
            _bannerDismissed,
        ) { exceptions, dismissed ->
            if (dismissed) {
                null
            } else {
                exceptions.firstOrNull { it.tripId == driveId && it.isActive }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    init {
        if (driveId != null) {
            loadDrive()
        } else {
            _uiState.value = TripDetailUiState.NotFound
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────

    /**
     * Tracks the in-flight [loadDrive] coroutine. [loadDrive] is re-invoked
     * after user actions (classify, apply suggestion, apply hint); cancelling
     * the previous load before starting a new one prevents a slower earlier
     * fetch from overwriting the result of a newer one.
     */
    private var loadJob: Job? = null
    private val locationEnrichmentInFlight = mutableSetOf<Int>()

    private fun loadDrive() {
        val id = driveId ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val drive = driveRepository.getDriveById(id)
            if (drive != null) {
                val vehicle = drive.vehicleId?.let { vehicleRepository.getVehicleById(it) }
                _uiState.value = TripDetailUiState.Success(
                    drive = drive,
                    vehicle = vehicle,
                )
                // Phase 2 task 2.1: populate dedicated route StateFlow
                _route.value = driveRepository.getRoutePointsForDrive(id)
                enqueueCoordinateLocationBackfill(drive)
                if (drive.classificationStatus == ClassificationStatus.PENDING) {
                    fetchSuggestion(drive)
                    if (drive.purposeText.isNullOrBlank()) {
                        fetchCalendarHints(drive)
                    }
                }
            } else {
                _uiState.value = TripDetailUiState.NotFound
            }
        }
    }

    private fun enqueueCoordinateLocationBackfill(drive: DriveEntity) {
        if (!resolveDriveLocationsUseCase.requiresResolution(drive)) {
            return
        }

        synchronized(locationEnrichmentInFlight) {
            if (!locationEnrichmentInFlight.add(drive.id)) {
                return
            }
        }

        viewModelScope.launch {
            try {
                val resolvedDrive = resolveDriveLocationsUseCase(drive)
                if (resolvedDrive != drive) {
                    val updated = resolvedDrive.copy(updatedAt = System.currentTimeMillis())
                    if (updateDriveUseCase(updated, driverUid)) {
                        loadDrive()
                    }
                }
            } finally {
                synchronized(locationEnrichmentInFlight) {
                    locationEnrichmentInFlight.remove(drive.id)
                }
            }
        }
    }

    // ── Task 4.1: suggestion actions ───────────────────────────────────────

    private fun fetchSuggestion(drive: DriveEntity) {
        viewModelScope.launch {
            val suggestions = getTripSuggestionsUseCase(
                candidate = drive,
                driverUid = driverUid,
            )
            _topSuggestion.value = suggestions.firstOrNull()
        }
    }

    val applySuggestion: (TripSuggestion) -> Unit = { suggestion ->
        driveId?.let { id ->
            viewModelScope.launch {
                runCatching {
                    val saved = applySuggestionUseCase(
                        tripId = id,
                        suggestion = suggestion,
                        actorUid = driverUid,
                    )
                    if (saved) {
                        _topSuggestion.value = null
                        _calendarHints.value = emptyList()
                        loadDrive()
                        _events.send(TripDetailEvent.SuggestionApplied(suggestion))
                    } else {
                        _events.send(TripDetailEvent.Error("Trip no longer exists."))
                    }
                }.onFailure { error ->
                    _events.send(TripDetailEvent.Error("Failed to apply suggestion: ${error.message}"))
                }
            }
        }
    }

    val dismissSuggestion: () -> Unit = {
        _topSuggestion.value = null
    }

    // ── Task 7.4: calendar hint actions ───────────────────────────────────

    private fun fetchCalendarHints(drive: DriveEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val enabled = settingsRepository.getCalendarHintsEnabled().first()
            if (!enabled) return@launch
            val hints = getCalendarHintsUseCase(
                tripStartMs = drive.startedAt,
                tripEndMs = drive.endedAt ?: drive.startedAt,
            )
            _calendarHints.value = hints
        }
    }

    val applyCalendarHint: (CalendarHint) -> Unit = { hint ->
        driveId?.let { id ->
            viewModelScope.launch {
                runCatching {
                    val saved = classifyTripUseCase(
                        driveId = id,
                        category = TripCategory.BUSINESS,
                        purposeText = hint.suggestedPurpose,
                        actorUid = driverUid,
                    )
                    if (saved) {
                        _calendarHints.value = emptyList()
                        _topSuggestion.value = null
                        loadDrive()
                        _events.send(TripDetailEvent.CalendarHintApplied(hint))
                    } else {
                        _events.send(TripDetailEvent.Error("Trip no longer exists."))
                    }
                }.onFailure { error ->
                    _events.send(TripDetailEvent.Error("Failed to apply calendar hint: ${error.message}"))
                }
            }
        }
    }

    val dismissCalendarHints: () -> Unit = {
        _calendarHints.value = emptyList()
    }

    // ── Task 7.5: exception banner actions ────────────────────────────────

    /**
     * Hides [com.example.mileagetracker.ui.audit.ExceptionFlagBanner] for this screen session without resolving
     * the underlying exception.  The banner will reappear if the screen is
     * recreated (e.g. after configuration change or re-navigation).
     */
    val dismissExceptionBanner: () -> Unit = {
        _bannerDismissed.value = true
    }

    // ── Classification ─────────────────────────────────────────────────────

    val classifyTrip: (TripCategory, String?) -> Unit = { category, purposeText ->
        driveId?.let { id ->
            viewModelScope.launch {
                runCatching {
                    val saved = classifyTripUseCase(
                        driveId = id,
                        category = category,
                        purposeText = purposeText,
                        actorUid = driverUid,
                    )
                    if (saved) {
                        _topSuggestion.value = null
                        _calendarHints.value = emptyList()
                        loadDrive()
                        _events.send(TripDetailEvent.ClassifySuccess(category))
                    } else {
                        _events.send(TripDetailEvent.Error("Trip no longer exists."))
                    }
                }.onFailure { error ->
                    val message = if (error is IllegalArgumentException) {
                        error.message ?: "Validation failed."
                    } else {
                        "Failed to save classification: ${error.message}"
                    }
                    _events.send(TripDetailEvent.Error(message))
                }
            }
        }
    }

    // ── Vehicle assignment ───────────────────────────────────────────────

    val assignVehicle: (Int) -> Unit = { vehicleId ->
        val current = _uiState.value as? TripDetailUiState.Success
        if (current != null) {
            viewModelScope.launch {
                runCatching {
                    val updated = current.drive.copy(
                        vehicleId = vehicleId,
                        updatedAt = System.currentTimeMillis(),
                    )
                    driveRepository.updateDrive(updated)
                    val vehicle = vehicleRepository.getVehicleById(vehicleId)
                    _uiState.update { _ -> current.copy(drive = updated, vehicle = vehicle) }
                    _events.send(TripDetailEvent.VehicleAssigned)
                }.onFailure { error ->
                    _events.send(TripDetailEvent.Error("Failed to assign vehicle: ${error.message}"))
                }
            }
        }
    }

    // ── Endpoints (manual address edit) ──────────────────────────────────────

    /**
     * Manually overwrite the trip's start and/or end location strings.
     *
     * Routed through [UpdateDriveUseCase] (not a bare repository write) so each
     * changed endpoint is captured as a [AuditEventType.LOCATION_UPDATED] audit
     * row — manual edits to a CRA record must be traceable. Blank input is
     * ignored so the user can't accidentally erase an endpoint.
     */
    val updateEndpoints: (String, String) -> Unit = { startLocation, endLocation ->
        val current = _uiState.value as? TripDetailUiState.Success
        val id = driveId
        if (current != null && id != null) {
            val newStart = startLocation.trim()
            val newEnd = endLocation.trim()
            if (newStart.isBlank() || newEnd.isBlank()) {
                viewModelScope.launch {
                    _events.send(TripDetailEvent.Error("Start and end locations can't be blank."))
                }
            } else if (newStart == current.drive.startLocation &&
                newEnd == current.drive.endLocation
            ) {
                // No-op edit; nothing to persist.
            } else {
                viewModelScope.launch {
                    runCatching {
                        val updated = current.drive.copy(
                            startLocation = newStart,
                            endLocation = newEnd,
                            updatedAt = System.currentTimeMillis(),
                        )
                        val saved = updateDriveUseCase(updated, driverUid)
                        if (saved) {
                            _uiState.update { _ -> current.copy(drive = updated) }
                            _events.send(TripDetailEvent.LocationsUpdated)
                        } else {
                            _events.send(TripDetailEvent.Error("Trip no longer exists."))
                        }
                    }.onFailure { error ->
                        _events.send(
                            TripDetailEvent.Error("Failed to update locations: ${error.message}"),
                        )
                    }
                }
            }
        }
    }

    // ── Notes ──────────────────────────────────────────────────────────────

    val updateNotes: (String) -> Unit = { notes ->
        val current = _uiState.value as? TripDetailUiState.Success
        if (current != null) {
            viewModelScope.launch {
                runCatching {
                    val updated = current.drive.copy(
                        notes = notes.trim().ifBlank { null },
                        updatedAt = System.currentTimeMillis(),
                    )
                    driveRepository.updateDrive(updated)
                    _uiState.update { _ -> current.copy(drive = updated) }
                    _events.send(TripDetailEvent.NotesSaved)
                }.onFailure { error ->
                    _events.send(TripDetailEvent.Error("Failed to save notes: ${error.message}"))
                }
            }
        }
    }

    // ── Soft delete ────────────────────────────────────────────────────────

    val softDelete: () -> Unit = {
        val current = _uiState.value as? TripDetailUiState.Success
        if (current != null) {
            viewModelScope.launch {
                runCatching {
                    driveRepository.deleteDrive(current.drive.id)
                    _events.send(TripDetailEvent.Deleted)
                }.onFailure { error ->
                    _events.send(TripDetailEvent.Error("Failed to delete trip: ${error.message}"))
                }
            }
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────

/**
 * Converts a list of [RoutePoint] domain objects into the appropriate
 * [RouteMapUiState].  A minimum of 2 points is required to draw a polyline
 * and compute meaningful bounds; fewer points yield [RouteMapUiState.NoRoute].
 */
internal fun List<RoutePoint>.toRouteMapUiState(): RouteMapUiState {
    if (size < 2) return RouteMapUiState.NoRoute
    val latLngs = map { LatLng(it.lat, it.lng) }
    val bounds = LatLngBounds.Builder()
        .apply { latLngs.forEach { include(it) } }
        .build()
    return RouteMapUiState.Ready(
        points = latLngs,
        bounds = bounds,
        startMarker = latLngs.first(),
        endMarker = latLngs.last(),
        hasLowAccuracy = any { it.accuracyMetres != null && it.accuracyMetres > 50f },
    )
}

// ── UI state ──────────────────────────────────────────────────────────────

sealed interface TripDetailUiState {
    data object Loading : TripDetailUiState
    data object NotFound : TripDetailUiState
    data class Success(
        val drive: DriveEntity,
        val vehicle: VehicleEntity?,
    ) : TripDetailUiState
}

// ── One-shot events ───────────────────────────────────────────────────────

sealed interface TripDetailEvent {
    data class ClassifySuccess(val category: TripCategory) : TripDetailEvent
    data class SuggestionApplied(val suggestion: TripSuggestion) : TripDetailEvent
    data class CalendarHintApplied(val hint: CalendarHint) : TripDetailEvent
    data object VehicleAssigned : TripDetailEvent
    data object NotesSaved : TripDetailEvent
    data object LocationsUpdated : TripDetailEvent
    data object Deleted : TripDetailEvent
    data class Error(val message: String) : TripDetailEvent
}
