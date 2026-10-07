package com.example.mileagetracker.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.VehicleEntity
import com.example.mileagetracker.data.repository.AuditRepositoryImpl
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.VehicleRepositoryImpl
import com.example.mileagetracker.domain.model.ExceptionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [GetTripExceptionsUseCase] against a real (in-memory)
 * Room DB.
 *
 * ## Detection rules under test (priority order)
 * 1. [ClassificationStatus.NEEDS_REVIEW] + latest audit == [AuditEventType.DUPLICATE_FLAGGED]
 *    → [ExceptionType.DUPLICATE_FLAGGED]
 * 2. [ClassificationStatus.NEEDS_REVIEW] (catch-all) → [ExceptionType.NEEDS_REVIEW]
 * 3. [ClassificationStatus.INCOMPLETE] or [isMissingRequiredFields] → [ExceptionType.INCOMPLETE_DATA]
 *    - Missing fields: purposeText blank/null, vehicleId null, distanceKm ≤ 0
 * 4. Impossible route (endedAt < startedAt) → [ExceptionType.IMPOSSIBLE_ROUTE]
 * 5. [ClassificationStatus.USER_CORRECTED] + no TRIP_REPAIRED audit → [ExceptionType.MANUAL_OVERRIDE]
 * 6. Otherwise → no exception
 *
 * ## Output ordering
 * Results sorted by [TripException.detectedAt] descending (most-recent first).
 */
@RunWith(AndroidJUnit4::class)
class GetTripExceptionsUseCaseTest {

    private lateinit var db:          MileageDatabase
    private lateinit var driveRepo:   DriveRepositoryImpl
    private lateinit var auditRepo:   AuditRepositoryImpl
    private lateinit var vehicleRepo: VehicleRepositoryImpl
    private lateinit var useCase:     GetTripExceptionsUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo   = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        auditRepo   = AuditRepositoryImpl(db.tripAuditDao())
        vehicleRepo = VehicleRepositoryImpl(db.vehicleDao(), db)
        useCase     = GetTripExceptionsUseCase(driveRepo, auditRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────

    private suspend fun vehicle(): Int = vehicleRepo.saveVehicle(
        VehicleEntity(ownerUid = "u1", displayName = "My Car", fiscalYear = 2026)
    )

    /** Fully-complete drive — satisfies every isMissingRequiredFields check → no exception. */
    private suspend fun insertCompleteDrive(
        status:    ClassificationStatus = ClassificationStatus.USER_CONFIRMED,
        startedAt: Long                 = 1_000_000L,
        endedAt:   Long?                = 2_000_000L,
        distanceKm: Float               = 10f,
    ): Int {
        val vid = vehicle()
        return driveRepo.saveDrive(
            DriveEntity(
                driverUid            = "u1",
                startedAt            = startedAt,
                endedAt              = endedAt,
                distanceKm           = distanceKm,
                purposeText          = "Client visit",
                vehicleId            = vid,
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = status,
                trackingSource       = TrackingSource.AUTO_TRACKED,
                startLocation        = "Home",
                endLocation          = "Office",
            )
        )
    }

    private suspend fun addAuditRow(tripId: Int, eventType: AuditEventType) {
        auditRepo.recordEvent(
            tripId    = tripId,
            actorUid  = "u1",
            eventType = eventType,
        )
    }

    // ── No exception ──────────────────────────────────────────────────────────────

    @Test
    fun invoke_noTrips_emitsEmptyList() = runTest {
        val exceptions = useCase("u1").first()
        assertTrue(exceptions.isEmpty())
    }

    @Test
    fun invoke_completeDrive_noException() = runTest {
        insertCompleteDrive()

        val exceptions = useCase("u1").first()

        assertTrue("Complete USER_CONFIRMED drive should produce no exception", exceptions.isEmpty())
    }

    // ── DUPLICATE_FLAGGED ─────────────────────────────────────────────────────────

    @Test
    fun invoke_needsReview_withDuplicateFlaggedAudit_returnsDuplicateFlagged() = runTest {
        val driveId = insertCompleteDrive(status = ClassificationStatus.NEEDS_REVIEW)
        addAuditRow(driveId, AuditEventType.DUPLICATE_FLAGGED)

        val exceptions = useCase("u1").first()

        assertEquals(1, exceptions.size)
        assertEquals(ExceptionType.DUPLICATE_FLAGGED, exceptions.single().exceptionType)
    }

    @Test
    fun invoke_duplicateFlagged_tripIdMatchesDrive() = runTest {
        val driveId = insertCompleteDrive(status = ClassificationStatus.NEEDS_REVIEW)
        addAuditRow(driveId, AuditEventType.DUPLICATE_FLAGGED)

        val exception = useCase("u1").first().single()

        assertEquals(driveId, exception.tripId)
    }

    // ── NEEDS_REVIEW (catch-all) ──────────────────────────────────────────────────

    @Test
    fun invoke_needsReview_noAudit_returnsNeedsReview() = runTest {
        insertCompleteDrive(status = ClassificationStatus.NEEDS_REVIEW)

        val exceptions = useCase("u1").first()

        assertEquals(ExceptionType.NEEDS_REVIEW, exceptions.single().exceptionType)
    }

    @Test
    fun invoke_needsReview_withDriveCreatedAudit_returnsNeedsReview_notDuplicateFlagged() = runTest {
        val driveId = insertCompleteDrive(status = ClassificationStatus.NEEDS_REVIEW)
        addAuditRow(driveId, AuditEventType.DRIVE_CREATED)

        val exception = useCase("u1").first().single()

        assertEquals(ExceptionType.NEEDS_REVIEW, exception.exceptionType)
    }

    // ── INCOMPLETE_DATA ───────────────────────────────────────────────────────────

    @Test
    fun invoke_incompleteStatus_returnsIncompleteData() = runTest {
        insertCompleteDrive(status = ClassificationStatus.INCOMPLETE)

        val exceptions = useCase("u1").first()

        assertEquals(ExceptionType.INCOMPLETE_DATA, exceptions.single().exceptionType)
    }

    @Test
    fun invoke_missingVehicleId_returnsIncompleteData() = runTest {
        driveRepo.saveDrive(
            DriveEntity(
                driverUid            = "u1",
                startedAt            = 1_000_000L,
                endedAt              = 2_000_000L,
                distanceKm           = 10f,
                purposeText          = "Meeting",
                vehicleId            = null,          // missing
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.USER_CONFIRMED,
                trackingSource       = TrackingSource.AUTO_TRACKED,
                startLocation        = "A",
                endLocation          = "B",
            )
        )

        val exceptions = useCase("u1").first()

        assertEquals(ExceptionType.INCOMPLETE_DATA, exceptions.single().exceptionType)
    }

    @Test
    fun invoke_blankPurposeText_returnsIncompleteData() = runTest {
        val vid = vehicle()
        driveRepo.saveDrive(
            DriveEntity(
                driverUid            = "u1",
                startedAt            = 1_000_000L,
                distanceKm           = 10f,
                purposeText          = null,          // missing
                vehicleId            = vid,
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.USER_CONFIRMED,
                trackingSource       = TrackingSource.AUTO_TRACKED,
                startLocation        = "A",
                endLocation          = "B",
            )
        )

        val exceptions = useCase("u1").first()

        assertEquals(ExceptionType.INCOMPLETE_DATA, exceptions.single().exceptionType)
    }

    // ── IMPOSSIBLE_ROUTE ──────────────────────────────────────────────────────────

    @Test
    fun invoke_endedAtBeforeStartedAt_returnsImpossibleRoute() = runTest {
        insertCompleteDrive(
            startedAt  = 5_000_000L,
            endedAt    = 3_000_000L,  // before startedAt
            distanceKm = 10f,
        )

        val exceptions = useCase("u1").first()

        assertEquals(ExceptionType.IMPOSSIBLE_ROUTE, exceptions.single().exceptionType)
    }

    // ── MANUAL_OVERRIDE ───────────────────────────────────────────────────────────

    @Test
    fun invoke_userCorrected_noTripRepairedAudit_returnsManualOverride() = runTest {
        val driveId = insertCompleteDrive(status = ClassificationStatus.USER_CORRECTED)
        addAuditRow(driveId, AuditEventType.CATEGORY_CHANGED) // latest audit is NOT TRIP_REPAIRED

        val exceptions = useCase("u1").first()

        assertEquals(ExceptionType.MANUAL_OVERRIDE, exceptions.single().exceptionType)
    }

    @Test
    fun invoke_userCorrected_withTripRepairedAudit_noException() = runTest {
        val driveId = insertCompleteDrive(status = ClassificationStatus.USER_CORRECTED)
        addAuditRow(driveId, AuditEventType.TRIP_REPAIRED)  // resolved

        val exceptions = useCase("u1").first()

        assertTrue("TRIP_REPAIRED resolves the MANUAL_OVERRIDE exception", exceptions.isEmpty())
    }

    // ── Sorting (most-recent detectedAt first) ────────────────────────────────────

    @Test
    fun invoke_twoExceptions_sortedByDetectedAtDescending() = runTest {
        // Drive A: NEEDS_REVIEW, startedAt earlier → detectedAt is startedAt (no audit trail)
        val idA = driveRepo.saveDrive(
            DriveEntity(
                driverUid            = "u1",
                startedAt            = 1_000L,
                purposeText          = "A",
                vehicleId            = vehicle(),
                distanceKm           = 5f,
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.NEEDS_REVIEW,
                trackingSource       = TrackingSource.AUTO_TRACKED,
                startLocation        = "A", endLocation = "B",
            )
        )
        // Drive B: NEEDS_REVIEW, startedAt later → detectedAt is larger
        val idB = driveRepo.saveDrive(
            DriveEntity(
                driverUid            = "u1",
                startedAt            = 2_000L,
                purposeText          = "B",
                vehicleId            = vehicle(),
                distanceKm           = 5f,
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.NEEDS_REVIEW,
                trackingSource       = TrackingSource.AUTO_TRACKED,
                startLocation        = "A", endLocation = "B",
            )
        )

        val exceptions = useCase("u1").first()

        assertEquals(2, exceptions.size)
        // B has the later startedAt so its detectedAt is larger → comes first
        assertEquals(idB, exceptions[0].tripId)
        assertEquals(idA, exceptions[1].tripId)
    }

    // ── Driver isolation ──────────────────────────────────────────────────────────

    @Test
    fun invoke_exceptionForOtherDriver_notIncluded() = runTest {
        driveRepo.saveDrive(
            DriveEntity(
                driverUid            = "other-driver",
                startedAt            = 1_000_000L,
                distanceKm           = 5f,
                tripCategory         = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.NEEDS_REVIEW,
                trackingSource       = TrackingSource.AUTO_TRACKED,
                startLocation        = "A", endLocation = "B",
            )
        )

        val exceptions = useCase("u1").first()

        assertTrue(exceptions.isEmpty())
    }
}
