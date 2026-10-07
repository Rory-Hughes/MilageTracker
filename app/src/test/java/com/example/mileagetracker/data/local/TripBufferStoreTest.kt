package com.example.mileagetracker.data.local

import com.example.mileagetracker.data.local.dao.BufferedTripDao
import com.example.mileagetracker.data.local.entity.BufferedTripEntity
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.DetectionSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TripBufferStoreTest {

    private lateinit var dao: BufferedTripDao
    private lateinit var store: TripBufferStore

    @Before
    fun setUp() {
        dao = mockk()
        store = TripBufferStore(dao)
    }

    private fun testDrive() = DriveEntity(
        id = 0,
        startedAt = 1000L,
        endedAt = 2000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = 10.5f,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = TripCategory.BUSINESS,
        classificationStatus = ClassificationStatus.AUTO_CLASSIFIED,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = 1000L,
        appliedRateSnapshot = 0.70f
    )

    @Test
    fun `deserialisation recovers all drive fields`() {
        // This test bypasses the DB and tests the serialisation logic directly.
        // We'll manually construct the JSON to verify deserialisation.
        val json = """
            {
                "startedAt": 1000,
                "endedAt": 2000,
                "driverUid": "u1",
                "startLocation": "A",
                "endLocation": "B",
                "distanceKm": 10.5,
                "trackingSource": "AUTO_TRACKED",
                "tripCategory": "BUSINESS",
                "classificationStatus": "AUTO_CLASSIFIED",
                "purposeText": "Meeting",
                "vehicleId": 1,
                "appliedRateSnapshot": 0.70,
                "classificationConfidence": 0.95,
                "notes": "Some notes"
            }
        """.trimIndent()

        val result = store.deserializeDrive(json)

        assertEquals(1000L, result.startedAt)
        assertEquals(2000L, result.endedAt)
        assertEquals("u1", result.driverUid)
        assertEquals("A", result.startLocation)
        assertEquals("Meeting", result.purposeText)
        assertEquals(10.5f, result.distanceKm)
        assertEquals(TripCategory.BUSINESS, result.tripCategory)
    }

    @Test
    fun `deserialisation handles nulls correctly`() {
        val json = """
            {
                "startedAt": 1000,
                "endedAt": -1,
                "driverUid": "",
                "startLocation": "A",
                "endLocation": "B",
                "distanceKm": 5.0,
                "trackingSource": "MANUAL_ENTRY",
                "tripCategory": "UNCLASSIFIED",
                "classificationStatus": "PENDING",
                "appliedRateSnapshot": 0.0
            }
        """.trimIndent()

        val result = store.deserializeDrive(json)

        assertNull(result.endedAt)
        assertNull(result.driverUid)
        assertNull(result.notes)
    }

    @Test
    fun `deserializeRoutePoints stamps tripId`() {
        val json = """
            [
                { "lat": 1.1, "lng": 2.2, "timestampMs": 1000 },
                { "lat": 3.3, "lng": 4.4, "timestampMs": 2000 }
            ]
        """.trimIndent()

        val result = store.deserializeRoutePoints(json, 42)

        assertEquals(2, result.size)
        assertEquals(42L, result[0].tripId)
        assertEquals(1.1, result[0].lat, 0.001)
        assertEquals(42L, result[1].tripId)
    }

    @Test
    fun `enqueue serialises payload and returns inserted id`() = runTest {
        val entitySlot = slot<BufferedTripEntity>()
        coEvery { dao.insert(capture(entitySlot)) } returns 99L
        val routePoints = listOf(
            RoutePointEntity(
                tripId = 0,
                lat = 45.1,
                lng = -63.2,
                altitudeMetres = null,
                accuracyMetres = null,
                speedKph = null,
                timestampMs = 1000L,
            ),
        )

        val result = store.enqueue(testDrive(), routePoints)

        assertEquals(99L, result)
        assertEquals(true, entitySlot.captured.tripJson.contains("\"startedAt\":1000"))
        assertEquals(true, entitySlot.captured.routePointsJson.contains("\"lat\":45.1"))
    }

    @Test
    fun `remove delegates id deletion to dao`() = runTest {
        coEvery { dao.deleteById(7L) } returns Unit

        store.remove(7L)

        coVerify(exactly = 1) { dao.deleteById(7L) }
    }

    @Test
    fun `getPending returns dao values`() = runTest {
        val rows = listOf(
            BufferedTripEntity(id = 1, tripJson = "{}", routePointsJson = "[]"),
            BufferedTripEntity(id = 2, tripJson = "{}", routePointsJson = "[]"),
        )
        coEvery { dao.getAll() } returns rows

        val result = store.getPending()

        assertEquals(rows, result)
    }

    @Test
    fun `update delegates row update to dao`() = runTest {
        val row = BufferedTripEntity(id = 5, tripJson = "{}", routePointsJson = "[]", retryCount = 2)
        coEvery { dao.update(row) } returns Unit

        store.update(row)

        coVerify(exactly = 1) { dao.update(row) }
    }

    @Test
    fun `pruneExhausted uses buffered trip max retry count`() = runTest {
        coEvery { dao.deleteExhausted(BufferedTripEntity.MAX_RETRY_COUNT) } returns Unit

        store.pruneExhausted()

        coVerify(exactly = 1) { dao.deleteExhausted(BufferedTripEntity.MAX_RETRY_COUNT) }
    }

    @Test
    fun `pendingCount delegates to dao count`() = runTest {
        coEvery { dao.count() } returns 3

        val result = store.pendingCount()

        assertEquals(3, result)
    }

    @Test
    fun `deserialisation maps tracking metadata fields when present`() {
        val json = """
            {
                "startedAt": 1000,
                "endLocation": "B",
                "startLocation": "A",
                "distanceKm": 1.0,
                "trackingSource": "AUTO_TRACKED",
                "tripCategory": "BUSINESS",
                "classificationStatus": "PENDING",
                "appliedRateSnapshot": 0.70,
                "meta_detectionSource": "AUTO",
                "meta_confidenceScore": 0.85,
                "meta_batteryLevelAtStart": 92,
                "meta_batteryLevelAtEnd": 88,
                "meta_wasBatterySaverActive": true
            }
        """.trimIndent()

        val result = store.deserializeDrive(json)

        assertEquals(DetectionSource.AUTO, result.trackingMetadata?.detectionSource)
        assertEquals(0.85f, result.trackingMetadata?.confidenceScore)
        assertEquals(92, result.trackingMetadata?.batteryLevelAtStart)
        assertEquals(88, result.trackingMetadata?.batteryLevelAtEnd)
        assertEquals(true, result.trackingMetadata?.wasBatterySaverActive)
    }

    @Test
    fun `deserialisation tolerates unknown detection source name`() {
        val json = """
            {
                "startedAt": 1000,
                "endLocation": "B",
                "startLocation": "A",
                "distanceKm": 1.0,
                "trackingSource": "AUTO_TRACKED",
                "tripCategory": "BUSINESS",
                "classificationStatus": "PENDING",
                "appliedRateSnapshot": 0.70,
                "meta_detectionSource": "ALIEN_SOURCE",
                "meta_confidenceScore": 0.90
            }
        """.trimIndent()

        val result = store.deserializeDrive(json)

        assertNull(result.trackingMetadata?.detectionSource)
        assertEquals(0.90f, result.trackingMetadata?.confidenceScore)
    }

    @Test
    fun `deserializeRoutePoints maps optional altitude accuracy and speed values`() {
        val json = """
            [
                {
                    "lat": 1.1,
                    "lng": 2.2,
                    "altitudeMetres": 33.3,
                    "accuracyMetres": 4.4,
                    "speedKph": 55.5,
                    "timestampMs": 1000
                }
            ]
        """.trimIndent()

        val point = store.deserializeRoutePoints(json, 9).single()

        assertEquals(33.3, point.altitudeMetres!!, 0.001)
        assertEquals(4.4f, point.accuracyMetres!!, 0.001f)
        assertEquals(55.5f, point.speedKph!!, 0.001f)
    }

    @Test
    fun `deserializeRoutePoints uses null for missing optional numeric fields`() {
        val json = """
            [
                { "lat": 1.1, "lng": 2.2, "timestampMs": 1000 }
            ]
        """.trimIndent()

        val point = store.deserializeRoutePoints(json, 10).single()

        assertNull(point.altitudeMetres)
        assertNull(point.accuracyMetres)
        assertNull(point.speedKph)
    }
}
