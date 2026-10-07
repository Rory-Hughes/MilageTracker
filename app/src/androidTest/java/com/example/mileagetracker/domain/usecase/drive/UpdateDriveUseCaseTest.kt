package com.example.mileagetracker.domain.usecase.drive

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.AuditRepositoryImpl
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
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
 * Integration tests for [UpdateDriveUseCase] against a real (in-memory) Room DB.
 *
 * ## Diff-based audit mechanism
 * The use case compares the stored drive against the updated entity and writes
 * one [AuditEventType] row per changed **auditable** field:
 *
 * | Field          | Audit event type    |
 * |----------------|---------------------|
 * | `tripCategory` | `CATEGORY_CHANGED`  |
 * | `purposeText`  | `PURPOSE_UPDATED`   |
 * | `distanceKm`   | `DISTANCE_UPDATED`  |
 * | `vehicleId`    | `VEHICLE_ASSIGNED`  |
 * | `notes`        | `NOTE_UPDATED`      |
 *
 * Non-auditable fields (`classificationStatus`, `trackingSource`, `startedAt`, …)
 * are persisted but produce no audit rows.
 *
 * ## Contract tests
 * - `false` returned for unknown / soft-deleted drive id.
 * - `true` returned and drive row updated when id is valid.
 * - Zero audit rows when no auditable field changed.
 * - Exactly one audit row per changed auditable field, with correct
 *   `oldValue`, `newValue`, `actorUid`, `tripId`, and `eventType`.
 * - Multiple changes produce multiple rows in the same call.
 */
@RunWith(AndroidJUnit4::class)
class UpdateDriveUseCaseTest {

    private lateinit var db:        MileageDatabase
    private lateinit var useCase:   UpdateDriveUseCase
    private lateinit var driveRepo: DriveRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo  = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        val auditRepo = AuditRepositoryImpl(db.tripAuditDao())
        useCase    = UpdateDriveUseCase(driveRepo, auditRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun baseDrive() = DriveEntity(
        startedAt            = 1_000_000L,
        endedAt              = 2_000_000L,
        startLocation        = "Home",
        endLocation          = "Office",
        distanceKm           = 10f,
        driverUid            = "u1",
        tripCategory         = TripCategory.UNCLASSIFIED,
        classificationStatus = ClassificationStatus.PENDING,
        trackingSource       = TrackingSource.AUTO_TRACKED,
        purposeText          = null,
        vehicleId            = null,
        notes                = null,
    )

    /** Saves [entity] and returns the fully-loaded row with its generated id. */
    private suspend fun insertAndLoad(entity: DriveEntity = baseDrive()): DriveEntity {
        val id = driveRepo.saveDrive(entity)
        return driveRepo.getDriveById(id)!!
    }

    private suspend fun auditRows(tripId: Int) =
        db.tripAuditDao().getAuditRowsForTripOnce(tripId)

    // ── Not-found ──────────────────────────────────────────────────────────────

    @Test
    fun invoke_unknownDriveId_returnsFalse() = runTest {
        val result = useCase(baseDrive().copy(id = 999), actorUid = "u1")
        assertFalse(result)
    }

    @Test
    fun invoke_unknownDriveId_writesNoAuditRow() = runTest {
        useCase(baseDrive().copy(id = 999), actorUid = "u1")
        // No drives exist so no audit rows can have been written.
        assertTrue(auditRows(tripId = 999).isEmpty())
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    fun invoke_validDrive_returnsTrue() = runTest {
        val existing = insertAndLoad()
        val result   = useCase(existing.copy(distanceKm = 20f), actorUid = "u1")
        assertTrue(result)
    }

    @Test
    fun invoke_validDrive_newValuesPersistedInDb() = runTest {
        val existing = insertAndLoad()
        useCase(
            existing.copy(
                tripCategory = TripCategory.BUSINESS,
                distanceKm   = 25f,
            ),
            actorUid = "u1",
        )

        val reloaded = driveRepo.getDriveById(existing.id)!!
        assertEquals(TripCategory.BUSINESS, reloaded.tripCategory)
        assertEquals(25f, reloaded.distanceKm)
    }

    // ── Zero audit rows when no auditable field changed ────────────────────────

    @Test
    fun invoke_onlyNonAuditableFieldChanged_writesNoAuditRow() = runTest {
        val existing = insertAndLoad()
        // Changing classificationStatus, trackingSource, startedAt: none are auditable.
        useCase(
            existing.copy(
                classificationStatus = ClassificationStatus.USER_CONFIRMED,
                trackingSource       = TrackingSource.MANUAL_ENTRY,
                startedAt            = 9_999_000L,
            ),
            actorUid = "u1",
        )

        assertTrue("No audit row expected for non-auditable changes",
            auditRows(existing.id).isEmpty())
    }

    // ── Individual auditable fields ───────────────────────────────────────────

    @Test
    fun invoke_tripCategoryChanged_writesCategoryChangedRow() = runTest {
        val existing = insertAndLoad()
        useCase(
            existing.copy(tripCategory = TripCategory.BUSINESS),
            actorUid = "actor",
        )

        val rows = auditRows(existing.id)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(AuditEventType.CATEGORY_CHANGED, row.eventType)
        assertEquals("trip_category", row.fieldName)
        assertEquals(TripCategory.UNCLASSIFIED.name, row.oldValue)
        assertEquals(TripCategory.BUSINESS.name, row.newValue)
    }

    @Test
    fun invoke_purposeTextChanged_writesPurposeUpdatedRow() = runTest {
        val existing = insertAndLoad()
        useCase(
            existing.copy(purposeText = "Client site visit"),
            actorUid = "u1",
        )

        val rows = auditRows(existing.id)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(AuditEventType.PURPOSE_UPDATED, row.eventType)
        assertEquals("purpose_text", row.fieldName)
        assertNull(row.oldValue)
        assertEquals("Client site visit", row.newValue)
    }

    @Test
    fun invoke_distanceKmChanged_writesDistanceUpdatedRow() = runTest {
        val existing = insertAndLoad()
        useCase(
            existing.copy(distanceKm = 32.5f),
            actorUid = "u1",
        )

        val rows = auditRows(existing.id)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(AuditEventType.DISTANCE_UPDATED, row.eventType)
        assertEquals("distance_km", row.fieldName)
        assertEquals("10.0", row.oldValue)
        assertEquals("32.5", row.newValue)
    }

    @Test
    fun invoke_vehicleIdChanged_writesVehicleAssignedRow() = runTest {
        val existing = insertAndLoad()
        useCase(
            existing.copy(vehicleId = 7),
            actorUid = "u1",
        )

        val rows = auditRows(existing.id)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(AuditEventType.VEHICLE_ASSIGNED, row.eventType)
        assertEquals("vehicle_id", row.fieldName)
        assertNull(row.oldValue)
        assertEquals("7", row.newValue)
    }

    @Test
    fun invoke_notesChanged_writesNoteUpdatedRow() = runTest {
        val existing = insertAndLoad()
        useCase(
            existing.copy(notes = "Picked up client"),
            actorUid = "u1",
        )

        val rows = auditRows(existing.id)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(AuditEventType.NOTE_UPDATED, row.eventType)
        assertEquals("notes", row.fieldName)
        assertNull(row.oldValue)
        assertEquals("Picked up client", row.newValue)
    }

    // ── Multiple changed fields ───────────────────────────────────────────────

    @Test
    fun invoke_multiplAuditableFieldsChanged_writesOneRowPerField() = runTest {
        val existing = insertAndLoad()
        useCase(
            existing.copy(
                tripCategory = TripCategory.PERSONAL,
                distanceKm   = 50f,
                notes        = "Weekend errand",
            ),
            actorUid = "u1",
        )

        val rows = auditRows(existing.id)
        assertEquals(3, rows.size)

        val eventTypes = rows.map { it.eventType }.toSet()
        assertTrue(AuditEventType.CATEGORY_CHANGED   in eventTypes)
        assertTrue(AuditEventType.DISTANCE_UPDATED   in eventTypes)
        assertTrue(AuditEventType.NOTE_UPDATED       in eventTypes)
    }

    // ── actorUid and tripId on every audit row ────────────────────────────────

    @Test
    fun invoke_auditRows_haveCorrectActorUidAndTripId() = runTest {
        val existing = insertAndLoad()
        useCase(
            existing.copy(tripCategory = TripCategory.MEDICAL, notes = "Doctor visit"),
            actorUid = "manager-uid-42",
        )

        val rows = auditRows(existing.id)
        assertEquals(2, rows.size)
        rows.forEach { row ->
            assertEquals("manager-uid-42", row.actorUid)
            assertEquals(existing.id, row.tripId)
        }
    }

    // ── Idempotent update — no change ─────────────────────────────────────────

    @Test
    fun invoke_noFieldChanged_writesNoAuditRowAndReturnsTrue() = runTest {
        val existing = insertAndLoad()
        // Pass an identical copy.
        val result = useCase(existing, actorUid = "u1")

        assertTrue(result)
        assertTrue("No auditable field changed — expect empty audit log",
            auditRows(existing.id).isEmpty())
    }
}
