package com.example.mileagetracker.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room integration tests for [VehicleDao] against a real (in-memory) SQLite
 * database. These exercise the actual SQL — owner/fiscal-year scoping, the
 * single-default invariant ([VehicleDao.clearDefaultForYear] /
 * [VehicleDao.getDefaultVehicle]), active filtering, the default-first / name
 * ordering, deactivation, and the cross-table [VehicleDao.countDrivesForVehicle]
 * guard query.
 */
@RunWith(AndroidJUnit4::class)
class VehicleDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: VehicleDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.vehicleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────

    private fun vehicle(
        id: Int = 0,
        ownerUid: String = "u1",
        displayName: String = "Car",
        fiscalYear: Int = 2026,
        isActive: Boolean = true,
        isDefault: Boolean = false,
    ) = VehicleEntity(
        id = id,
        ownerUid = ownerUid,
        displayName = displayName,
        fiscalYear = fiscalYear,
        isActive = isActive,
        isDefault = isDefault,
    )

    private fun driveForVehicle(vehicleId: Int, deletedAt: Long? = null) = DriveEntity(
        startedAt = 1_000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = 1f,
        vehicleId = vehicleId,
        driverUid = "u1",
        trackingSource = TrackingSource.AUTO_TRACKED,
        deletedAt = deletedAt,
    )

    // ── insert + single-row reads ────────────────────────────────────────────

    @Test
    fun insertVehicle_returnsId_andGetByIdReadsBack_regardlessOfActiveStatus() = runTest {
        val id = dao.insertVehicle(vehicle(displayName = "My Civic", isActive = false)).toInt()

        // getVehicleById is not active-filtered.
        val loaded = dao.getVehicleById(id)
        assertEquals("My Civic", loaded?.displayName)
        assertFalse(loaded!!.isActive)
    }

    @Test
    fun getVehicleById_returnsNull_forUnknownId() = runTest {
        assertNull(dao.getVehicleById(999))
    }

    // ── getActiveVehicles: scoping, filtering, ordering ──────────────────────

    @Test
    fun getActiveVehicles_scopesByOwnerAndYear_excludesInactive_defaultFirstThenName() = runTest {
        dao.insertVehicle(vehicle(displayName = "Zeta", isDefault = false))
        dao.insertVehicle(vehicle(displayName = "Alpha", isDefault = true))
        dao.insertVehicle(vehicle(displayName = "Beta", isDefault = false))
        dao.insertVehicle(vehicle(displayName = "InactiveOne", isActive = false))
        dao.insertVehicle(vehicle(displayName = "OtherOwner", ownerUid = "u2"))
        dao.insertVehicle(vehicle(displayName = "PriorYear", fiscalYear = 2025))

        val names = dao.getActiveVehicles("u1", 2026).first().map { it.displayName }

        // default first, then display_name ASC; inactive/other-owner/other-year excluded.
        assertEquals(listOf("Alpha", "Beta", "Zeta"), names)
    }

    // ── getDefaultVehicle ────────────────────────────────────────────────────

    @Test
    fun getDefaultVehicle_returnsActiveDefaultForOwnerYear() = runTest {
        dao.insertVehicle(vehicle(displayName = "Default", isDefault = true))
        dao.insertVehicle(vehicle(displayName = "NonDefault", isDefault = false))

        assertEquals("Default", dao.getDefaultVehicle("u1", 2026)?.displayName)
    }

    @Test
    fun getDefaultVehicle_returnsNull_whenNoDefaultSet() = runTest {
        dao.insertVehicle(vehicle(displayName = "NonDefault", isDefault = false))

        assertNull(dao.getDefaultVehicle("u1", 2026))
    }

    @Test
    fun getDefaultVehicle_ignoresInactiveDefault() = runTest {
        // A default flag on an inactive row must not be returned.
        dao.insertVehicle(vehicle(displayName = "ArchivedDefault", isDefault = true, isActive = false))

        assertNull(dao.getDefaultVehicle("u1", 2026))
    }

    // ── clearDefaultForYear ──────────────────────────────────────────────────

    @Test
    fun clearDefaultForYear_clearsOnlyMatchingOwnerYear() = runTest {
        val target = dao.insertVehicle(vehicle(displayName = "Target", isDefault = true)).toInt()
        val priorYear = dao.insertVehicle(
            vehicle(displayName = "PriorYear", fiscalYear = 2025, isDefault = true),
        ).toInt()
        val otherOwner = dao.insertVehicle(
            vehicle(displayName = "OtherOwner", ownerUid = "u2", isDefault = true),
        ).toInt()

        dao.clearDefaultForYear(ownerUid = "u1", fiscalYear = 2026, updatedAt = 5_000L)

        assertFalse(dao.getVehicleById(target)!!.isDefault)      // cleared
        assertTrue(dao.getVehicleById(priorYear)!!.isDefault)    // different year, untouched
        assertTrue(dao.getVehicleById(otherOwner)!!.isDefault)   // different owner, untouched
        assertEquals(5_000L, dao.getVehicleById(target)!!.updatedAt)
    }

    // ── deactivateVehicle ────────────────────────────────────────────────────

    @Test
    fun deactivateVehicle_clearsActiveAndDefault_andDropsFromActiveList() = runTest {
        val id = dao.insertVehicle(vehicle(displayName = "ToArchive", isDefault = true)).toInt()

        dao.deactivateVehicle(id, updatedAt = 7_000L)

        val loaded = dao.getVehicleById(id)!!
        assertFalse(loaded.isActive)
        assertFalse(loaded.isDefault)
        assertEquals(7_000L, loaded.updatedAt)
        assertTrue(dao.getActiveVehicles("u1", 2026).first().isEmpty())
    }

    // ── getAllVehiclesForUser ────────────────────────────────────────────────

    @Test
    fun getAllVehiclesForUser_includesInactive_orderedByYearDescThenName() = runTest {
        dao.insertVehicle(vehicle(displayName = "B2026", fiscalYear = 2026))
        dao.insertVehicle(vehicle(displayName = "A2026", fiscalYear = 2026, isActive = false))
        dao.insertVehicle(vehicle(displayName = "Z2025", fiscalYear = 2025))
        dao.insertVehicle(vehicle(displayName = "OtherOwner", ownerUid = "u2"))

        val labels = dao.getAllVehiclesForUser("u1").first()
            .map { "${it.fiscalYear}:${it.displayName}" }

        // fiscal_year DESC, then display_name ASC; inactive included; other owner excluded.
        assertEquals(listOf("2026:A2026", "2026:B2026", "2025:Z2025"), labels)
    }

    // ── countDrivesForVehicle (cross-table guard) ────────────────────────────

    @Test
    fun countDrivesForVehicle_countsAllReferencingDrives_includingSoftDeleted() = runTest {
        val vehicleId = dao.insertVehicle(vehicle(displayName = "Tracked")).toInt()
        val otherVehicleId = dao.insertVehicle(vehicle(displayName = "Untracked")).toInt()

        db.driveDao().insertDrive(driveForVehicle(vehicleId))
        db.driveDao().insertDrive(driveForVehicle(vehicleId, deletedAt = 1L)) // soft-deleted still counts
        db.driveDao().insertDrive(driveForVehicle(otherVehicleId))            // different vehicle

        assertEquals(2, dao.countDrivesForVehicle(vehicleId))
        assertEquals(0, dao.countDrivesForVehicle(999))
    }

    // ── update / delete ──────────────────────────────────────────────────────

    @Test
    fun updateVehicle_persistsChangedFields() = runTest {
        val id = dao.insertVehicle(vehicle(displayName = "Old")).toInt()
        val loaded = dao.getVehicleById(id)!!

        dao.updateVehicle(loaded.copy(displayName = "New", closingOdometerKm = 12_345f))

        val updated = dao.getVehicleById(id)!!
        assertEquals("New", updated.displayName)
        assertEquals(12_345f, updated.closingOdometerKm)
    }

    @Test
    fun deleteVehicle_removesRow() = runTest {
        val id = dao.insertVehicle(vehicle()).toInt()
        val loaded = dao.getVehicleById(id)!!

        dao.deleteVehicle(loaded)

        assertNull(dao.getVehicleById(id))
    }

    // ── conflict strategy ────────────────────────────────────────────────────

    @Test
    fun insertVehicle_withDuplicatePrimaryKey_aborts() = runTest {
        dao.insertVehicle(vehicle(id = 42))
        val error = try {
            dao.insertVehicle(vehicle(id = 42))
            null
        } catch (error: SQLiteConstraintException) {
            error
        }
        assertNotNull(error)
    }
}
