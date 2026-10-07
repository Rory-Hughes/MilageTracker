package com.example.mileagetracker.domain.model

/**
 * Represents the current trip-recording lifecycle as observed by
 * [ManualTripControlViewModel] and the persistent foreground notification.
 *
 * ## State machine
 * ```
 *   Idle
 *     ├── startManualTrip()  ────────────────────────────► ManualRecording
 *     └── auto-detection trigger (future)  ───────────► AutoRecording
 *
 *   ManualRecording
 *     ├── stopManualTrip() / ACTION_MANUAL_STOP  ───► Idle  (trip saved)
 *     └── pauseTrip()  ────────────────────────► Paused
 *
 *   AutoRecording
 *     └── auto-detection end (future)  ────────────► Idle
 *
 *   Paused
 *     ├── resumeTrip()  ────────────────────────► ManualRecording
 *     └── stopManualTrip()  ────────────────────► Idle  (trip saved)
 * ```
 *
 * [ManualRecording] and [AutoRecording] both carry [startTimeMs] so the
 * elapsed-time display in [ManualTripControlScreen] can be computed without
 * querying the service.
 */
sealed class TrackingState {

    /** No trip is being recorded.  The Start button is shown. */
    data object Idle : TrackingState()

    /**
     * The user explicitly started a trip via the UI or notification.
     *
     * @param startTimeMs  Epoch millis when the trip was started.
     * @param currentKm    Live odometer reading updated by the service via
     *                     the shared [TrackingStateBus].
     */
    data class ManualRecording(
        val startTimeMs: Long,
        val currentKm: Float = 0f,
    ) : TrackingState()

    /**
     * A trip was started by the auto-detection subsystem (ActivityRecognition
     * or geofence trigger).  The manual UI shows a read-only in-progress
     * indicator rather than a Start button.
     *
     * @param startTimeMs Epoch millis when the auto trip was detected.
     */
    data class AutoRecording(
        val startTimeMs: Long,
    ) : TrackingState()

    /**
     * The user has paused a manual recording.  GPS updates are suspended;
     * the elapsed timer is frozen.  [pausedAtMs] is used to offset
     * [ManualRecording.startTimeMs] on resume so total elapsed time is
     * accurate.
     *
     * @param startTimeMs Original start time (unchanged on pause).
     * @param pausedAtMs  Epoch millis when the trip was paused.
     * @param kmAtPause   Odometer reading frozen at the moment of pause.
     */
    data class Paused(
        val startTimeMs: Long,
        val pausedAtMs: Long,
        val kmAtPause: Float,
    ) : TrackingState()
}
