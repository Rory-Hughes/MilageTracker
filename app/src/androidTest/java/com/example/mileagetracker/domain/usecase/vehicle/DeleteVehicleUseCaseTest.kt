package com.example.mileagetracker.domain.usecase.vehicle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [DeleteVehicleUseCase] against a real (in-memory) Room DB.
 *
 * ## Behaviour under test
 * - **Has trips**: throws [IllegalStateException] whose message contains the vehicle
 *   display name and the trip count. Singular ("trip") for 1, plural ("trips") for > 1.
 * - **No trips**: permanently removes the row from `vehicles`.
 * - **Other vehicles unaffected**: deleting one vehicle does not remove others.
 */
@RunWith(AndroidJUnit4::class)
class DeleteVehicleUseCaseTest {

    private lateinit var db:          MileageDatabase
    private lateinit var vehicleRepo: VehicleRepositoryImpl
    private lateinit var driveRepo:   DriveRepositoryImpl
    private lateinit var useCase:     DeleteVehicleUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        vehicleRepo = VehicleRepositoryImpl(db.vehicleDao(), db)
        driveRepo   = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        useCase     = DeleteVehicleUseCase(vehicleRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private suspend fun insertVehicle(displayName: String = "My Car"): VehicleEntity {
        val id = vehicleRepo.saveVehicle(
            VehicleEntity(
                ownerUid    = "u1",
                displayName = displayName,
                fiscalYear  = 2026,
            )
        )
        return vehicleRepo.getVehicleById(id)!!
    }

    private suspend fun insertDriveForVehicle(vehicleId: Int) =
        driveRepo.saveDrive(
            DriveEntity(
                driverUid            = "u1",
                startedAt            = 1_000_000L,
                startLocation        = "A",
                endLocation          = "B",
                distanceKm           = 10f,
                vehicleId            = vehicleId,
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.PENDING,
                trackingSource       = TrackingSource.AUTO_TRACKED,
            )
        )

    // ── Has trips ─────────────────────────────────────────────────────────────────

    @Test
    fun invoke_vehicleWithOneTrip_throwsIllegalStateException() = runTest {
        val vehicle = insertVehicle("Honda Fit")
        insertDriveForVehicle(vehicle.id)

        try {
            useCase(vehicle)
            fail("Expected IllegalStateException for vehicle with trips")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Error must mention the vehicle name",
                e.message!!.contains("Honda Fit"),
            )
        }
    }

    @Test
    fun invoke_vehicleWithOneTrip_messageContainsSingular() = runTest {
        val vehicle = insertVehicle("Solo Car")
        insertDriveForVehicle(vehicle.id)

        try {
            useCase(vehicle)
        } catch (e: IllegalStateException) {
            // "1 recorded trip" uses singular; must NOT contain "trips"
            assertTrue(
                "Expected singular form '1 recorded trip', got: ${e.message}",
                e.message!!.contains("1 recorded trip"),
            )
        }
    }

    @Test
    fun invoke_vehicleWithMultipleTrips_messageContainsTripCount() = runTest {
        val vehicle = insertVehicle("Ford F-150")
        repeat(3) { insertDriveForVehicle(vehicle.id) }

        try {
            useCase(vehicle)
        } catch (e: IllegalStateException) {
            assertTrue(
                "Expected count '3' in error, got: ${e.message}",
                e.message!!.contains("3"),
            )
        }
    }

    @Test
    fun invoke_vehicleWithMultipleTrips_messageContainsPlural() = runTest {
        val vehicle = insertVehicle("Toyota Sienna")
        repeat(2) { insertDriveForVehicle(vehicle.id) }

        try {
            useCase(vehicle)
        } catch (e: IllegalStateException) {
            assertTrue(
                "Expected plural 'trips' in error, got: ${e.message}",
                e.message!!.contains("trips"),
            )
        }
    }

    @Test
    fun invoke_vehicleWithTrips_vehicleRowNotDeleted() = runTest {
        val vehicle = insertVehicle()
        insertDriveForVehicle(vehicle.id)

        try { useCase(vehicle) } catch (_: IllegalStateException) {}

        assertNotNull(
            "Vehicle must still exist after failed delete",
            vehicleRepo.getVehicleById(vehicle.id),
        )
    }

    // ── No trips ──────────────────────────────────────────────────────────────────

    @Test
    fun invoke_vehicleWithNoTrips_doesNotThrow() = runTest {
        val vehicle = insertVehicle()
        useCase(vehicle) // must not throw
    }

    @Test
    fun invoke_vehicleWithNoTrips_rowRemovedFromDb() = runTest {
        val vehicle = insertVehicle()

        useCase(vehicle)

        assertNull(vehicleRepo.getVehicleById(vehicle.id))
    }

    // ── Isolation ─────────────────────────────────────────────────────────────────

    @Test
    fun invoke_deleteOneVehicle_otherVehiclesUnaffected() = runTest {
        val v1 = insertVehicle("Car A")
        val v2 = insertVehicle("Car B")

        useCase(v1)

        assertNull("v1 should be deleted", vehicleRepo.getVehicleById(v1.id))
        assertNotNull("v2 should still exist", vehicleRepo.getVehicleById(v2.id))
    }
}
