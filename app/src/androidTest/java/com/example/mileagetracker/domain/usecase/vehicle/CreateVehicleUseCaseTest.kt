package com.example.mileagetracker.domain.usecase.vehicle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [CreateVehicleUseCase] against a real (in-memory) Room DB.
 *
 * ## Behaviour under test
 * - **Validation**: blank [displayName], out-of-range [fiscalYear],
 *   negative [openingOdometerKm], and [closingOdometerKm] < [openingOdometerKm]
 *   all throw [IllegalArgumentException] before any write.
 * - **Happy path**: inserts the row and returns a positive auto-generated id.
 * - **Default promotion**: `setAsDefault = true` promotes the new vehicle to
 *   default for its `(ownerUid, fiscalYear)` pair.  A second `setAsDefault = true`
 *   clears the first and promotes the second.
 */
@RunWith(AndroidJUnit4::class)
class CreateVehicleUseCaseTest {

    private lateinit var db:          MileageDatabase
    private lateinit var vehicleRepo: VehicleRepositoryImpl
    private lateinit var useCase:     CreateVehicleUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        vehicleRepo = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase     = CreateVehicleUseCase(vehicleRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private fun vehicle(
        displayName:       String = "My Civic",
        fiscalYear:        Int    = 2026,
        openingOdometerKm: Float? = null,
        closingOdometerKm: Float? = null,
    ) = VehicleEntity(
        ownerUid          = "u1",
        displayName       = displayName,
        fiscalYear        = fiscalYear,
        openingOdometerKm = openingOdometerKm,
        closingOdometerKm = closingOdometerKm,
    )

    // ── Validation ────────────────────────────────────────────────────────────────

    @Test
    fun invoke_blankDisplayName_throwsIllegalArgument() = runTest {
        try {
            useCase(vehicle(displayName = "   "))
            fail("Expected IllegalArgumentException for blank displayName")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("display name", ignoreCase = true))
        }
    }

    @Test
    fun invoke_emptyDisplayName_throwsIllegalArgument() = runTest {
        val error = try {
            useCase(vehicle(displayName = ""))
            fail("Expected IllegalArgumentException for empty displayName")
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertNotNull(error)
    }

    @Test
    fun invoke_fiscalYearBefore2000_throwsIllegalArgument() = runTest {
        val error = try {
            useCase(vehicle(fiscalYear = 1999))
            fail("Expected IllegalArgumentException for fiscalYear < 2000")
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertNotNull(error)
    }

    @Test
    fun invoke_fiscalYearAfter2100_throwsIllegalArgument() = runTest {
        val error = try {
            useCase(vehicle(fiscalYear = 2101))
            fail("Expected IllegalArgumentException for fiscalYear > 2100")
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertNotNull(error)
    }

    @Test
    fun invoke_negativeOpeningOdometer_throwsIllegalArgument() = runTest {
        try {
            useCase(vehicle(openingOdometerKm = -1f))
            fail("Expected IllegalArgumentException for negative opening odometer")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Opening odometer", ignoreCase = true))
        }
    }

    @Test
    fun invoke_closingLessThanOpening_throwsIllegalArgument() = runTest {
        try {
            useCase(vehicle(openingOdometerKm = 10_000f, closingOdometerKm = 9_000f))
            fail("Expected IllegalArgumentException for closing < opening odometer")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Closing odometer", ignoreCase = true))
        }
    }

    // ── Happy path ────────────────────────────────────────────────────────────────

    @Test
    fun invoke_validVehicle_returnsPositiveId() = runTest {
        val id = useCase(vehicle())
        assertTrue("Generated id must be > 0", id > 0)
    }

    @Test
    fun invoke_validVehicle_rowPersistedInDb() = runTest {
        useCase(vehicle(displayName = "Toyota Camry"))

        val vehicles = vehicleRepo.getActiveVehicles("u1", 2026).first()
        assertEquals(1, vehicles.size)
        assertEquals("Toyota Camry", vehicles.single().displayName)
    }

    @Test
    fun invoke_twoVehicles_eachGetsDistinctId() = runTest {
        val id1 = useCase(vehicle(displayName = "Car A"))
        val id2 = useCase(vehicle(displayName = "Car B"))
        assertNotEquals(id1, id2)
    }

    @Test
    fun invoke_zeroOpeningOdometer_isValid() = runTest {
        val id = useCase(vehicle(openingOdometerKm = 0f))
        assertTrue(id > 0)
    }

    @Test
    fun invoke_equalOpeningAndClosing_isValid() = runTest {
        val id = useCase(vehicle(openingOdometerKm = 5_000f, closingOdometerKm = 5_000f))
        assertTrue(id > 0)
    }

    @Test
    fun invoke_fiscalYearBoundary2000_isValid() = runTest {
        val id = useCase(vehicle(fiscalYear = 2000))
        assertTrue(id > 0)
    }

    @Test
    fun invoke_fiscalYearBoundary2100_isValid() = runTest {
        val id = useCase(vehicle(fiscalYear = 2100))
        assertTrue(id > 0)
    }

    // ── Default promotion ─────────────────────────────────────────────────────────

    @Test
    fun invoke_setAsDefaultFalse_vehicleIsNotDefault() = runTest {
        useCase(vehicle(), setAsDefault = false)

        val saved = vehicleRepo.getActiveVehicles("u1", 2026).first().single()
        assertFalse(saved.isDefault)
    }

    @Test
    fun invoke_setAsDefaultTrue_vehicleIsDefault() = runTest {
        useCase(vehicle(), setAsDefault = true)

        val saved = vehicleRepo.getActiveVehicles("u1", 2026).first().single()
        assertTrue(saved.isDefault)
    }

    @Test
    fun invoke_setAsDefaultTrue_secondCreationPromotesNewDefault() = runTest {
        useCase(vehicle(displayName = "First"),  setAsDefault = true)
        useCase(vehicle(displayName = "Second"), setAsDefault = true)

        val defaults = vehicleRepo.getActiveVehicles("u1", 2026).first()
            .filter { it.isDefault }

        assertEquals("Exactly one default vehicle", 1, defaults.size)
        assertEquals("Second", defaults.single().displayName)
    }

    @Test
    fun invoke_setAsDefaultFalse_defaultVehicleFromEarlierIsPreserved() = runTest {
        useCase(vehicle(displayName = "First"),  setAsDefault = true)
        useCase(vehicle(displayName = "Second"), setAsDefault = false)

        val defaults = vehicleRepo.getActiveVehicles("u1", 2026).first()
            .filter { it.isDefault }

        assertEquals(1, defaults.size)
        assertEquals("First", defaults.single().displayName)
    }
}
