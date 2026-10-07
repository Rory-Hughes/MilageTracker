package com.example.mileagetracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.RoutePoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository integration tests for [TrackingRepositoryImpl] against a real
 * (in-memory) SQLite database.
 *
 * These tests verify:
 *
 * - **Batch insert**: [saveRoutePoints] with a list of [RoutePoint]s inserts
 *   all items in a single DAO call.
 * - **Domain mapping**: all [RoutePoint] fields, including the three nullable
 *   sensor fields ([altitudeMetres], [accuracyMetres], [speedKph]), survive
 *   the entity ↔ domain round-trip.
 * - **Scoping**: [getRoutePoints] returns only points for the requested
 *   `tripId`; points for other trips are not included.
 * - **Empty list**: calling [saveRoutePoints] with an empty list inserts nothing.
 *
 * `route_points.trip_id` is a hard FK to `drives.id`, so a parent drive is
 * inserted in [setUp] via the raw DAO.  [TrackingRepositoryImpl] only holds
 * a [RoutePointDao], so parent drive management is done directly on the
 * database handle in the tests.
 */
@RunWith(AndroidJUnit4::class)
class TrackingRepositoryImplTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: TrackingRepositoryImpl
    private var tripId: Long = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = TrackingRepositoryImpl(db.routePointDao())

        // route_points.trip_id is a hard FK — insert a parent drive first.
        tripId = db.driveDao().insertDrive(
            DriveEntity(
                startedAt            = 1_000L,
                startLocation        = "Home",
                endLocation          = "Office",
                distanceKm           = 12f,
                driverUid            = "u1",
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.PENDING,
                trackingSource       = TrackingSource.AUTO_TRACKED,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun point(
        timestampMs: Long,
        lat: Double = 43.65,
        lng: Double = -79.38,
        altitudeMetres: Double? = null,
        accuracyMetres: Float? = null,
        speedKph: Float? = null,
    ) = RoutePoint(
        tripId         = tripId,
        lat            = lat,
        lng            = lng,
        altitudeMetres = altitudeMetres,
        accuracyMetres = accuracyMetres,
        speedKph       = speedKph,
        timestampMs    = timestampMs,
    )

    // ── saveRoutePoints + getRoutePoints ──────────────────────────────────────

    @Test
    fun saveRoutePoints_insertsAllPoints_getRoutePointsEmitsThem() = runTest {
        repository.saveRoutePoints(
            tripId,
            listOf(
                point(timestampMs = 1_000L),
                point(timestampMs = 2_000L),
                point(timestampMs = 3_000L),
            ),
        )

        val loaded = repository.getRoutePoints(tripId).first()
        assertEquals(3, loaded.size)
        assertTrue(loaded.all { it.tripId == tripId })
    }

    @Test
    fun saveRoutePoints_allDomainFields_roundTripCorrectly() = runTest {
        repository.saveRoutePoints(
            tripId,
            listOf(
                point(
                    timestampMs    = 5_000L,
                    lat            = 43.70,
                    lng            = -79.50,
                    altitudeMetres = 120.5,
                    accuracyMetres = 3.5f,
                    speedKph       = 60.0f,
                ),
            ),
        )

        val loaded = repository.getRoutePoints(tripId).first().single()
        assertEquals(tripId, loaded.tripId)
        assertEquals(43.70, loaded.lat, 0.0001)
        assertEquals(-79.50, loaded.lng, 0.0001)
        assertEquals(120.5, loaded.altitudeMetres!!, 0.01)
        assertEquals(3.5f, loaded.accuracyMetres!!)
        assertEquals(60.0f, loaded.speedKph!!)
        assertEquals(5_000L, loaded.timestampMs)
    }

    // ── nullable sensor fields round-trip as null ─────────────────────────────

    @Test
    fun saveRoutePoints_nullableSensorFields_roundTripAsNull() = runTest {
        repository.saveRoutePoints(
            tripId,
            listOf(point(timestampMs = 1_000L, altitudeMetres = null, accuracyMetres = null, speedKph = null)),
        )

        val loaded = repository.getRoutePoints(tripId).first().single()
        assertNull(loaded.altitudeMetres)
        assertNull(loaded.accuracyMetres)
        assertNull(loaded.speedKph)
    }

    // ── getRoutePoints: scoped to tripId ──────────────────────────────────────

    @Test
    fun getRoutePoints_emitsEmpty_forTripWithNoPoints() = runTest {
        assertTrue(repository.getRoutePoints(tripId).first().isEmpty())
    }

    @Test
    fun getRoutePoints_scopedToTripId_doesNotIncludeOtherTripsPoints() = runTest {
        val otherTripId = db.driveDao().insertDrive(
            DriveEntity(
                startedAt            = 2_000L,
                startLocation        = "C",
                endLocation          = "D",
                distanceKm           = 8f,
                driverUid            = "u1",
                tripCategory         = TripCategory.PERSONAL,
                classificationStatus = ClassificationStatus.PENDING,
                trackingSource       = TrackingSource.AUTO_TRACKED,
            ),
        )

        repository.saveRoutePoints(tripId, listOf(point(timestampMs = 1_000L)))
        repository.saveRoutePoints(
            otherTripId,
            listOf(
                RoutePoint(
                    tripId         = otherTripId,
                    lat            = 45.0,
                    lng            = -75.0,
                    altitudeMetres = null,
                    accuracyMetres = null,
                    speedKph       = null,
                    timestampMs    = 1_500L,
                ),
            ),
        )

        val loaded = repository.getRoutePoints(tripId).first()
        assertEquals(1, loaded.size)
        assertEquals(tripId, loaded.single().tripId)
    }

    // ── empty list ────────────────────────────────────────────────────────────

    @Test
    fun saveRoutePoints_emptyList_insertsNothing() = runTest {
        repository.saveRoutePoints(tripId, emptyList())

        assertTrue(repository.getRoutePoints(tripId).first().isEmpty())
    }
}
