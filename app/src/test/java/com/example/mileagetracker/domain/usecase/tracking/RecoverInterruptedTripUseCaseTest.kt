package com.example.mileagetracker.domain.usecase.tracking

import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.entity.ActiveRoutePointEntity
import com.example.mileagetracker.data.local.entity.ActiveTrackingSessionEntity
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecoverInterruptedTripUseCaseTest {

    private lateinit var activeTripStore: ActiveTripStore
    private lateinit var tripBufferStore: TripBufferStore
    private lateinit var useCase: RecoverInterruptedTripUseCase

    @Before
    fun setUp() {
        activeTripStore = mockk(relaxed = true)
        tripBufferStore = mockk(relaxed = true)
        useCase = RecoverInterruptedTripUseCase(activeTripStore, tripBufferStore)
    }

    private fun testPoint(lat: Double, lng: Double, ts: Long) = ActiveRoutePointEntity(
        id = 0,
        sessionId = 1,
        lat = lat,
        lng = lng,
        altitudeMetres = null,
        accuracyMetres = null,
        speedKph = null,
        timestampMs = ts
    )

    @Test
    fun `returns false if no active trip to recover`() = runTest {
        coEvery { activeTripStore.snapshot() } returns null
        val result = useCase()
        assertFalse(result)
    }

    @Test
    fun `recovers trip if distance exceeds threshold`() = runTest {
        val session = ActiveTrackingSessionEntity(
            id = 1,
            ownerUid = "u1",
            startedAt = 1000L,
            detectionSource = "AUTO",
            batteryLevelAtStart = 80,
            batterySaverSeen = false,
            isManual = false
        )
        // 0,0 to 0.1,0.1 is ~15km, well over 0.3km threshold
        val points = listOf(
            testPoint(0.0, 0.0, 1000L),
            testPoint(0.1, 0.1, 2000L)
        )
        coEvery { activeTripStore.snapshot() } returns ActiveTripStore.Snapshot(session, points)

        val result = useCase()

        assertTrue(result)
        coVerify { tripBufferStore.enqueue(match {
            it.driverUid == "u1" &&
            it.classificationStatus == ClassificationStatus.NEEDS_REVIEW &&
            it.distanceKm > 0.3f
        }, any()) }
        coVerify { activeTripStore.clear() }
    }

    @Test
    fun `clears but does not recover if trip is too short`() = runTest {
        val session = mockk<ActiveTrackingSessionEntity>(relaxed = true)
        val points = listOf(testPoint(0.0, 0.0, 1000L), testPoint(0.0001, 0.0001, 2000L))
        coEvery { activeTripStore.snapshot() } returns ActiveTripStore.Snapshot(session, points)

        val result = useCase()

        assertFalse(result)
        coVerify(exactly = 0) { tripBufferStore.enqueue(any(), any()) }
        coVerify { activeTripStore.clear() }
    }
}
