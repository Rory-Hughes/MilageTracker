package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.OdometerCaptureEntity
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.domain.model.OdometerCaptureType
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
 * Room integration tests for [OdometerCaptureDao] against a real (in-memory)
 * SQLite database. These exercise the actual SQL — newest-first ordering,
 * vehicle-id scoping, single-row resolution ([getLatestForVehicle],
 * [getFirstCaptureForVehicleInYear], [getLastCaptureForVehicleInYear]),
 * the REPLACE conflict strategy, and the ON DELETE CASCADE from `vehicles`.
 *
 * `odometer_captures.vehicle_id` is a hard FK to `vehicles.id`, so each
 * test that inserts captures must first insert a parent [VehicleEntity].
 */
@RunWith(AndroidJUnit4::class)
class OdometerCaptureDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: OdometerCaptureDao
    private lateinit var vehicleDao: VehicleDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.odometerCaptureDao()
        vehicleDao = db.vehicleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private suspend fun insertVehicle(ownerUid: String = "u1"): Int =
        vehicleDao.insertVehicle(
            VehicleEntity(
                ownerUid = ownerUid,
                displayName = "Test Car",
                fiscalYear = 2026,
                isActive = true,
            ),
        ).toInt()

    private fun capture(
        id: Int = 0,
        vehicleId: Int,
        captureDate: Long,
        readingKm: Int = 10_000,
        photoUri: String? = null,
        captureType: String = OdometerCaptureType.MANUAL.name,
        notes: String? = null,
        createdAt: Long = 1_000L,
    ) = OdometerCaptureEntity(
        id = id,
        vehicleId = vehicleId,
        captureDate = captureDate,
        readingKm = readingKm,
        photoUri = photoUri,
        captureType = captureType,
        notes = notes,
        createdAt = createdAt,
    )

    // ── insert + read-back ─────────────────────────────────────────────────────

    @Test
    fun insert_thenGetForVehicle_roundTripsAllFields() = runTest {
        val vid = insertVehicle()
        dao.insert(
            capture(
                vehicleId = vid,
                captureDate = 5_000L,
                readingKm = 12_345,
                photoUri = "content://photos/1",
                captureType = OdometerCaptureType.PHOTO_ASSISTED.name,
                notes = "Annual close",
                createdAt = 6_000L,
            ),
        )

        val loaded = dao.getForVehicle(vid).first().single()
        assertEquals(vid, loaded.vehicleId)
        assertEquals(5_000L, loaded.captureDate)
        assertEquals(12_345, loaded.readingKm)
        assertEquals("content://photos/1", loaded.photoUri)
        assertEquals(OdometerCaptureType.PHOTO_ASSISTED.name, loaded.captureType)
        assertEquals("Annual close", loaded.notes)
        assertEquals(6_000L, loaded.createdAt)
    }

    @Test
    fun getForVehicle_returnsEmpty_forUnknownVehicleId() = runTest {
        assertTrue(dao.getForVehicle(999).first().isEmpty())
    }

    // ── ordering: newest capture_date first ────────────────────────────────────

    @Test
    fun getForVehicle_ordersNewestFirst() = runTest {
        val vid = insertVehicle()
        dao.insert(capture(vehicleId = vid, captureDate = 1_000L, readingKm = 100))
        dao.insert(capture(vehicleId = vid, captureDate = 3_000L, readingKm = 300))
        dao.insert(capture(vehicleId = vid, captureDate = 2_000L, readingKm = 200))

        val dates = dao.getForVehicle(vid).first().map { it.captureDate }
        assertEquals(listOf(3_000L, 2_000L, 1_000L), dates)
    }

    // ── vehicle-id scoping ────────────────────────────────────────────────────

    @Test
    fun getForVehicle_scopesToRequestedVehicle() = runTest {
        val v1 = insertVehicle()
        val v2 = insertVehicle()
        dao.insert(capture(vehicleId = v1, captureDate = 1_000L))
        dao.insert(capture(vehicleId = v2, captureDate = 2_000L))

        assertEquals(1, dao.getForVehicle(v1).first().size)
        assertEquals(v1, dao.getForVehicle(v1).first().single().vehicleId)
    }

    // ── getLatestForVehicle ───────────────────────────────────────────────────

    @Test
    fun getLatestForVehicle_returnsHighestCaptureDate() = runTest {
        val vid = insertVehicle()
        dao.insert(capture(vehicleId = vid, captureDate = 1_000L, readingKm = 100))
        dao.insert(capture(vehicleId = vid, captureDate = 3_000L, readingKm = 300))
        dao.insert(capture(vehicleId = vid, captureDate = 2_000L, readingKm = 200))

        val latest = dao.getLatestForVehicle(vid)!!
        assertEquals(3_000L, latest.captureDate)
        assertEquals(300, latest.readingKm)
    }

    @Test
    fun getLatestForVehicle_returnsNull_forVehicleWithNoCaptures() = runTest {
        val vid = insertVehicle()
        assertNull(dao.getLatestForVehicle(vid))
    }

    // ── getFirstCaptureForVehicleInYear (opening odometer) ────────────────────

    @Test
    fun getFirstCaptureForVehicleInYear_returnsEarliestInWindow_inclusiveBounds() = runTest {
        val vid = insertVehicle()
        dao.insert(capture(vehicleId = vid, captureDate = 999L,   readingKm = 1))  // before window
        dao.insert(capture(vehicleId = vid, captureDate = 1_000L, readingKm = 10)) // at lower bound
        dao.insert(capture(vehicleId = vid, captureDate = 2_000L, readingKm = 20))
        dao.insert(capture(vehicleId = vid, captureDate = 3_000L, readingKm = 30)) // at upper bound
        dao.insert(capture(vehicleId = vid, captureDate = 3_001L, readingKm = 40)) // after window

        val first = dao.getFirstCaptureForVehicleInYear(vid, fromMillis = 1_000L, toMillis = 3_000L)!!
        assertEquals(1_000L, first.captureDate)
        assertEquals(10, first.readingKm)
    }

    @Test
    fun getFirstCaptureForVehicleInYear_returnsNull_whenNoCaptures() = runTest {
        val vid = insertVehicle()
        assertNull(dao.getFirstCaptureForVehicleInYear(vid, fromMillis = 1_000L, toMillis = 2_000L))
    }

    // ── getLastCaptureForVehicleInYear (closing odometer) ─────────────────────

    @Test
    fun getLastCaptureForVehicleInYear_returnsLatestInWindow_inclusiveBounds() = runTest {
        val vid = insertVehicle()
        dao.insert(capture(vehicleId = vid, captureDate = 999L,   readingKm = 1))
        dao.insert(capture(vehicleId = vid, captureDate = 1_000L, readingKm = 10))
        dao.insert(capture(vehicleId = vid, captureDate = 2_000L, readingKm = 20))
        dao.insert(capture(vehicleId = vid, captureDate = 3_000L, readingKm = 30)) // should be returned
        dao.insert(capture(vehicleId = vid, captureDate = 3_001L, readingKm = 40)) // after window

        val last = dao.getLastCaptureForVehicleInYear(vid, fromMillis = 1_000L, toMillis = 3_000L)!!
        assertEquals(3_000L, last.captureDate)
        assertEquals(30, last.readingKm)
    }

    @Test
    fun getLastCaptureForVehicleInYear_returnsNull_whenNoCaptures() = runTest {
        val vid = insertVehicle()
        assertNull(dao.getLastCaptureForVehicleInYear(vid, fromMillis = 1_000L, toMillis = 2_000L))
    }

    // ── REPLACE conflict strategy ─────────────────────────────────────────────

    @Test
    fun insert_withSamePrimaryKey_replacesExistingRow() = runTest {
        val vid = insertVehicle()
        dao.insert(capture(id = 10, vehicleId = vid, captureDate = 1_000L, readingKm = 100))
        dao.insert(capture(id = 10, vehicleId = vid, captureDate = 2_000L, readingKm = 200))

        val all = dao.getForVehicle(vid).first()
        assertEquals(1, all.size)
        assertEquals(200, all.single().readingKm)
        assertEquals(2_000L, all.single().captureDate)
    }

    // ── ON DELETE CASCADE ─────────────────────────────────────────────────────

    @Test
    fun deletingVehicle_cascadeDeletesItsCaptures() = runTest {
        val vid = insertVehicle()
        val other = insertVehicle()
        dao.insert(capture(vehicleId = vid,   captureDate = 1_000L))
        dao.insert(capture(vehicleId = other, captureDate = 2_000L))

        val vehicle = vehicleDao.getVehicleById(vid)!!
        vehicleDao.deleteVehicle(vehicle)

        assertTrue(dao.getForVehicle(vid).first().isEmpty())
        assertEquals(1, dao.getForVehicle(other).first().size)
    }

}
