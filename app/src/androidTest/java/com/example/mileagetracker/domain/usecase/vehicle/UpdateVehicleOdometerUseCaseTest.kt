package com.example.mileagetracker.domain.usecase.vehicle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [UpdateVehicleOdometerUseCase] against a real
 * (in-memory) Room DB.
 *
 * ## Behaviour under test
 * - **Both null**: no-op, returns `true`, nothing written.
 * - **Vehicle not found**: returns `false`.
 * - **Negative openingKm**: throws [IllegalArgumentException].
 * - **Negative closingKm**: throws [IllegalArgumentException].
 * - **closingKm < openingKm** (in same call): throws [IllegalArgumentException].
 * - **closingKm < stored opening** (only closingKm supplied): throws
 *   [IllegalArgumentException] because the effective opening is the stored value.
 * - **Happy paths**: opening-only, closing-only, and both values update correctly
 *   without touching the other field.
 * - **Zero openingKm**: accepted — a new vehicle with a fresh odometer is valid.
 * - **Equal opening and closing**: accepted — common for year-end snapshots.
 */
@RunWith(AndroidJUnit4::class)
class UpdateVehicleOdometerUseCaseTest {

    private lateinit var db:          MileageDatabase
    private lateinit var vehicleRepo: VehicleRepositoryImpl
    private lateinit var useCase:     UpdateVehicleOdometerUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        vehicleRepo = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase     = UpdateVehicleOdometerUseCase(vehicleRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private suspend fun insertVehicle(
        openingKm: Float? = null,
        closingKm: Float? = null,
    ): Int = vehicleRepo.saveVehicle(
        VehicleEntity(
            ownerUid          = "u1",
            displayName       = "Test Car",
            fiscalYear        = 2026,
            openingOdometerKm = openingKm,
            closingOdometerKm = closingKm,
        )
    )

    // ── No-op ─────────────────────────────────────────────────────────────────────

    @Test
    fun invoke_bothNull_returnsTrueWithoutChangingRow() = runTest {
        val vehicleId = insertVehicle()

        val result = useCase(vehicleId = vehicleId, openingKm = null, closingKm = null)

        assertTrue(result)
        val saved = vehicleRepo.getVehicleById(vehicleId)!!
        assertNull(saved.openingOdometerKm)
        assertNull(saved.closingOdometerKm)
    }

    // ── Vehicle not found ─────────────────────────────────────────────────────────

    @Test
    fun invoke_vehicleNotFound_returnsFalse() = runTest {
        val result = useCase(vehicleId = 9999, openingKm = 1_000f)
        assertFalse(result)
    }

    // ── Validation ────────────────────────────────────────────────────────────────

    @Test
    fun invoke_negativeOpeningKm_throwsIllegalArgument() = runTest {
        val vehicleId = insertVehicle()

        try {
            useCase(vehicleId = vehicleId, openingKm = -1f)
            fail("Expected IllegalArgumentException for negative opening")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Opening odometer", ignoreCase = true))
        }
    }

    @Test
    fun invoke_negativeClosingKm_throwsIllegalArgument() = runTest {
        val vehicleId = insertVehicle()

        try {
            useCase(vehicleId = vehicleId, closingKm = -1f)
            fail("Expected IllegalArgumentException for negative closing")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Closing odometer", ignoreCase = true))
        }
    }

    @Test
    fun invoke_closingLessThanOpening_inSameCall_throwsIllegalArgument() = runTest {
        val vehicleId = insertVehicle()

        try {
            useCase(vehicleId = vehicleId, openingKm = 10_000f, closingKm = 9_000f)
            fail("Expected IllegalArgumentException for closing < opening")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Closing odometer", ignoreCase = true))
        }
    }

    @Test
    fun invoke_closingLessThanStoredOpening_throwsIllegalArgument() = runTest {
        // Vehicle already has an opening reading stored; only closingKm supplied and it
        // is below the stored opening — effective opening is the stored value.
        val vehicleId = insertVehicle(openingKm = 10_000f)

        try {
            useCase(vehicleId = vehicleId, closingKm = 5_000f)
            fail("Expected IllegalArgumentException for closing < stored opening")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Closing odometer", ignoreCase = true))
        }
    }

    // ── Happy paths ───────────────────────────────────────────────────────────────

    @Test
    fun invoke_openingOnly_returnsTrueAndPersists() = runTest {
        val vehicleId = insertVehicle()

        val result = useCase(vehicleId = vehicleId, openingKm = 1_000f)

        assertTrue(result)
        assertEquals(1_000f, vehicleRepo.getVehicleById(vehicleId)!!.openingOdometerKm)
    }

    @Test
    fun invoke_openingOnly_doesNotAlterClosing() = runTest {
        val vehicleId = insertVehicle()

        useCase(vehicleId = vehicleId, openingKm = 1_000f)

        assertNull(vehicleRepo.getVehicleById(vehicleId)!!.closingOdometerKm)
    }

    @Test
    fun invoke_closingOnly_returnsTrueAndPersists() = runTest {
        val vehicleId = insertVehicle(openingKm = 10_000f)

        val result = useCase(vehicleId = vehicleId, closingKm = 15_000f)

        assertTrue(result)
        assertEquals(15_000f, vehicleRepo.getVehicleById(vehicleId)!!.closingOdometerKm)
    }

    @Test
    fun invoke_closingOnly_doesNotAlterStoredOpening() = runTest {
        val vehicleId = insertVehicle(openingKm = 10_000f)

        useCase(vehicleId = vehicleId, closingKm = 15_000f)

        assertEquals(10_000f, vehicleRepo.getVehicleById(vehicleId)!!.openingOdometerKm)
    }

    @Test
    fun invoke_bothValues_persistsCorrectly() = runTest {
        val vehicleId = insertVehicle()

        useCase(vehicleId = vehicleId, openingKm = 5_000f, closingKm = 20_000f)

        val saved = vehicleRepo.getVehicleById(vehicleId)!!
        assertEquals(5_000f, saved.openingOdometerKm)
        assertEquals(20_000f, saved.closingOdometerKm)
    }

    @Test
    fun invoke_zeroOpeningKm_isValid() = runTest {
        val vehicleId = insertVehicle()

        val result = useCase(vehicleId = vehicleId, openingKm = 0f)

        assertTrue(result)
        assertEquals(0f, vehicleRepo.getVehicleById(vehicleId)!!.openingOdometerKm)
    }

    @Test
    fun invoke_equalOpeningAndClosing_isValid() = runTest {
        val vehicleId = insertVehicle()

        val result = useCase(vehicleId = vehicleId, openingKm = 5_000f, closingKm = 5_000f)

        assertTrue(result)
    }

    @Test
    fun invoke_updateClosingTwice_secondValueWins() = runTest {
        val vehicleId = insertVehicle(openingKm = 0f)

        useCase(vehicleId = vehicleId, closingKm = 10_000f)
        useCase(vehicleId = vehicleId, closingKm = 12_000f)

        assertEquals(12_000f, vehicleRepo.getVehicleById(vehicleId)!!.closingOdometerKm)
    }
}
