package com.example.mileagetracker.data.local

import androidx.room.withTransaction
import com.example.mileagetracker.data.local.dao.ActiveTrackingDao
import com.example.mileagetracker.data.local.entity.ActiveRoutePointEntity
import com.example.mileagetracker.data.local.entity.ActiveTrackingSessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ActiveTripStoreTest {

    private lateinit var database: MileageDatabase
    private lateinit var dao: ActiveTrackingDao
    private lateinit var store: ActiveTripStore

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        database = mockk()
        dao = mockk()
        store = ActiveTripStore(database, dao)

        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `begin clears existing and upserts new session`() = runTest {
        val session = ActiveTrackingSessionEntity(
            ownerUid = "u1",
            startedAt = 1000L,
            detectionSource = "AUTO",
            batteryLevelAtStart = 80,
            batterySaverSeen = false,
            isManual = false
        )
        
        coEvery { dao.deletePoints() } returns Unit
        coEvery { dao.deleteSession() } returns Unit
        coEvery { dao.upsertSession(any()) } returns Unit

        store.begin(session)

        coVerify {
            dao.deletePoints()
            dao.deleteSession()
            dao.upsertSession(session)
        }
    }

    @Test
    fun `snapshot returns null if no session`() = runTest {
        coEvery { dao.getSession() } returns null
        
        val result = store.snapshot()
        
        assertNull(result)
    }

    @Test
    fun `snapshot returns session and points`() = runTest {
        val session = mockk<ActiveTrackingSessionEntity>()
        val points = listOf(mockk<ActiveRoutePointEntity>())
        
        coEvery { dao.getSession() } returns session
        coEvery { dao.getPoints() } returns points

        val result = store.snapshot()

        assertNotNull(result)
        assertEquals(session, result?.session)
        assertEquals(points, result?.points)
    }

    @Test
    fun `append inserts point`() = runTest {
        val point = ActiveRoutePointEntity(
            id = 0L,
            lat = 45.5,
            lng = -63.6,
            altitudeMetres = 20.0,
            accuracyMetres = 5.0f,
            speedKph = 38.2f,
            timestampMs = 1234L,
        )
        coEvery { dao.insertPoint(point) } returns Unit

        store.append(point)

        coVerify(exactly = 1) { dao.insertPoint(point) }
    }

    @Test
    fun `clear deletes points and session in transaction`() = runTest {
        coEvery { dao.deletePoints() } returns Unit
        coEvery { dao.deleteSession() } returns Unit

        store.clear()

        coVerify {
            dao.deletePoints()
            dao.deleteSession()
        }
    }

    @Test
    fun `hasActiveTrip returns true when dao count is positive`() = runTest {
        coEvery { dao.activeCount() } returns 1

        val result = store.hasActiveTrip()

        assertTrue(result)
    }

    @Test
    fun `hasActiveTrip returns false when dao count is zero`() = runTest {
        coEvery { dao.activeCount() } returns 0

        val result = store.hasActiveTrip()

        assertFalse(result)
    }
}
