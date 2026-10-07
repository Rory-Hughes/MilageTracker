package com.example.mileagetracker.ui.map

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.maps.model.LatLng
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.dao.RoutePointDao
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.RoutePointEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.toDomain
import com.example.mileagetracker.ui.viewmodel.RouteMapUiState
import com.example.mileagetracker.ui.viewmodel.toRouteMapUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for the RouteMap pipeline — Phase 7, task 7.2.
 *
 * Verifies that inserting [RoutePointEntity] rows into an in-memory Room
 * database and then reading them back via [RoutePointDao.getPointsForTrip]
 * produces the correct [RouteMapUiState] when processed by
 * [toRouteMapUiState].
 *
 * This test exercises the full data path from DB insert → Flow emission →
 * domain mapping → state derivation without mocking the DAO.
 *
 * ## Database setup
 * An in-memory [MileageDatabase] is created fresh for each test. A parent
 * [DriveEntity] is inserted first (required by the FK constraint on
 * `route_points.trip_id`) using a minimal set of non-null columns.
 *
 * ## State assertions
 * - Zero points → [RouteMapUiState.NoRoute]
 * - One point  → [RouteMapUiState.NoRoute]
 * - Two points  → [RouteMapUiState.Ready] with correct start/end markers
 * - Three points → [RouteMapUiState.Ready] with bounds containing all points
 * - Low-accuracy point (> 50 m) → [RouteMapUiState.Ready] with hasLowAccuracy = true
 * - All high-accuracy points → [RouteMapUiState.Ready] with hasLowAccuracy = false
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RouteMapIntegrationTest {

    private lateinit var db:            MileageDatabase
    private lateinit var routePointDao: RoutePointDao

    /** Fixed trip ID used across all tests. */
    private val tripId = 1L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        routePointDao = db.routePointDao()

        // Insert a minimal parent DriveEntity so the FK constraint is satisfied.
        val parentDrive = DriveEntity(
            id                   = tripId.toInt(),
            startLocation        = "Start",
            endLocation          = "End",
            distanceKm           = 0f,
            dateString           = "2024-01-01",
            timeString           = "08:00",
            tripCategory         = TripCategory.UNCLASSIFIED,
            classificationStatus = ClassificationStatus.PENDING,
            appliedRateSnapshot  = 0f,
            trackingSource       = TrackingSource.AUTO_TRACKED,
            startedAt            = 1_700_000_000_000L,
            createdAt            = 1_700_000_000_000L,
            updatedAt            = 1_700_000_000_000L,
        )
        // Insert synchronously (allowMainThreadQueries is enabled for tests)
        runBlocking {
            db.driveDao().insertDrive(parentDrive)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun makeEntity(
        lat: Double,
        lng: Double,
        index: Int,
        accuracyMetres: Float? = null,
    ) = RoutePointEntity(
        tripId         = tripId,
        lat            = lat,
        lng            = lng,
        altitudeMetres = null,
        accuracyMetres = accuracyMetres,
        speedKph       = null,
        timestampMs    = 1_000_000L + index,
    )

    /** Inserts entities, reads them back via the Flow, converts to state. */
    private suspend fun insertAndDeriveState(
        entities: List<RoutePointEntity>,
    ): RouteMapUiState {
        routePointDao.insertAll(entities)
        val domainPoints = routePointDao.getPointsForTrip(tripId)
            .first()
            .map { it.toDomain() }
        return domainPoints.toRouteMapUiState()
    }

    // ── zero and single-point cases ──────────────────────────────────────

    @Test
    fun zeroPoints_emitsNoRoute() = runTest {
        val state = insertAndDeriveState(emptyList())
        assertEquals(RouteMapUiState.NoRoute, state)
    }

    @Test
    fun singlePoint_emitsNoRoute() = runTest {
        val state = insertAndDeriveState(
            listOf(makeEntity(45.0, -66.0, 0))
        )
        assertEquals(RouteMapUiState.NoRoute, state)
    }

    // ── two-point case ───────────────────────────────────────────────────

    @Test
    fun twoPoints_emitsReady_withCorrectStartAndEndMarkers() = runTest {
        val state = insertAndDeriveState(
            listOf(
                makeEntity(45.0, -66.0, 0),
                makeEntity(46.0, -67.0, 1),
            )
        )

        assertTrue(state is RouteMapUiState.Ready)
        val ready = state as RouteMapUiState.Ready
        assertEquals(LatLng(45.0, -66.0), ready.startMarker)
        assertEquals(LatLng(46.0, -67.0), ready.endMarker)
        assertEquals(2, ready.points.size)
    }

    // ── three-point / bounds case ─────────────────────────────────────────

    @Test
    fun threePoints_emitsReady_boundsContainAllPoints() = runTest {
        val state = insertAndDeriveState(
            listOf(
                makeEntity(45.0, -66.0, 0),
                makeEntity(45.5, -66.5, 1),
                makeEntity(46.0, -67.0, 2),
            )
        )

        assertTrue(state is RouteMapUiState.Ready)
        val ready = state as RouteMapUiState.Ready
        assertTrue(ready.bounds.contains(LatLng(45.0, -66.0)))
        assertTrue(ready.bounds.contains(LatLng(45.5, -66.5)))
        assertTrue(ready.bounds.contains(LatLng(46.0, -67.0)))
    }

    // ── ordering ─────────────────────────────────────────────────────────

    /**
     * Rows are inserted out-of-order (by timestamp) to confirm the DAO
     * ORDER BY timestamp_ms ASC clause is honoured and the derived state
     * reflects the correct first/last point.
     */
    @Test
    fun pointsInsertedOutOfOrder_readyState_isChronologicallyOrdered() = runTest {
        // Insert in reverse chronological order
        val state = insertAndDeriveState(
            listOf(
                makeEntity(46.0, -67.0, index = 2),  // latest
                makeEntity(45.5, -66.5, index = 1),  // middle
                makeEntity(45.0, -66.0, index = 0),  // earliest
            )
        )

        assertTrue(state is RouteMapUiState.Ready)
        val ready = state as RouteMapUiState.Ready
        // After DAO ordering, first point should be index=0 (45.0, -66.0)
        assertEquals(LatLng(45.0, -66.0), ready.startMarker)
        assertEquals(LatLng(46.0, -67.0), ready.endMarker)
    }

    // ── accuracy flag ─────────────────────────────────────────────────────

    @Test
    fun pointWithAccuracyOver50m_setsHasLowAccuracyTrue() = runTest {
        val state = insertAndDeriveState(
            listOf(
                makeEntity(45.0, -66.0, 0, accuracyMetres = 10f),
                makeEntity(46.0, -67.0, 1, accuracyMetres = 75f),  // > 50 m
            )
        )

        assertTrue(state is RouteMapUiState.Ready)
        assertTrue((state as RouteMapUiState.Ready).hasLowAccuracy)
    }

    @Test
    fun allPointsUnder50mAccuracy_hasLowAccuracyIsFalse() = runTest {
        val state = insertAndDeriveState(
            listOf(
                makeEntity(45.0, -66.0, 0, accuracyMetres = 10f),
                makeEntity(46.0, -67.0, 1, accuracyMetres = 30f),
            )
        )

        assertTrue(state is RouteMapUiState.Ready)
        assertFalse((state as RouteMapUiState.Ready).hasLowAccuracy)
    }

    @Test
    fun nullAccuracy_doesNotTriggerLowAccuracyFlag() = runTest {
        val state = insertAndDeriveState(
            listOf(
                makeEntity(45.0, -66.0, 0, accuracyMetres = null),
                makeEntity(46.0, -67.0, 1, accuracyMetres = null),
            )
        )

        assertTrue(state is RouteMapUiState.Ready)
        assertFalse((state as RouteMapUiState.Ready).hasLowAccuracy)
    }

    // ── flow-based re-emission ─────────────────────────────────────────────

    /**
     * Confirms that after an initial empty state, inserting two points causes
     * the Flow to emit a non-empty list — simulating the Loading → Ready
     * transition described in task 7.2.
     */
    @Test
    fun insertingPoints_flowEmitsNonEmptyList() = runTest {
        // First emission should be empty (nothing inserted yet)
        val emptyEmission = routePointDao.getPointsForTrip(tripId).first()
        assertTrue(emptyEmission.isEmpty())

        // Insert two points
        routePointDao.insertAll(
            listOf(
                makeEntity(45.0, -66.0, 0),
                makeEntity(46.0, -67.0, 1),
            )
        )

        // Next emission must contain both points
        val filledEmission = routePointDao.getPointsForTrip(tripId).first()
        assertEquals(2, filledEmission.size)

        // Deriving state from the filled emission must produce Ready
        val state = filledEmission.map { it.toDomain() }.toRouteMapUiState()
        assertTrue(state is RouteMapUiState.Ready)
    }
}
