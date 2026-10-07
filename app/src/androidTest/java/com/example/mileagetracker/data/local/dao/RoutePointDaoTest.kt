package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [RoutePointDao] against a real (in-memory) SQLite
 * database. These exercise the actual SQL and schema behaviour — chronological
 * ordering, trip-id scoping, the `INSERT OR IGNORE` conflict strategy, explicit
 * deletion, and the `ON DELETE CASCADE` foreign key to `drives`.
 *
 * ## Foreign-key setup
 * `route_points.trip_id` is a hard FK to `drives.id`, and Room enables
 * `PRAGMA foreign_keys = ON` by default, so a point cannot be inserted without
 * an existing parent drive. [setUp] seeds parent rows for [TRIP_A] and [TRIP_B].
 */
@RunWith(AndroidJUnit4::class)
class RoutePointDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: RoutePointDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.routePointDao()

        // FK parents — points can't exist without their drive.
        runBlocking {
            insertParentDrive(TRIP_A)
            insertParentDrive(TRIP_B)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    private suspend fun insertParentDrive(tripId: Long) {
        db.driveDao().insertDrive(
            DriveEntity(
                id = tripId.toInt(),
                startedAt = 1_700_000_000_000L,
                startLocation = "Start",
                endLocation = "End",
                distanceKm = 0f,
                driverUid = "u1",
                tripCategory = TripCategory.UNCLASSIFIED,
                classificationStatus = ClassificationStatus.PENDING,
                trackingSource = TrackingSource.AUTO_TRACKED,
            ),
        )
    }

    private fun point(
        tripId: Long = TRIP_A,
        id: Long = 0,
        lat: Double = 45.0,
        lng: Double = -66.0,
        altitudeMetres: Double? = null,
        accuracyMetres: Float? = null,
        speedKph: Float? = null,
        timestampMs: Long = 1_000L,
    ) = RoutePointEntity(
        id = id,
        tripId = tripId,
        lat = lat,
        lng = lng,
        altitudeMetres = altitudeMetres,
        accuracyMetres = accuracyMetres,
        speedKph = speedKph,
        timestampMs = timestampMs,
    )

    // ── insert + read-back ───────────────────────────────────────────────────

    @Test
    fun insertAll_thenGetOnce_roundTripsAllFields() = runTest {
        dao.insertAll(
            listOf(
                point(
                    timestampMs = 1_000L,
                    lat = 45.5,
                    lng = -66.5,
                    altitudeMetres = 12.3,
                    accuracyMetres = 8f,
                    speedKph = 40f,
                ),
            ),
        )

        val loaded = dao.getPointsForTripOnce(TRIP_A).single()
        assertEquals(45.5, loaded.lat, 0.0)
        assertEquals(-66.5, loaded.lng, 0.0)
        assertEquals(12.3, loaded.altitudeMetres!!, 0.0)
        assertEquals(8f, loaded.accuracyMetres)
        assertEquals(40f, loaded.speedKph)
        assertEquals(1_000L, loaded.timestampMs)
    }

    @Test
    fun nullableFields_persistAsNull() = runTest {
        dao.insertAll(listOf(point(altitudeMetres = null, accuracyMetres = null, speedKph = null)))

        val loaded = dao.getPointsForTripOnce(TRIP_A).single()
        assertNull(loaded.altitudeMetres)
        assertNull(loaded.accuracyMetres)
        assertNull(loaded.speedKph)
    }

    @Test
    fun getPointsForTripOnce_returnsEmpty_forUnknownTrip() = runTest {
        assertTrue(dao.getPointsForTripOnce(999L).isEmpty())
    }

    // ── ordering ─────────────────────────────────────────────────────────────

    @Test
    fun getPointsForTripOnce_ordersByTimestampAscending() = runTest {
        dao.insertAll(
            listOf(
                point(timestampMs = 3_000L, lat = 3.0),
                point(timestampMs = 1_000L, lat = 1.0),
                point(timestampMs = 2_000L, lat = 2.0),
            ),
        )

        val timestamps = dao.getPointsForTripOnce(TRIP_A).map { it.timestampMs }
        assertEquals(listOf(1_000L, 2_000L, 3_000L), timestamps)
    }

    @Test
    fun getPointsForTrip_flowEmitsChronologicalOrder() = runTest {
        dao.insertAll(
            listOf(
                point(timestampMs = 2_000L),
                point(timestampMs = 1_000L),
            ),
        )

        val timestamps = dao.getPointsForTrip(TRIP_A).first().map { it.timestampMs }
        assertEquals(listOf(1_000L, 2_000L), timestamps)
    }

    // ── trip-id scoping ──────────────────────────────────────────────────────

    @Test
    fun getPointsForTrip_returnsOnlyRequestedTrip() = runTest {
        dao.insertAll(
            listOf(
                point(tripId = TRIP_A, timestampMs = 1_000L),
                point(tripId = TRIP_A, timestampMs = 2_000L),
                point(tripId = TRIP_B, timestampMs = 3_000L),
            ),
        )

        val tripA = dao.getPointsForTripOnce(TRIP_A)
        val tripB = dao.getPointsForTripOnce(TRIP_B)
        assertEquals(2, tripA.size)
        assertTrue(tripA.all { it.tripId == TRIP_A })
        assertEquals(1, tripB.size)
        assertEquals(TRIP_B, tripB.single().tripId)
    }

    // ── conflict strategy ────────────────────────────────────────────────────

    @Test
    fun insertAll_ignoresDuplicatePrimaryKey() = runTest {
        dao.insertAll(listOf(point(id = 5L, lat = 1.0)))
        // Same PK on a retry must be skipped (INSERT OR IGNORE), not crash.
        dao.insertAll(listOf(point(id = 5L, lat = 99.0)))

        val all = dao.getPointsForTripOnce(TRIP_A)
        assertEquals(1, all.size)
        assertEquals(1.0, all.single().lat, 0.0) // original row retained, not overwritten
    }

    // ── deletion ─────────────────────────────────────────────────────────────

    @Test
    fun deletePointsForTrip_removesOnlyThatTripsPoints() = runTest {
        dao.insertAll(
            listOf(
                point(tripId = TRIP_A, timestampMs = 1_000L),
                point(tripId = TRIP_B, timestampMs = 2_000L),
            ),
        )

        dao.deletePointsForTrip(TRIP_A)

        assertTrue(dao.getPointsForTripOnce(TRIP_A).isEmpty())
        assertEquals(1, dao.getPointsForTripOnce(TRIP_B).size)
    }

    @Test
    fun deletingParentDrive_cascadeDeletesItsRoutePoints() = runTest {
        dao.insertAll(
            listOf(
                point(tripId = TRIP_A, timestampMs = 1_000L),
                point(tripId = TRIP_B, timestampMs = 2_000L),
            ),
        )

        // Hard-deleting the parent drive must cascade to route_points (ON DELETE CASCADE).
        db.driveDao().hardDeleteDrive(TRIP_A.toInt())

        assertTrue(dao.getPointsForTripOnce(TRIP_A).isEmpty())
        assertEquals(1, dao.getPointsForTripOnce(TRIP_B).size) // sibling trip untouched
    }

    private companion object {
        const val TRIP_A = 1L
        const val TRIP_B = 2L
    }
}
