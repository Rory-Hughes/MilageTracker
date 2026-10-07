package com.example.mileagetracker.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.domain.model.OdometerCapture
import com.example.mileagetracker.domain.model.OdometerBoundaryType
import com.example.mileagetracker.domain.model.OdometerCaptureType
import com.example.mileagetracker.domain.repository.OdometerRepository
import com.example.mileagetracker.domain.usecase.odometer.OdometerSaveResult
import com.example.mileagetracker.domain.usecase.odometer.SaveOdometerCaptureUseCase
import com.example.mileagetracker.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel for [com.example.mileagetracker.ui.screen.OdometerCaptureScreen].
 *
 * ## Responsibilities
 * - Creates a camera-temp [Uri] via [FileProvider] so `TakePicture` has a
 *   write target before the camera launches.
 * - Receives the boolean result from `TakePicture` and promotes
 *   [OdometerFormState.captureType] to [OdometerCaptureType.PHOTO_ASSISTED]
 *   if the photo was taken.
 * - Drives the form fields: km text, capture date, notes.
 * - Calls [SaveOdometerCaptureUseCase] and routes the result to a one-shot
 *   [OdometerCaptureEvent] channel.
 * - Exposes [historyFlow] as a reactive list of past captures for the
 *   selected vehicle.
 *
 * ## Vehicle ID
 * Sourced from [SavedStateHandle] using [Screen.ARG_VEHICLE_ID] so the screen
 * can be navigated to from any vehicle card without additional wiring.
 */
@HiltViewModel
class OdometerCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val saveCapture: SaveOdometerCaptureUseCase,
    private val repo: OdometerRepository,
) : ViewModel() {

    val vehicleId: Int = checkNotNull(savedStateHandle[Screen.ARG_VEHICLE_ID]) {
        "OdometerCaptureViewModel requires '${Screen.ARG_VEHICLE_ID}' in SavedStateHandle"
    }

    // ── Form state ────────────────────────────────────────────────────

    private val _formState = MutableStateFlow(OdometerFormState())
    val formState: StateFlow<OdometerFormState> = _formState.asStateFlow()

    /** Temp URI created for the camera intent.  Held here so TakePicture
     *  result handler can reference it to pass to the domain model. */
    private var pendingCameraUri: Uri? = null

    // ── History ────────────────────────────────────────────────────────

    val historyFlow = repo.observeCapturesForVehicle(vehicleId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    // ── Events ─────────────────────────────────────────────────────────

    private val _events = Channel<OdometerCaptureEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Camera URI ────────────────────────────────────────────────────

    /**
     * Creates a temp file in the app's cache dir and returns a [FileProvider]
     * URI for it.  The camera will write the photo to this URI.
     *
     * Must be called on the main thread (UI action); safe to call before
     * launching `TakePicture`.
     */
    fun createCameraUri(): Uri {
        val photoDir = File(context.cacheDir, "odometer_temp").also { it.mkdirs() }
        val file = File(photoDir, "odo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
        pendingCameraUri = uri
        return uri
    }

    /**
     * Called with the result of `ActivityResultContracts.TakePicture`.
     *
     * @param success `true` if the user confirmed the photo; `false` if they
     *                cancelled or the camera returned an error.
     */
    fun onPhotoResult(success: Boolean) {
        if (success && pendingCameraUri != null) {
            _formState.value = _formState.value.copy(
                photoUri = pendingCameraUri.toString(),
                captureType = OdometerCaptureType.PHOTO_ASSISTED,
            )
        } else {
            pendingCameraUri = null
        }
    }

    // ── Form field updates ──────────────────────────────────────────────

    fun updateReadingText(text: String) {
        _formState.value = _formState.value.copy(readingKmText = text)
    }

    fun updateCaptureDate(epochMs: Long) {
        _formState.value = _formState.value.copy(
            captureDateMs = epochMs,
            taxYear = calendarYear(epochMs),
        )
    }

    fun updateNotes(text: String) {
        _formState.value = _formState.value.copy(notes = text)
    }

    fun updateBoundaryType(type: OdometerBoundaryType) {
        _formState.value = _formState.value.copy(boundaryType = type)
    }

    fun clearPhoto() {
        pendingCameraUri = null
        _formState.value = _formState.value.copy(
            photoUri = null,
            captureType = OdometerCaptureType.MANUAL,
        )
    }

    // ── Save ───────────────────────────────────────────────────────────────

    fun save() {
        val form = _formState.value
        val km = form.readingKmText.trim().toIntOrNull()

        if (km == null) {
            _formState.value = form.copy(kmError = "Enter a valid whole number.")
            return
        }

        _formState.value = form.copy(kmError = null, isSaving = true)

        viewModelScope.launch {
            val capture = OdometerCapture(
                vehicleId = vehicleId,
                captureDate = form.captureDateMs,
                readingKm = km,
                taxYear = form.taxYear,
                photoUri = form.photoUri,
                captureType = form.captureType,
                boundaryType = form.boundaryType,
                notes = form.notes.takeIf { it.isNotBlank() },
            )

            when (val result = saveCapture(capture)) {
                is OdometerSaveResult.Success -> {
                    _events.send(OdometerCaptureEvent.SaveSuccess)
                    resetForm()
                }
                is OdometerSaveResult.RegressionWarning -> {
                    _events.send(
                        OdometerCaptureEvent.RegressionWarning(
                            savedKm = result.saved.readingKm,
                            previousKm = result.previous,
                        )
                    )
                    resetForm()
                }
                is OdometerSaveResult.InvalidReading -> {
                    _formState.value = _formState.value.copy(
                        kmError = result.reason,
                        isSaving = false,
                    )
                }
            }
        }
    }

    private fun resetForm() {
        pendingCameraUri = null
        _formState.value = OdometerFormState()
    }
}

// ── Form state ─────────────────────────────────────────────────────────────────

data class OdometerFormState(
    val readingKmText: String = "",
    val captureDateMs: Long = System.currentTimeMillis(),
    val taxYear: Int = calendarYear(captureDateMs),
    val photoUri: String? = null,
    val captureType: OdometerCaptureType = OdometerCaptureType.MANUAL,
    val boundaryType: OdometerBoundaryType = defaultBoundaryType(captureDateMs),
    val notes: String = "",
    val kmError: String? = null,
    val isSaving: Boolean = false,
) {
    val isSaveEnabled: Boolean
        get() = readingKmText.trim().toIntOrNull()?.let { it > 0 } == true && !isSaving
}

// ── Events ───────────────────────────────────────────────────────────────────

sealed interface OdometerCaptureEvent {
    data object SaveSuccess : OdometerCaptureEvent

    data class RegressionWarning(
        val savedKm: Int,
        val previousKm: Int,
    ) : OdometerCaptureEvent

    data class Error(val message: String) : OdometerCaptureEvent
}

private fun defaultBoundaryType(epochMs: Long): OdometerBoundaryType {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = epochMs
    return if (calendar.get(Calendar.MONTH) == Calendar.JANUARY) {
        OdometerBoundaryType.OPENING
    } else {
        OdometerBoundaryType.OTHER
    }
}

private fun calendarYear(epochMs: Long): Int {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = epochMs
    return calendar.get(Calendar.YEAR)
}
