package com.example.mileagetracker.service

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton bus that broadcasts the latest GPS fix during an active trip.
 *
 * ## Contract
 * - Only [DriveTrackingService] writes via [emit].
 * - [ManualTripControlViewModel] (and any other UI observer) reads [location]
 *   as a read-only [StateFlow].
 * - [clear] is called by the service when tracking stops so the map resets
 *   to null ("Acquiring GPS…" placeholder) on the next recording session.
 */
@Singleton
class LiveLocationBus @Inject constructor() {

    private val _location = MutableStateFlow<LatLng?>(null)

    /** Read-only stream of the latest GPS fix, or null when no trip is active. */
    val location: StateFlow<LatLng?> = _location.asStateFlow()

    /** Called by [DriveTrackingService] on every [LocationCallback] result. */
    internal fun emit(latLng: LatLng) {
        _location.value = latLng
    }

    /** Resets location to null when tracking stops. */
    internal fun clear() {
        _location.value = null
    }
}
