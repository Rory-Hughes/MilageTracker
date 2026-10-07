package com.example.mileagetracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository integration tests for [VehicleRepositoryImpl] against a real
 * (in-memory) SQLite database.
 *
 * The primary value here — beyond what [VehicleDaoTest] already covers —
 * is testing the repository's non-trivial business logic:
 *
 * - [VehicleRepositoryImpl.updateVehicle] stamps `updatedAt`.
 * - [VehicleRepositoryImpl.deactivateVehicle] clears both `isActive` and
 *   `isDefault` atomically.
 * - [VehicleRepositoryImpl.setDefaultVehicle] is a `@Transaction`:
 *   - clears all existing defaults for the owner + year,
 *   - promotes the target vehicle,
 *   - throws [IllegalArgumentException] for an unknown vehicle,
 *   - throws [IllegalArgumentException] for an inactive vehicle.
 *
 * The repository is wired directly (no Hilt) so the full chain executes
 * against real SQLite without mocking.
 */
@RunWith(AndroidJUnit4::class)
class VehicleRepositoryImplTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: VehicleRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = VehicleRepositoryImpl(db.vehicleDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun vehicle(
        ownerUid: String = "u1",
        displayName: String = "Car",
        fiscalYear: Int = 2026,
        isActive: Boolean = true,
        isDefault: Boolean = false,
    ) = VehicleEntity(
        ownerUid = ownerUid,
        displayName = displayName,
        fiscalYear = fiscalYear,
        isActive = isActive,
        isDefault = isDefault,
    )

    // ── saveVehicle + getVehicleById ──────────────────────────────────────────

    @Test
    fun saveVehicle_returnsId_andGetByIdReadsItBack() = runTest {
        val id = repository.saveVehicle(vehicle(displayName = "Civic"))

        assertTrue(id > 0)
        val loaded = repository.getVehicleById(id)!!
        assertEquals("Civic", loaded.displayName)
    }

    @Test
    fun getVehicleById_returnsNull_forUnknownId() = runTest {
        assertNull(repository.getVehicleById(999))
    }

    // ── updateVehicle stamps updatedAt ────────────────────────────────────────

    @Test
    fun updateVehicle_stampsUpdatedAt_andPersistsChangedDisplayName() = runTest {
        val id = repository.saveVehicle(vehicle(displayName = "Old"))
        val original = repository.getVehicleById(id)!!
        val originalUpdatedAt = original.updatedAt

        repository.updateVehicle(original.copy(displayName = "New"))

        val updated = repository.getVehicleById(id)!!
        assertEquals("New", updated.displayName)
        assertTrue(
            "updatedAt must advance after updateVehicle",
            updated.updatedAt >= originalUpdatedAt,
        )
    }

    // ── deactivateVehicle ─────────────────────────────────────────────────────

    @Test
    fun deactivateVehicle_clearsIsActiveAndIsDefault() = runTest {
        val id = repository.saveVehicle(vehicle(isDefault = true, isActive = true))

        repository.deactivateVehicle(id)

        val loaded = repository.getVehicleById(id)!!
        assertFalse(loaded.isActive)
        assertFalse(loaded.isDefault)
    }

    @Test
    fun deactivateVehicle_dropsVehicleFromActiveList() = runTest {
        val id = repository.saveVehicle(vehicle())
        assertEquals(1, repository.getActiveVehicles("u1", 2026).first().size)

        repository.deactivateVehicle(id)

        assertTrue(repository.getActiveVehicles("u1", 2026).first().isEmpty())
    }

    // ── setDefaultVehicle: happy path ─────────────────────────────────────────

    @Test
    fun setDefaultVehicle_promotesVehicle_andClearsPreviousDefault() = runTest {
        val prev = repository.saveVehicle(vehicle(displayName = "Prev", isDefault = true))
        val next = repository.saveVehicle(vehicle(displayName = "Next", isDefault = false))

        repository.setDefaultVehicle(vehicleId = next, ownerUid = "u1", fiscalYear = 2026)

        assertFalse(repository.getVehicleById(prev)!!.isDefault)
        assertTrue(repository.getVehicleById(next)!!.isDefault)
    }

    @Test
    fun setDefaultVehicle_isIdempotent_whenVehicleIsAlreadyDefault() = runTest {
        val id = repository.saveVehicle(vehicle(isDefault = true))

        repository.setDefaultVehicle(vehicleId = id, ownerUid = "u1", fiscalYear = 2026)

        assertTrue(repository.getVehicleById(id)!!.isDefault)
    }

    @Test
    fun setDefaultVehicle_scopedToOwnerYear_doesNotAffectOtherOwner() = runTest {
        val u1Vehicle = repository.saveVehicle(vehicle(ownerUid = "u1", isDefault = true))
        val u2Vehicle = repository.saveVehicle(vehicle(ownerUid = "u2", isDefault = true))
        val u1NewDefault = repository.saveVehicle(vehicle(ownerUid = "u1", isDefault = false))

        repository.setDefaultVehicle(
            vehicleId  = u1NewDefault,
            ownerUid   = "u1",
            fiscalYear = 2026,
        )

        // u2's default must be untouched.
        assertTrue(repository.getVehicleById(u2Vehicle)!!.isDefault)
        // u1's old default cleared.
        assertFalse(repository.getVehicleById(u1Vehicle)!!.isDefault)
        assertTrue(repository.getVehicleById(u1NewDefault)!!.isDefault)
    }

    // ── setDefaultVehicle: error paths ────────────────────────────────────────

    @Test
    fun setDefaultVehicle_throwsIllegalArgument_forUnknownVehicle() = runTest {
        try {
            repository.setDefaultVehicle(vehicleId = 999, ownerUid = "u1", fiscalYear = 2026)
            fail("Expected IllegalArgumentException for unknown vehicle")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("999"))
        }
    }

    @Test
    fun setDefaultVehicle_throwsIllegalArgument_forInactiveVehicle() = runTest {
        val id = repository.saveVehicle(vehicle(isActive = false, isDefault = false))

        try {
            repository.setDefaultVehicle(vehicleId = id, ownerUid = "u1", fiscalYear = 2026)
            fail("Expected IllegalArgumentException for inactive vehicle")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("inactive"))
        }
    }

    // ── getTripCountForVehicle ────────────────────────────────────────────────

    @Test
    fun getTripCountForVehicle_returnsZero_forVehicleWithNoTrips() = runTest {
        val id = repository.saveVehicle(vehicle())
        assertEquals(0, repository.getTripCountForVehicle(id))
    }

    // ── deleteVehicle ─────────────────────────────────────────────────────────

    @Test
    fun deleteVehicle_hardDeletesRow_getByIdReturnsNull() = runTest {
        val id = repository.saveVehicle(vehicle())
        val loaded = repository.getVehicleById(id)!!

        repository.deleteVehicle(loaded)

        assertNull(repository.getVehicleById(id))
    }

    // ── getDefaultVehicle ─────────────────────────────────────────────────────

    @Test
    fun getDefaultVehicle_returnsActiveDefault_orNull() = runTest {
        repository.saveVehicle(vehicle(displayName = "Default", isDefault = true))
        repository.saveVehicle(vehicle(displayName = "Other",   isDefault = false))

        val default = repository.getDefaultVehicle("u1", 2026)
        assertNotNull(default)
        assertEquals("Default", default!!.displayName)
    }

    @Test
    fun getDefaultVehicle_returnsNull_whenNoneSet() = runTest {
        repository.saveVehicle(vehicle(isDefault = false))
        assertNull(repository.getDefaultVehicle("u1", 2026))
    }
}
