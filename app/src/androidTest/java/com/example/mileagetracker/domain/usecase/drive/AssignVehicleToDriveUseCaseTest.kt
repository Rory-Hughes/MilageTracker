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
import com.example.mileagetracker.data.repository.AuditRepositoryImpl
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
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
 * Integration tests for [AssignVehicleToDriveUseCase] against a real (in-memory) Room DB.
 *
 * ## Behaviour under test
 * - **Drive not found**: returns `false`.
 * - **Vehicle not found** (non-null vehicleId): throws [IllegalArgumentException].
 * - **No-op** (same vehicle already assigned): returns `true`, writes no audit row.
 * - **Happy path** (new or changed vehicle): updates the drive row, writes a
 *   `VEHICLE_ASSIGNED` audit row with old/new vehicle IDs.
 * - **Clear vehicle** (assign `null`): sets `vehicleId` to `null`, audit row
 *   reflects old vehicleId → null.
 */
@RunWith(AndroidJUnit4::class)
class AssignVehicleToDriveUseCaseTest {

    private lateinit var db:          MileageDatabase
    private lateinit var driveRepo:   DriveRepositoryImpl
    private lateinit var vehicleRepo: VehicleRepositoryImpl
    private lateinit var auditRepo:   AuditRepositoryImpl
    private lateinit var useCase:     AssignVehicleToDriveUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo   = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        vehicleRepo = VehicleRepositoryImpl(db.vehicleDao(), db)
        auditRepo   = AuditRepositoryImpl(db.tripAuditDao())
        useCase     = AssignVehicleToDriveUseCase(driveRepo, vehicleRepo, auditRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private suspend fun insertVehicle(displayName: String = "My Car"): Int =
        vehicleRepo.saveVehicle(
            VehicleEntity(
                ownerUid    = "u1",
                displayName = displayName,
                fiscalYear  = 2026,
            )
        )

    private suspend fun insertDrive(vehicleId: Int? = null): Int =
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

    // ── Drive not found ───────────────────────────────────────────────────────────

    @Test
    fun invoke_driveNotFound_returnsFalse() = runTest {
        val result = useCase(driveId = 999, vehicleId = null, actorUid = "u1")
        assertFalse(result)
    }

    // ── Vehicle validation ────────────────────────────────────────────────────────

    @Test
    fun invoke_vehicleIdNotFound_throwsIllegalArgument() = runTest {
        val driveId = insertDrive()
        try {
            useCase(driveId = driveId, vehicleId = 9999, actorUid = "u1")
            fail("Expected IllegalArgumentException for non-existent vehicleId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("not found", ignoreCase = true))
        }
    }

    // ── No-op guard ───────────────────────────────────────────────────────────────

    @Test
    fun invoke_sameVehicleAlreadyAssigned_returnsTrueWithNoAuditRow() = runTest {
        val vehicleId = insertVehicle()
        val driveId   = insertDrive(vehicleId = vehicleId)

        val result = useCase(driveId = driveId, vehicleId = vehicleId, actorUid = "u1")

        assertTrue(result)
        assertTrue(
            "No audit row expected for no-op assignment",
            db.tripAuditDao().getAuditRowsForTripOnce(driveId).isEmpty(),
        )
    }

    @Test
    fun invoke_bothNullVehicle_returnsTrueWithNoAuditRow() = runTest {
        val driveId = insertDrive(vehicleId = null)

        val result = useCase(driveId = driveId, vehicleId = null, actorUid = "u1")

        assertTrue(result)
        assertTrue(db.tripAuditDao().getAuditRowsForTripOnce(driveId).isEmpty())
    }

    // ── Happy path: assign vehicle ────────────────────────────────────────────────

    @Test
    fun invoke_assignVehicle_returnsTrue() = runTest {
        val vehicleId = insertVehicle()
        val driveId   = insertDrive()

        val result = useCase(driveId = driveId, vehicleId = vehicleId, actorUid = "u1")

        assertTrue(result)
    }

    @Test
    fun invoke_assignVehicle_driveRowVehicleIdUpdated() = runTest {
        val vehicleId = insertVehicle()
        val driveId   = insertDrive()

        useCase(driveId = driveId, vehicleId = vehicleId, actorUid = "u1")

        val updated = driveRepo.getDriveById(driveId)!!
        assertEquals(vehicleId, updated.vehicleId)
    }

    @Test
    fun invoke_assignVehicle_writesExactlyOneAuditRow() = runTest {
        val vehicleId = insertVehicle()
        val driveId   = insertDrive()

        useCase(driveId = driveId, vehicleId = vehicleId, actorUid = "actor-uid")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(driveId)
        assertEquals(1, audit.size)
        assertEquals("actor-uid", audit.single().actorUid)
    }

    @Test
    fun invoke_assignVehicle_auditOldValueIsNullWhenNopriorVehicle() = runTest {
        val vehicleId = insertVehicle()
        val driveId   = insertDrive(vehicleId = null)

        useCase(driveId = driveId, vehicleId = vehicleId, actorUid = "u1")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(driveId).single()
        assertNull(audit.oldValue)
        assertEquals(vehicleId.toString(), audit.newValue)
    }

    @Test
    fun invoke_reassignVehicle_auditCarriesPreviousVehicleId() = runTest {
        val v1      = insertVehicle("Car A")
        val v2      = insertVehicle("Car B")
        val driveId = insertDrive(vehicleId = v1)

        useCase(driveId = driveId, vehicleId = v2, actorUid = "u1")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(driveId).single()
        assertEquals(v1.toString(), audit.oldValue)
        assertEquals(v2.toString(), audit.newValue)
    }

    // ── Clear vehicle (assign null) ───────────────────────────────────────────────

    @Test
    fun invoke_clearVehicle_setsVehicleIdToNull() = runTest {
        val vehicleId = insertVehicle()
        val driveId   = insertDrive(vehicleId = vehicleId)

        useCase(driveId = driveId, vehicleId = null, actorUid = "u1")

        assertNull(driveRepo.getDriveById(driveId)!!.vehicleId)
    }

    @Test
    fun invoke_clearVehicle_auditOldValueIsPreviousId_newValueIsNull() = runTest {
        val vehicleId = insertVehicle()
        val driveId   = insertDrive(vehicleId = vehicleId)

        useCase(driveId = driveId, vehicleId = null, actorUid = "u1")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(driveId).single()
        assertEquals(vehicleId.toString(), audit.oldValue)
        assertNull(audit.newValue)
    }
}
