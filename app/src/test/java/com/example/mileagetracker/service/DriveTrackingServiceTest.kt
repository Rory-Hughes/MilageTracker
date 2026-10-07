package com.example.mileagetracker.service

import android.app.Service
import android.content.Intent
import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.auth.SignedInUserRequiredException
import com.example.mileagetracker.domain.model.TrackingState
import com.example.mileagetracker.domain.repository.VehicleRepository
import com.example.mileagetracker.domain.usecase.drive.PersistTrackedDriveUseCase
import com.example.mileagetracker.domain.usecase.tracking.DetectDuplicateTripUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveTrackingServiceTest {

    private lateinit var service: DriveTrackingService
    private lateinit var trackingStateBus: TrackingStateBus
    private lateinit var currentUserProvider: CurrentUserProvider

    @Before
    fun setUp() {
        service = Robolectric.buildService(DriveTrackingService::class.java).get()
        trackingStateBus = TrackingStateBus()
        currentUserProvider = mockk()

        inject("persistTrackedDriveUseCase", mockk<PersistTrackedDriveUseCase>(relaxed = true))
        inject("tripBufferStore", mockk<TripBufferStore>(relaxed = true))
        inject("detectDuplicateTripUseCase", mockk<DetectDuplicateTripUseCase>(relaxed = true))
        inject("trackingStateBus", trackingStateBus)
        inject("liveLocationBus", LiveLocationBus())
        inject("currentUserProvider", currentUserProvider)
        inject("activeTripStore", mockk<ActiveTripStore>(relaxed = true))
        inject("vehicleRepository", mockk<VehicleRepository>(relaxed = true))
    }

    @Test
    fun `manual start is ignored when state is already active`() {
        trackingStateBus.emit(TrackingState.ManualRecording(startTimeMs = 1_000L))

        val result = service.onStartCommand(
            Intent().apply { action = DriveTrackingService.ACTION_MANUAL_START },
            0,
            0,
        )

        assertEquals(Service.START_STICKY, result)
        verify(exactly = 0) { currentUserProvider.requireUid() }
    }

    @Test
    fun `auto start is ignored when state is already active`() {
        trackingStateBus.emit(TrackingState.AutoRecording(startTimeMs = 2_000L))

        val result = service.onStartCommand(
            Intent().apply { action = DriveTrackingService.ACTION_AUTO_START },
            0,
            0,
        )

        assertEquals(Service.START_STICKY, result)
        verify(exactly = 0) { currentUserProvider.requireUid() }
    }

    @Test
    fun `manual start without authenticated user keeps service idle`() {
        every { currentUserProvider.requireUid() } throws SignedInUserRequiredException()

        val result = service.onStartCommand(
            Intent().apply { action = DriveTrackingService.ACTION_MANUAL_START },
            0,
            0,
        )

        assertEquals(Service.START_STICKY, result)
        assertEquals(TrackingState.Idle, trackingStateBus.state.value)
    }

    @Test
    fun `stop action while idle is safely ignored`() {
        val result = service.onStartCommand(
            Intent().apply { action = DriveTrackingService.ACTION_STOP },
            0,
            0,
        )

        assertEquals(Service.START_STICKY, result)
        assertEquals(TrackingState.Idle, trackingStateBus.state.value)
        verify(exactly = 0) { currentUserProvider.requireUid() }
    }

    private fun inject(fieldName: String, value: Any) {
        val field = DriveTrackingService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(service, value)
    }
}
