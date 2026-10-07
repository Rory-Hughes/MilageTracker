package com.example.mileagetracker.service

import androidx.test.core.app.ApplicationProvider
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.DetectionSource
import com.example.mileagetracker.domain.model.DuplicateCheckResult
import com.example.mileagetracker.domain.model.RoutePoint
import com.example.mileagetracker.domain.repository.VehicleRepository
import com.example.mileagetracker.domain.usecase.drive.PersistTrackedDriveUseCase
import com.example.mileagetracker.domain.usecase.tracking.DetectDuplicateTripUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DriveTrackingPersistenceSupportTest {

    private lateinit var tripBufferStore: TripBufferStore
    private lateinit var detectDuplicateTripUseCase: DetectDuplicateTripUseCase
    private lateinit var persistTrackedDriveUseCase: PersistTrackedDriveUseCase
    private lateinit var vehicleRepository: VehicleRepository

    @Before
    fun setUp() {
        tripBufferStore = mockk(relaxed = true)
        detectDuplicateTripUseCase = mockk()
        persistTrackedDriveUseCase = mockk(relaxed = true)
        vehicleRepository = mockk()

        coEvery { tripBufferStore.enqueue(any(), any()) } returns BUFFER_ID
        coEvery { detectDuplicateTripUseCase(any()) } returns DuplicateCheckResult.Clear
    }

    @Test
    fun `auto-tracked drive is stamped with the owner default vehicle id`() = runTest {
        val defaultVehicle = vehicle(id = 7)
        coEvery { vehicleRepository.getDefaultVehicle(OWNER_UID, any()) } returns defaultVehicle

        val captured = slot<DriveEntity>()
        coEvery {
            persistTrackedDriveUseCase(capture(captured), any(), any(), any())
        } returns 1

        persistTrackedDriveSnapshot(
            context = ApplicationProvider.getApplicationContext(),
            capture = capture(),
            deps = deps(),
        )

        assertEquals(7, captured.captured.vehicleId)
        coVerify { vehicleRepository.getDefaultVehicle(OWNER_UID, any()) }
    }

    @Test
    fun `drive vehicleId is null when no default vehicle exists`() = runTest {
        coEvery { vehicleRepository.getDefaultVehicle(OWNER_UID, any()) } returns null

        val captured = slot<DriveEntity>()
        coEvery {
            persistTrackedDriveUseCase(capture(captured), any(), any(), any())
        } returns 1

        persistTrackedDriveSnapshot(
            context = ApplicationProvider.getApplicationContext(),
            capture = capture(),
            deps = deps(),
        )

        assertNull(captured.captured.vehicleId)
    }

    @Test
    fun `drive persists with null vehicleId when default lookup throws`() = runTest {
        coEvery {
            vehicleRepository.getDefaultVehicle(OWNER_UID, any())
        } throws IllegalStateException("DB unavailable")

        val captured = slot<DriveEntity>()
        coEvery {
            persistTrackedDriveUseCase(capture(captured), any(), any(), any())
        } returns 1

        persistTrackedDriveSnapshot(
            context = ApplicationProvider.getApplicationContext(),
            capture = capture(),
            deps = deps(),
        )

        assertNull(captured.captured.vehicleId)
    }

    private fun capture() = DriveTrackingCapture(
        tripStartTime = 1_000L,
        detectionSource = DetectionSource.AUTO,
        batteryAtStart = 80,
        batterySaverSeenActive = false,
        actorUid = OWNER_UID,
        isManualMode = false,
        totalDistanceKm = 2.5f,
        snapshot = listOf(
            RoutePoint(
                tripId = 0L,
                lat = 43.65107,
                lng = -79.347015,
                altitudeMetres = null,
                accuracyMetres = null,
                speedKph = null,
                timestampMs = 1_000L,
            ),
            RoutePoint(
                tripId = 0L,
                lat = 43.66107,
                lng = -79.357015,
                altitudeMetres = null,
                accuracyMetres = null,
                speedKph = null,
                timestampMs = 2_000L,
            ),
        ),
    )

    private fun deps() = DriveTrackingPersistenceDeps(
        tripBufferStore = tripBufferStore,
        detectDuplicateTripUseCase = detectDuplicateTripUseCase,
        persistTrackedDriveUseCase = persistTrackedDriveUseCase,
        vehicleRepository = vehicleRepository,
    )

    private fun vehicle(id: Int) = VehicleEntity(
        id = id,
        ownerUid = OWNER_UID,
        displayName = "Test Car $id",
        fiscalYear = 2026,
        isDefault = true,
        isActive = true,
    )

    private companion object {
        const val OWNER_UID = "owner-uid-1"
        const val BUFFER_ID = 42L
    }
}
