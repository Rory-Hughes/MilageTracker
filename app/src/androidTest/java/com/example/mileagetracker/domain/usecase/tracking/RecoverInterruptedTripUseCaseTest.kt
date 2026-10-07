package com.example.mileagetracker.domain.usecase.tracking

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.ActiveTripStore
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.TripBufferStore
import com.example.mileagetracker.data.local.entity.ActiveRoutePointEntity
import com.example.mileagetracker.data.local.entity.ActiveTrackingSessionEntity
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.DetectionSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoverInterruptedTripUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var activeTripStore: ActiveTripStore
    private lateinit var tripBufferStore: TripBufferStore
    private lateinit var useCase: RecoverInterruptedTripUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        activeTripStore = ActiveTripStore(db, db.activeTrackingDao())
        tripBufferStore = TripBufferStore(db.bufferedTripDao())
        useCase = RecoverInterruptedTripUseCase(activeTripStore, tripBufferStore)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun invoke_enqueuesRecoveredTrip_andClearsActiveSession() = runTest {
        activeTripStore.begin(session(detectionSource = "UNKNOWN_SOURCE"))
        activeTripStore.append(point(lat = 44.6488, lng = -63.5752, timestampMs = 1_000L))
        activeTripStore.append(point(lat = 44.6588, lng = -63.5652, timestampMs = 2_000L))

        val result = useCase()

        assertTrue(result)
        assertNull(activeTripStore.snapshot())
        assertEquals(0, db.activeTrackingDao().activeCount())

        val pending = tripBufferStore.getPending()
        assertEquals(1, pending.size)

        val bufferedDrive = tripBufferStore.deserializeDrive(pending.single().tripJson)
        assertEquals("u1", bufferedDrive.driverUid)
        assertEquals(1_000L, bufferedDrive.startedAt)
        assertEquals(2_000L, bufferedDrive.endedAt)
        assertEquals("44.64880, -63.57520", bufferedDrive.startLocation)
        assertEquals("44.65880, -63.56520", bufferedDrive.endLocation)
        assertEquals(TrackingSource.AUTO_TRACKED, bufferedDrive.trackingSource)
        assertEquals(TripCategory.UNCLASSIFIED, bufferedDrive.tripCategory)
        assertEquals(ClassificationStatus.NEEDS_REVIEW, bufferedDrive.classificationStatus)
        assertTrue(bufferedDrive.distanceKm > 0.3f)
        assertTrue(bufferedDrive.notes!!.contains("Recovered after tracking interruption"))
        assertEquals(
            DetectionSource.AUTO,
            bufferedDrive.trackingMetadata?.detectionSource,
        )
        assertEquals(73, bufferedDrive.trackingMetadata?.batteryLevelAtStart)
        assertTrue(bufferedDrive.trackingMetadata?.wasBatterySaverActive == true)

        val bufferedPoints = tripBufferStore.deserializeRoutePoints(
            routePointsJson = pending.single().routePointsJson,
            tripId = 55,
        )
        assertEquals(2, bufferedPoints.size)
        assertEquals(55L, bufferedPoints.first().tripId)
        assertEquals(44.6488, bufferedPoints.first().lat, 0.0)
        assertEquals(-63.5652, bufferedPoints.last().lng, 0.0)
        assertEquals(2_000L, bufferedPoints.last().timestampMs)
    }

    @Test
    fun invoke_discardsTooShortTrip_andStillClearsActiveSession() = runTest {
        activeTripStore.begin(session())
        activeTripStore.append(point(lat = 44.6488, lng = -63.5752, timestampMs = 1_000L))
        activeTripStore.append(point(lat = 44.64881, lng = -63.57521, timestampMs = 2_000L))

        val result = useCase()

        assertFalse(result)
        assertNull(activeTripStore.snapshot())
        assertTrue(tripBufferStore.getPending().isEmpty())
    }

    private fun session(
        ownerUid: String = "u1",
        startedAt: Long = 1_000L,
        detectionSource: String = "AUTO",
        batteryLevelAtStart: Int? = 73,
        batterySaverSeen: Boolean = true,
        isManual: Boolean = false,
    ) = ActiveTrackingSessionEntity(
        ownerUid = ownerUid,
        startedAt = startedAt,
        detectionSource = detectionSource,
        batteryLevelAtStart = batteryLevelAtStart,
        batterySaverSeen = batterySaverSeen,
        isManual = isManual,
    )

    private fun point(
        lat: Double,
        lng: Double,
        timestampMs: Long,
        altitudeMetres: Double? = null,
        accuracyMetres: Float? = null,
        speedKph: Float? = null,
    ) = ActiveRoutePointEntity(
        lat = lat,
        lng = lng,
        altitudeMetres = altitudeMetres,
        accuracyMetres = accuracyMetres,
        speedKph = speedKph,
        timestampMs = timestampMs,
    )
}
