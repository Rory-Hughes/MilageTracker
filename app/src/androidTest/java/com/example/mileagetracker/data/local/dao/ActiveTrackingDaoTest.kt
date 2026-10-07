package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ActiveRoutePointEntity
import com.example.mileagetracker.data.local.entity.ActiveTrackingSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [ActiveTrackingDao] against a real (in-memory)
 * SQLite database. These exercise the single-session design (hardcoded id=1),
 * the REPLACE upsert semantics, chronological point ordering, selective
 * deletion of points vs session, and the ON DELETE CASCADE from
 * `active_tracking_sessions` to `active_route_points`.
 */
@RunWith(AndroidJUnit4::class)
class ActiveTrackingDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: ActiveTrackingDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.activeTrackingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun session(
        ownerUid: String = "u1",
        startedAt: Long = 1_000L,
        detectionSource: String = "AUTO",
        batteryLevelAtStart: Int? = 85,
        batterySaverSeen: Boolean = false,
        isManual: Boolean = false,
    ) = ActiveTrackingSessionEntity(
        id = ActiveTrackingSessionEntity.ACTIVE_SESSION_ID,
        ownerUid = ownerUid,
        startedAt = startedAt,
        detectionSource = detectionSource,
        batteryLevelAtStart = batteryLevelAtStart,
        batterySaverSeen = batterySaverSeen,
        isManual = isManual,
    )

    private fun point(
        timestampMs: Long,
        lat: Double = 45.0,
        lng: Double = -66.0,
        altitudeMetres: Double? = null,
        accuracyMetres: Float? = null,
        speedKph: Float? = null,
    ) = ActiveRoutePointEntity(
        sessionId = ActiveTrackingSessionEntity.ACTIVE_SESSION_ID,
        lat = lat,
        lng = lng,
        altitudeMetres = altitudeMetres,
        accuracyMetres = accuracyMetres,
        speedKph = speedKph,
        timestampMs = timestampMs,
    )

    // ── upsertSession + getSession ─────────────────────────────────────────────

    @Test
    fun upsertSession_thenGetSession_roundTripsAllFields() = runTest {
        dao.upsertSession(
            session(
                ownerUid = "alice",
                startedAt = 9_000L,
                detectionSource = "MANUAL",
                batteryLevelAtStart = 42,
                batterySaverSeen = true,
                isManual = true,
            ),
        )

        val loaded = dao.getSession()!!
        assertEquals("alice", loaded.ownerUid)
        assertEquals(9_000L, loaded.startedAt)
        assertEquals("MANUAL", loaded.detectionSource)
        assertEquals(42, loaded.batteryLevelAtStart)
        assertEquals(true, loaded.batterySaverSeen)
        assertEquals(true, loaded.isManual)
    }

    @Test
    fun getSession_returnsNull_whenNoSessionExists() = runTest {
        assertNull(dao.getSession())
    }

    @Test
    fun upsertSession_replacesExistingSession_singleRowContract() = runTest {
        dao.upsertSession(session(ownerUid = "first", startedAt = 1_000L))
        dao.upsertSession(session(ownerUid = "second", startedAt = 2_000L))

        // Single-session design: only the latest session survives.
        val loaded = dao.getSession()!!
        assertEquals("second", loaded.ownerUid)
        assertEquals(2_000L, loaded.startedAt)
        assertEquals(1, dao.activeCount())
    }

    @Test
    fun activeCount_returnsZero_whenNoSession() = runTest {
        assertEquals(0, dao.activeCount())
    }

    @Test
    fun activeCount_returnsOne_afterUpsert() = runTest {
        dao.upsertSession(session())
        assertEquals(1, dao.activeCount())
    }

    // ── insertPoint + getPoints ───────────────────────────────────────────────

    @Test
    fun insertPoint_thenGetPoints_roundTripsAllFields() = runTest {
        dao.upsertSession(session())
        dao.insertPoint(
            point(
                timestampMs = 5_000L,
                lat = 45.123,
                lng = -66.456,
                altitudeMetres = 12.5,
                accuracyMetres = 4f,
                speedKph = 60f,
            ),
        )

        val loaded = dao.getPoints().single()
        assertEquals(5_000L, loaded.timestampMs)
        assertEquals(45.123, loaded.lat, 0.0)
        assertEquals(-66.456, loaded.lng, 0.0)
        assertEquals(12.5, loaded.altitudeMetres!!, 0.0)
        assertEquals(4f, loaded.accuracyMetres)
        assertEquals(60f, loaded.speedKph)
    }

    @Test
    fun getPoints_ordersChronologicallyAscending() = runTest {
        dao.upsertSession(session())
        dao.insertPoint(point(timestampMs = 3_000L))
        dao.insertPoint(point(timestampMs = 1_000L))
        dao.insertPoint(point(timestampMs = 2_000L))

        val timestamps = dao.getPoints().map { it.timestampMs }
        assertEquals(listOf(1_000L, 2_000L, 3_000L), timestamps)
    }

    @Test
    fun getPoints_returnsEmpty_whenNoPointsInserted() = runTest {
        dao.upsertSession(session())
        assertTrue(dao.getPoints().isEmpty())
    }

    // ── deletePoints ──────────────────────────────────────────────────────────

    @Test
    fun deletePoints_removesAllPoints_leavesSessionIntact() = runTest {
        dao.upsertSession(session())
        dao.insertPoint(point(timestampMs = 1_000L))
        dao.insertPoint(point(timestampMs = 2_000L))

        dao.deletePoints()

        assertTrue(dao.getPoints().isEmpty())
        assertNotNull(dao.getSession()) // session still present
    }

    // ── deleteSession ─────────────────────────────────────────────────────────

    @Test
    fun deleteSession_removesSession_andGetSessionReturnsNull() = runTest {
        dao.upsertSession(session())
        dao.deleteSession()

        assertNull(dao.getSession())
        assertEquals(0, dao.activeCount())
    }

    @Test
    fun deleteSession_cascadeDeletesPoints() = runTest {
        dao.upsertSession(session())
        dao.insertPoint(point(timestampMs = 1_000L))
        dao.insertPoint(point(timestampMs = 2_000L))

        // Deleting the session must cascade to active_route_points (ON DELETE CASCADE).
        dao.deleteSession()

        assertTrue(dao.getPoints().isEmpty())
    }

    // ── nullable battery level ────────────────────────────────────────────────

    @Test
    fun upsertSession_persistsNullBatteryLevel() = runTest {
        dao.upsertSession(session(batteryLevelAtStart = null))

        assertNull(dao.getSession()!!.batteryLevelAtStart)
    }
}
