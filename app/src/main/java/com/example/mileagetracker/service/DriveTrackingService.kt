package com.example.mileagetracker.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.entity.ActiveTrackingSessionEntity
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.DetectionSource
import com.example.mileagetracker.domain.model.TrackingState
import com.example.mileagetracker.domain.repository.VehicleRepository
import com.example.mileagetracker.domain.usecase.drive.PersistTrackedDriveUseCase
import com.example.mileagetracker.domain.usecase.tracking.DetectDuplicateTripUseCase
import com.example.mileagetracker.ui.screen.ManualTripControlScreen
import com.example.mileagetracker.ui.viewmodel.ManualTripControlViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that records a drive trip using GPS.
 *
 * Collects [com.example.mileagetracker.domain.model.RoutePoint] values,
 * checkpoints them into [ActiveTripStore], and persists completed trips through
 * [PersistTrackedDriveUseCase] after duplicate detection via
 * [DetectDuplicateTripUseCase].
 *
 * [ACTION_MANUAL_START] and [ACTION_MANUAL_STOP] provide explicit user control.
 * Live state is broadcast through [TrackingStateBus] for
 * [ManualTripControlViewModel], while live GPS position is broadcast through
 * [LiveLocationBus] for [ManualTripControlScreen].
 */
@AndroidEntryPoint
class DriveTrackingService : Service() {

    @Inject lateinit var persistTrackedDriveUseCase: PersistTrackedDriveUseCase

    @Inject lateinit var tripBufferStore: TripBufferStore

    @Inject lateinit var detectDuplicateTripUseCase: DetectDuplicateTripUseCase

    @Inject lateinit var trackingStateBus: TrackingStateBus

    @Inject lateinit var liveLocationBus: LiveLocationBus

    @Inject lateinit var currentUserProvider: CurrentUserProvider

    @Inject lateinit var activeTripStore: ActiveTripStore

    @Inject lateinit var vehicleRepository: VehicleRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val routePointsLock = Any()
    private val routePoints = mutableListOf<com.example.mileagetracker.domain.model.RoutePoint>()
    private val checkpointJobsLock = Any()
    private val checkpointJobs = mutableListOf<Job>()

    private var tripStartTime = 0L
    private var detectionSource = DetectionSource.MANUAL
    private var batteryAtStart: Int? = null
    private var batterySaverSeenActive = false
    private var activeOwnerUid: String? = null
    private var isStopping = false
    private var isManualMode = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (isStopping) return
            val fix = result.lastLocation ?: return
            checkBatterySaver()
            val point = fix.toTrackedRoutePoint()
            val currentKm = synchronized(routePointsLock) {
                routePoints.add(point)
                RouteDistanceCalculator.calculateTotalDistanceKm(routePoints)
            }
            val checkpointJob = serviceScope.launch {
                runCatching { activeTripStore.append(point.toActiveEntity()) }
                    .onFailure { error ->
                        Log.w(TAG, "Active point checkpoint failed: ${error.message}")
                    }
            }
            synchronized(checkpointJobsLock) { checkpointJobs.add(checkpointJob) }
            liveLocationBus.emit(LatLng(fix.latitude, fix.longitude))

            if (isManualMode) {
                trackingStateBus.emit(
                    TrackingState.ManualRecording(
                        startTimeMs = tripStartTime,
                        currentKm = currentKm,
                    )
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        ensureDriveTrackingNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleCommand(intent)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        trackingStateBus.emit(TrackingState.Idle)
        liveLocationBus.clear()
        serviceScope.cancel()
    }

    private fun startTracking(source: DetectionSource) {
        val ownerUid = try {
            currentUserProvider.requireUid()
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Tracking start rejected: ${error.message}")
            trackingStateBus.emit(TrackingState.Idle)
            stopSelf()
            return
        }

        tripStartTime = System.currentTimeMillis()
        detectionSource = source
        batteryAtStart = currentBatteryLevel(this)
        batterySaverSeenActive = isPowerSaverActive(this)
        activeOwnerUid = ownerUid
        isStopping = false
        synchronized(routePointsLock) { routePoints.clear() }
        synchronized(checkpointJobsLock) { checkpointJobs.clear() }
        liveLocationBus.clear()

        trackingStateBus.emit(
            if (isManualMode) {
                TrackingState.ManualRecording(startTimeMs = tripStartTime)
            } else {
                TrackingState.AutoRecording(startTimeMs = tripStartTime)
            }
        )

        startForeground(
            NOTIFICATION_ID,
            buildDriveTrackingNotification(this, DriveTrackingService::class.java),
        )

        serviceScope.launch {
            val startResult = runCatching {
                activeTripStore.begin(
                    ActiveTrackingSessionEntity(
                        ownerUid = ownerUid,
                        startedAt = tripStartTime,
                        detectionSource = source.name,
                        batteryLevelAtStart = batteryAtStart,
                        batterySaverSeen = batterySaverSeenActive,
                        isManual = isManualMode,
                    )
                )
            }
            if (startResult.isSuccess) {
                withContext(Dispatchers.Main) { requestLocationUpdates() }
            } else {
                val error = startResult.exceptionOrNull()
                Log.e(TAG, "Unable to start durable tracking: ${error?.message}", error)
                withContext(Dispatchers.Main) { finishService() }
            }
        }
    }

    private fun stopTracking() {
        if (isStopping || trackingStateBus.state.value is TrackingState.Idle) {
            Log.d(TAG, "Stop ignored - no active trip.")
            stopSelf()
            return
        }

        isStopping = true
        val snapshot = synchronized(routePointsLock) {
            val copy = routePoints.toList()
            routePoints.clear()
            copy
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)

        serviceScope.launch {
            synchronized(checkpointJobsLock) { checkpointJobs.toList() }.joinAll()
            val totalDistanceKm = RouteDistanceCalculator.calculateTotalDistanceKm(snapshot)
            if (totalDistanceKm >= MIN_TRIP_DISTANCE_KM) {
                persistTrackedDriveSnapshot(
                    context = this@DriveTrackingService,
                    capture = DriveTrackingCapture(
                        tripStartTime = tripStartTime,
                        detectionSource = detectionSource,
                        batteryAtStart = batteryAtStart,
                        batterySaverSeenActive = batterySaverSeenActive,
                        actorUid = activeOwnerUid ?: currentUserProvider.requireUid(),
                        isManualMode = isManualMode,
                        totalDistanceKm = totalDistanceKm,
                        snapshot = snapshot,
                    ),
                    deps = DriveTrackingPersistenceDeps(
                        tripBufferStore = tripBufferStore,
                        detectDuplicateTripUseCase = detectDuplicateTripUseCase,
                        persistTrackedDriveUseCase = persistTrackedDriveUseCase,
                        vehicleRepository = vehicleRepository,
                    ),
                )
            } else {
                Log.d(TAG, "Trip too short ($totalDistanceKm km) - discarded.")
            }
            activeTripStore.clear()
            withContext(Dispatchers.Main) { finishService() }
        }
    }

    private val finishService: () -> Unit = {
        trackingStateBus.emit(TrackingState.Idle)
        liveLocationBus.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val requestLocationUpdates: () -> Unit = {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS,
        ).setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS).build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        } catch (error: SecurityException) {
            Log.e(TAG, "Location permission missing while starting trip.", error)
            finishService()
        }
    }

    private val handleCommand: (Intent?) -> Unit = { intent ->
        when (resolveTrackingCommand(intent?.action)) {
            TrackingCommand.MANUAL_START -> {
                if (trackingStateBus.state.value !is TrackingState.Idle) {
                    Log.d(TAG, "Manual start ignored - trip already in progress.")
                } else {
                    isManualMode = true
                    startTracking(DetectionSource.MANUAL)
                }
            }

            TrackingCommand.AUTO_START -> {
                if (trackingStateBus.state.value !is TrackingState.Idle) {
                    Log.d(TAG, "Auto start ignored - trip already in progress.")
                } else {
                    logManualSourceOverride(intent)
                    isManualMode = false
                    startTracking(DetectionSource.AUTO)
                }
            }

            TrackingCommand.STOP -> stopTracking()
            TrackingCommand.NONE -> Unit
        }
    }

    private fun resolveTrackingCommand(action: String?): TrackingCommand =
        when (action) {
            ACTION_MANUAL_START -> TrackingCommand.MANUAL_START
            ACTION_AUTO_START, LEGACY_ACTION_START -> TrackingCommand.AUTO_START
            ACTION_STOP, ACTION_AUTO_STOP, ACTION_MANUAL_STOP -> TrackingCommand.STOP
            else -> TrackingCommand.NONE
        }

    private fun logManualSourceOverride(intent: Intent?) {
        val requestedSource = intent?.getStringExtra(EXTRA_DETECTION_SOURCE)
            ?.let { runCatching { DetectionSource.valueOf(it) }.getOrNull() }
            ?: DetectionSource.AUTO
        if (requestedSource == DetectionSource.MANUAL) {
            Log.w(TAG, "Ignoring MANUAL source for auto start action; forcing AUTO.")
        }
    }

    private val checkBatterySaver: () -> Unit = {
        if (!batterySaverSeenActive && isPowerSaverActive(this)) {
            batterySaverSeenActive = true
        }
    }

    companion object {
        @Deprecated("Use ACTION_MANUAL_START or ACTION_AUTO_START with EXTRA_DETECTION_SOURCE")
        const val ACTION_START = "ACTION_START_TRACKING"
        private const val LEGACY_ACTION_START = "ACTION_START_TRACKING"

        const val ACTION_AUTO_START = "ACTION_AUTO_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val ACTION_AUTO_STOP = "ACTION_AUTO_STOP_TRACKING"
        const val ACTION_MANUAL_START = "ACTION_MANUAL_START_TRACKING"
        const val ACTION_MANUAL_STOP = "ACTION_MANUAL_STOP_TRACKING"

        const val EXTRA_DETECTION_SOURCE = "EXTRA_DETECTION_SOURCE"
        const val NOTIFICATION_ID = 1001
        const val DUPLICATE_NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "drive_tracking_channel"

        private const val TAG = "DriveTrackingService"
        private const val MIN_TRIP_DISTANCE_KM = 0.3f
        private const val LOCATION_INTERVAL_MS = 5_000L
        private const val MIN_UPDATE_DISTANCE_METERS = 10f
    }
}

private enum class TrackingCommand {
    MANUAL_START,
    AUTO_START,
    STOP,
    NONE,
}
