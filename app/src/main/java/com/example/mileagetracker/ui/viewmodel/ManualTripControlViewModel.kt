package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.example.mileagetracker.domain.model.TrackingState
import com.example.mileagetracker.service.DriveTrackingService
import com.example.mileagetracker.service.LiveLocationBus
import com.example.mileagetracker.service.TrackingCommandDispatcher
import com.example.mileagetracker.service.TrackingStateBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [ManualTripControlScreen].
 *
 * ## Responsibilities
 * - Exposes [trackingState] as a read-only [StateFlow] sourced from
 *   [TrackingStateBus] (written by [DriveTrackingService]).
 * - Exposes [liveLocation] as a read-only [StateFlow] sourced from
 *   [LiveLocationBus] (written by [DriveTrackingService] on every GPS fix)
 *   so the UI can display the user's current position on a Google Map.
 * - Provides [startManualTrip] / [stopManualTrip] which send the appropriate
 *   intent to [DriveTrackingService] without the UI needing to know any
 *   service internals.
 *
 * ## Design notes
 * The ViewModel does not bind to the service.  Instead it reads
 * [TrackingStateBus] and [LiveLocationBus], both Hilt singletons updated by
 * the service in-process.  This avoids [ServiceConnection] callbacks and
 * survives configuration changes naturally.
 *
 * [viewModelScope] is used only to drive reactive effects on [trackingState]
 * changes; the actual coroutine work (GPS, persist) happens inside the service.
 */
@HiltViewModel
class ManualTripControlViewModel @Inject constructor(
    private val trackingCommandDispatcher: TrackingCommandDispatcher,
    private val trackingStateBus: TrackingStateBus,
    private val liveLocationBus: LiveLocationBus,
) : ViewModel() {

    /**
     * Live tracking state.  Observed by [ManualTripControlScreen] to drive
     * the Start/Stop button, elapsed-time display, and live km counter.
     */
    val trackingState: StateFlow<TrackingState> = trackingStateBus.state

    /**
     * Latest GPS fix during recording, or null between trips / before first fix.
     * Observed by [ManualTripControlScreen] to update the live map position.
     */
    val liveLocation: StateFlow<LatLng?> = liveLocationBus.location

    /**
     * Sends [DriveTrackingService.ACTION_MANUAL_START] to begin recording.
     *
     * Safe to call while already in [TrackingState.ManualRecording] — the
     * service ignores duplicate starts.
     */
    fun startManualTrip() {
        trackingCommandDispatcher.startManual()
    }

    /**
     * Sends [DriveTrackingService.ACTION_MANUAL_STOP] to end recording.
     *
     * Safe to call while in [TrackingState.Idle] — the service no-ops.
     */
    fun stopManualTrip() {
        trackingCommandDispatcher.stopManual()
    }

    // Reserved for future pause support.
    // fun pauseTrip() { ... }
    // fun resumeTrip() { ... }
}
