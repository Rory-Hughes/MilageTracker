package com.example.mileagetracker.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import kotlinx.coroutines.flow.first
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
 * Room integration tests for [DriveDao] against a real (in-memory) SQLite
 * database. These exercise the actual SQL — soft-delete filtering, driver
 * scoping, category filters, date-range aggregation, ordering, and the
 * time-window overlap query — which JVM unit tests with a mocked DAO cannot.
 *
 * Each test gets a fresh [MileageDatabase]; [allowMainThreadQueries] lets the
 * suspend/Flow DAO calls run directly inside [runTest].
 */
@RunWith(AndroidJUnit4::class)
class DriveDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: DriveDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.driveDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    private fun drive(
        id: Int = 0,
        driverUid: String = "u1",
        startedAt: Long = 1_000L,
        endedAt: Long? = null,
        distanceKm: Float = 10f,
        category: TripCategory = TripCategory.BUSINESS,
        vehicleId: Int? = null,
        deletedAt: Long? = null,
    ) = DriveEntity(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        startLocation = "A",
        endLocation = "B",
        distanceKm = distanceKm,
        vehicleId = vehicleId,
        driverUid = driverUid,
        tripCategory = category,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource = TrackingSource.AUTO_TRACKED,
        deletedAt = deletedAt,
    )

    // ── insert + single-row read ─────────────────────────────────────────────

    @Test
    fun insertDrive_returnsGeneratedId_andGetByIdReadsItBack() = runTest {
        val rowId = dao.insertDrive(drive(distanceKm = 12.5f)).toInt()

        val loaded = dao.getDriveById(rowId)
        assertEquals(rowId, loaded?.id)
        assertEquals(12.5f, loaded?.distanceKm)
    }

    @Test
    fun getDriveById_returnsNull_forUnknownId() = runTest {
        assertNull(dao.getDriveById(999))
    }

    @Test
    fun getDriveById_returnsNull_afterSoftDelete() = runTest {
        val id = dao.insertDrive(drive()).toInt()
        assertTrue(dao.getDriveById(id) != null)

        dao.softDeleteDrive(id, deletedAt = 5_000L)

        assertNull(dao.getDriveById(id))
    }

    // ── getAllDrives: scoping, ordering, soft-delete ─────────────────────────

    @Test
    fun getAllDrives_returnsOnlyOwnersActiveRows_newestFirst() = runTest {
        dao.insertDrive(drive(driverUid = "u1", startedAt = 100L))
        dao.insertDrive(drive(driverUid = "u1", startedAt = 300L))
        dao.insertDrive(drive(driverUid = "u1", startedAt = 200L))
        dao.insertDrive(drive(driverUid = "u2", startedAt = 999L)) // other driver
        dao.insertDrive(drive(driverUid = "u1", startedAt = 400L, deletedAt = 1L)) // soft-deleted

        val result = dao.getAllDrives("u1").first()

        // Excludes u2 and the soft-deleted row; ordered by started_at DESC.
        assertEquals(listOf(300L, 200L, 100L), result.map { it.startedAt })
    }

    // ── getUnclassifiedDrives ────────────────────────────────────────────────

    @Test
    fun getUnclassifiedDrives_returnsOnlyUnclassifiedCategory() = runTest {
        dao.insertDrive(drive(category = TripCategory.UNCLASSIFIED, startedAt = 100L))
        dao.insertDrive(drive(category = TripCategory.BUSINESS, startedAt = 200L))
        dao.insertDrive(drive(category = TripCategory.PERSONAL, startedAt = 300L))

        val result = dao.getUnclassifiedDrives("u1").first()

        assertEquals(1, result.size)
        assertEquals(TripCategory.UNCLASSIFIED, result.single().tripCategory)
    }

    // ── getDrivesByVehicle ───────────────────────────────────────────────────

    @Test
    fun getDrivesByVehicle_filtersByVehicle_andExcludesSoftDeleted() = runTest {
        dao.insertDrive(drive(vehicleId = 7, startedAt = 100L))
        dao.insertDrive(drive(vehicleId = 7, startedAt = 200L, deletedAt = 1L)) // deleted
        dao.insertDrive(drive(vehicleId = 9, startedAt = 300L)) // other vehicle

        val result = dao.getDrivesByVehicle(7).first()

        assertEquals(1, result.size)
        assertEquals(100L, result.single().startedAt)
    }

    // ── getYearToDateBusinessDistance ────────────────────────────────────────

    @Test
    fun ytdBusinessDistance_sumsOnlyBusinessRowsInRange() = runTest {
        dao.insertDrive(drive(category = TripCategory.BUSINESS, startedAt = 1_500L, distanceKm = 10f))
        dao.insertDrive(drive(category = TripCategory.BUSINESS, startedAt = 1_800L, distanceKm = 5f))
        dao.insertDrive(drive(category = TripCategory.BUSINESS, startedAt = 2_500L, distanceKm = 100f)) // out of range
        dao.insertDrive(
            drive(category = TripCategory.PERSONAL, startedAt = 1_500L, distanceKm = 50f)
        ) // wrong category
        dao.insertDrive(
            drive(category = TripCategory.BUSINESS, startedAt = 1_500L, distanceKm = 999f, deletedAt = 1L)
        ) // deleted

        val sum = dao.getYearToDateBusinessDistance("u1", fromMillis = 1_000L, toMillis = 2_000L).first()

        assertEquals(15f, sum!!, 0.0001f)
    }

    @Test
    fun ytdBusinessDistance_returnsNull_whenNoQualifyingRows() = runTest {
        // A non-qualifying personal row exists, but SUM over zero matches is NULL,
        // letting callers distinguish "no data" from "0 km".
        dao.insertDrive(drive(category = TripCategory.PERSONAL, startedAt = 1_500L))

        val sum = dao.getYearToDateBusinessDistance("u1", fromMillis = 1_000L, toMillis = 2_000L).first()

        assertNull(sum)
    }

    // ── getBusinessDrivesByDateRange ─────────────────────────────────────────

    @Test
    fun businessDrivesByDateRange_appliesCategoryAndInclusiveBounds() = runTest {
        dao.insertDrive(drive(category = TripCategory.BUSINESS, startedAt = 1_000L)) // lower bound, inclusive
        dao.insertDrive(drive(category = TripCategory.BUSINESS, startedAt = 2_000L)) // upper bound, inclusive
        dao.insertDrive(drive(category = TripCategory.BUSINESS, startedAt = 2_001L)) // just outside
        dao.insertDrive(drive(category = TripCategory.PERSONAL, startedAt = 1_500L)) // wrong category

        val result = dao.getBusinessDrivesByDateRange("u1", fromMillis = 1_000L, toMillis = 2_000L).first()

        assertEquals(listOf(2_000L, 1_000L), result.map { it.startedAt }) // DESC order
    }

    // ── getDrivesForAnnualSummary (ASC order, all categories) ─────────────────

    @Test
    fun drivesForAnnualSummary_returnsAllCategoriesAscending() = runTest {
        dao.insertDrive(drive(category = TripCategory.PERSONAL, startedAt = 300L))
        dao.insertDrive(drive(category = TripCategory.BUSINESS, startedAt = 100L))
        dao.insertDrive(drive(category = TripCategory.MEDICAL, startedAt = 200L))

        val result = dao.getDrivesForAnnualSummary("u1", fromMillis = 0L, toMillis = 1_000L)

        assertEquals(listOf(100L, 200L, 300L), result.map { it.startedAt })
    }

    // ── getDrivesInTimeWindow (overlap + excludeId) ──────────────────────────

    @Test
    fun drivesInTimeWindow_matchesOverlap_andHonoursExcludeId() = runTest {
        val overlappingId = dao.insertDrive(
            drive(startedAt = 1_000L, endedAt = 2_000L),
        ).toInt()
        dao.insertDrive(drive(startedAt = 5_000L, endedAt = 6_000L)) // no overlap with window

        // Window [1_500, 1_600] overlaps the first trip only.
        val matches = dao.getDrivesInTimeWindow("u1", windowStartMs = 1_500L, windowEndMs = 1_600L)
        assertEquals(listOf(overlappingId), matches.map { it.id })

        // Excluding the overlapping trip itself yields nothing.
        val excluded = dao.getDrivesInTimeWindow(
            "u1",
            windowStartMs = 1_500L,
            windowEndMs = 1_600L,
            excludeId = overlappingId,
        )
        assertTrue(excluded.isEmpty())
    }

    @Test
    fun drivesInTimeWindow_includesInProgressTrip_withNullEndedAt() = runTest {
        // ended_at IS NULL trips are still "active" and must match a later window.
        val id = dao.insertDrive(drive(startedAt = 1_000L, endedAt = null)).toInt()

        val matches = dao.getDrivesInTimeWindow("u1", windowStartMs = 9_000L, windowEndMs = 9_500L)

        assertEquals(listOf(id), matches.map { it.id })
    }

    // ── insert conflict strategy ─────────────────────────────────────────────

    @Test
    fun insertDrive_withDuplicatePrimaryKey_aborts() = runTest {
        dao.insertDrive(drive(id = 42))
        val error = try {
            dao.insertDrive(drive(id = 42))
            null
        } catch (error: SQLiteConstraintException) {
            error
        }
        assertNotNull(error)
    }
}
