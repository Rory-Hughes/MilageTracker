package com.example.mileagetracker.domain.usecase.drive

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
import com.example.mileagetracker.data.repository.TrackingRepositoryImpl
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import com.example.mileagetracker.domain.model.RoutePoint
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
 * Integration tests for [GetTripDetailUseCase] against a real (in-memory) Room DB.
 *
 * ## Behaviour under test
 * - **Drive not found**: emits [TripDetailResult.NotFound].
 * - **Drive found, no vehicle, no route**: emits [TripDetailResult.Success] with
 *   empty route list and null vehicle.
 * - **Drive found with vehicle**: vehicle entity loaded and present in result.
 * - **Drive found with route points**: route list contains the expected points.
 * - **Drive found with vehicle and route**: both populated correctly.
 * - **Drive id field**: the returned drive's id matches the saved id.
 */
@RunWith(AndroidJUnit4::class)
class GetTripDetailUseCaseTest {

    private lateinit var db:             MileageDatabase
    private lateinit var driveRepo:      DriveRepositoryImpl
    private lateinit var trackingRepo:   TrackingRepositoryImpl
    private lateinit var vehicleRepo:    VehicleRepositoryImpl
    private lateinit var useCase:        GetTripDetailUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo    = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        trackingRepo = TrackingRepositoryImpl(db.routePointDao())
        vehicleRepo  = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase      = GetTripDetailUseCase(driveRepo, trackingRepo, vehicleRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private suspend fun insertDrive(vehicleId: Int? = null): Int =
        driveRepo.saveDrive(
            DriveEntity(
                driverUid            = "u1",
                startedAt            = 1_000_000L,
                startLocation        = "Home",
                endLocation          = "Office",
                distanceKm           = 15f,
                vehicleId            = vehicleId,
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.USER_CONFIRMED,
                trackingSource       = TrackingSource.AUTO_TRACKED,
            )
        )

    private suspend fun insertVehicle(displayName: String = "Test Car"): Int =
        vehicleRepo.saveVehicle(
            VehicleEntity(
                ownerUid    = "u1",
                displayName = displayName,
                fiscalYear  = 2026,
            )
        )

    private suspend fun insertRoutePoints(tripId: Int, count: Int = 3) {
        val points = (1..count).map { i ->
            RoutePoint(
                tripId      = tripId.toLong(),
                lat         = 43.0 + i * 0.01,
                lng         = -79.0 + i * 0.01,
                altitudeMetres = null,
                accuracyMetres = null,
                speedKph    = null,
                timestampMs = 1_000_000L + i * 10_000L,
            )
        }
        trackingRepo.saveRoutePoints(tripId.toLong(), points)
    }

    // ── Drive not found ───────────────────────────────────────────────────────────

    @Test
    fun invoke_driveNotFound_emitsNotFound() = runTest {
        val result = useCase(driveId = 9999).first()
        assertTrue(result is TripDetailResult.NotFound)
    }

    // ── Drive found, no vehicle, no route ─────────────────────────────────────────

    @Test
    fun invoke_driveExists_emitsSuccess() = runTest {
        val driveId = insertDrive()

        val result = useCase(driveId).first()

        assertTrue(result is TripDetailResult.Success)
    }

    @Test
    fun invoke_driveExists_noVehicle_vehicleIsNull() = runTest {
        val driveId = insertDrive(vehicleId = null)

        val result = useCase(driveId).first() as TripDetailResult.Success

        assertNull(result.vehicle)
    }

    @Test
    fun invoke_driveExists_noRoutePoints_routeIsEmpty() = runTest {
        val driveId = insertDrive()

        val result = useCase(driveId).first() as TripDetailResult.Success

        assertTrue(result.route.isEmpty())
    }

    @Test
    fun invoke_driveExists_driveIdMatchesSaved() = runTest {
        val driveId = insertDrive()

        val result = useCase(driveId).first() as TripDetailResult.Success

        assertEquals(driveId, result.drive.id)
    }

    @Test
    fun invoke_driveExists_driveFieldsMatchSaved() = runTest {
        val driveId = insertDrive()

        val result = useCase(driveId).first() as TripDetailResult.Success

        assertEquals("Office", result.drive.endLocation)
        assertEquals(15f,      result.drive.distanceKm)
    }

    // ── Drive with vehicle ─────────────────────────────────────────────────────────

    @Test
    fun invoke_driveWithVehicle_vehiclePopulatedInResult() = runTest {
        val vehicleId = insertVehicle("Honda Civic")
        val driveId   = insertDrive(vehicleId = vehicleId)

        val result = useCase(driveId).first() as TripDetailResult.Success

        assertNotNull(result.vehicle)
        assertEquals("Honda Civic", result.vehicle!!.displayName)
    }

    @Test
    fun invoke_driveWithVehicle_vehicleIdMatchesDriveFk() = runTest {
        val vehicleId = insertVehicle()
        val driveId   = insertDrive(vehicleId = vehicleId)

        val result = useCase(driveId).first() as TripDetailResult.Success

        assertEquals(vehicleId, result.vehicle!!.id)
    }

    // ── Drive with route points ────────────────────────────────────────────────────

    @Test
    fun invoke_driveWithRoutePoints_routePopulatedInResult() = runTest {
        val driveId = insertDrive()
        insertRoutePoints(driveId, count = 3)

        val result = useCase(driveId).first() as TripDetailResult.Success

        assertEquals(3, result.route.size)
    }

    @Test
    fun invoke_driveWithRoutePoints_firstPointLatCorrect() = runTest {
        val driveId = insertDrive()
        insertRoutePoints(driveId, count = 1)

        val result = useCase(driveId).first() as TripDetailResult.Success

        assertEquals(43.01, result.route.single().lat, 0.0001)
    }

    // ── Drive with both vehicle and route ─────────────────────────────────────────

    @Test
    fun invoke_driveWithVehicleAndRoute_bothPopulated() = runTest {
        val vehicleId = insertVehicle("Ford F-150")
        val driveId   = insertDrive(vehicleId = vehicleId)
        insertRoutePoints(driveId, count = 5)

        val result = useCase(driveId).first() as TripDetailResult.Success

        assertNotNull(result.vehicle)
        assertEquals(5, result.route.size)
        assertEquals("Ford F-150", result.vehicle!!.displayName)
    }

    // ── Two drives: correct isolation ─────────────────────────────────────────────

    @Test
    fun invoke_twoDrives_eachReturnsItsOwnData() = runTest {
        val id1 = insertDrive()
        val id2 = insertDrive()
        insertRoutePoints(id1, count = 2)
        insertRoutePoints(id2, count = 4)

        val r1 = useCase(id1).first() as TripDetailResult.Success
        val r2 = useCase(id2).first() as TripDetailResult.Success

        assertEquals(2, r1.route.size)
        assertEquals(4, r2.route.size)
        assertEquals(id1, r1.drive.id)
        assertEquals(id2, r2.drive.id)
    }
}
