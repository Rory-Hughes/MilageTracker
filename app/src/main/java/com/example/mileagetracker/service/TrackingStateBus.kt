package com.example.mileagetracker.service

import com.example.mileagetracker.domain.model.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton state bus that bridges [DriveTrackingService] (which runs in the
 * same process as the UI) with [ManualTripControlViewModel].
 *
 * ## Why a shared singleton rather than a bound service?
 * A bound service requires a [ServiceConnection] callback, which is
 * asynchronous and adds boilerplate to every consumer.  Because
 * [DriveTrackingService] is a foreground service in the **same process**,
 * a Hilt singleton [StateFlow] is the simplest correct approach:
 * - No IPC serialisation.
 * - No lifecycle coupling between ViewModel and Service binding.
 * - Survives configuration changes naturally (singleton scope).
 * - Easily unit-testable by injecting a fake.
 *
 * ## Contract
 * Only [DriveTrackingService] writes to [_state].
 * [ManualTripControlViewModel] (and any other observer) only reads
 * [state] as a read-only [StateFlow].
 */
@Singleton
class TrackingStateBus @Inject constructor() {

    private val _state = MutableStateFlow<com.example.mileagetracker.domain.model.TrackingState>(
        com.example.mileagetracker.domain.model.TrackingState.Idle
    )

    /** Read-only view of the current tracking state.  Observed by the ViewModel. */
    val state: StateFlow<com.example.mileagetracker.domain.model.TrackingState> = _state.asStateFlow()

    /**
     * Called exclusively by [DriveTrackingService] to push a new state.
     * Must not be called from any other class.
     */
    internal fun emit(newState: com.example.mileagetracker.domain.model.TrackingState) {
        _state.value = newState
    }
}
