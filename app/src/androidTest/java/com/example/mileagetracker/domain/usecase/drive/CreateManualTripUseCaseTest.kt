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
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [CreateManualTripUseCase] against a real (in-memory)
 * Room database.
 *
 * ## Key behaviours under test
 *
 * - **Validation**: six guards must throw [IllegalArgumentException] before
 *   any write: blank `startLocation`, blank `endLocation`, `distanceKm ≤ 0`,
 *   `startedAt = 0`, `endedAt < startedAt`, blank `driverUid`.
 * - **TrackingSource override**: the result row must always have
 *   [TrackingSource.MANUAL_ENTRY], regardless of what the draft carries.
 * - **classificationStatus**: [ClassificationStatus.USER_CONFIRMED] for any
 *   category that is not [TripCategory.UNCLASSIFIED]; [ClassificationStatus.PENDING]
 *   for [TripCategory.UNCLASSIFIED].
 * - **classificationConfidence**: always 1.0f (user-confirmed ground truth).
 * - **actorUid override**: the saved row's `driverUid` is the `actorUid`
 *   parameter, not the draft's `driverUid`.
 * - **MANUAL_ENTRY audit event**: exactly one audit row with
 *   [AuditEventType.MANUAL_ENTRY] (not `DRIVE_CREATED`).
 * - **Return value**: the generated database id (> 0).
 *
 * ## Reimbursement snapshot
 * The reimbursement computation path calls [CalculateReimbursementUseCase],
 * which internally calls [SettingsRepository.getCraRateRegion].  The mock
 * returns `null` → `CraRateRegionRequiredException` is thrown and caught by
 * the reimbursement try-catch → `appliedRateSnapshot = 0f`.  This
 * keeps the test self-contained without requiring seeded CRA rates.
 */
@RunWith(AndroidJUnit4::class)
class CreateManualTripUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var useCase: CreateManualTripUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        val driveRepo = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        val auditRepo = AuditRepositoryImpl(db.tripAuditDao())

        // Stubbed SettingsRepository: getCraRateRegion() returns null → CraRateRegionRequiredException
        // is thrown inside calculateReimbursementUseCase and caught by computeReimbursement.
        val settingsRepo = mockk<SettingsRepository>()
        every { settingsRepo.getCraRateRegion() } returns flowOf(null)

        val calcUseCase = CalculateReimbursementUseCase(
            rateRepository     = mockk<RateRepository>(relaxed = true),
            settingsRepository = settingsRepo,
        )

        useCase = CreateManualTripUseCase(driveRepo, auditRepo, calcUseCase)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun draft(
        startLocation: String = "Home",
        endLocation: String = "Office",
        distanceKm: Float = 10f,
        startedAt: Long = 1_000_000L,
        endedAt: Long? = 2_000_000L,
        driverUid: String? = "draft-uid",
        tripCategory: TripCategory = TripCategory.BUSINESS,
        classificationStatus: ClassificationStatus = ClassificationStatus.PENDING,
    ) = DriveEntity(
        startLocation        = startLocation,
        endLocation          = endLocation,
        distanceKm           = distanceKm,
        startedAt            = startedAt,
        endedAt              = endedAt,
        driverUid            = driverUid,
        tripCategory         = tripCategory,
        classificationStatus = classificationStatus,
        trackingSource       = TrackingSource.AUTO_TRACKED, // should be overridden to MANUAL_ENTRY
    )

    // ── Validation ─────────────────────────────────────────────────────────────

    @Test
    fun invoke_blankStartLocation_throwsIllegalArgument() = runTest {
        try {
            useCase(draft(startLocation = ""), actorUid = "u1")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Start location", ignoreCase = true))
        }
    }

    @Test
    fun invoke_blankEndLocation_throwsIllegalArgument() = runTest {
        try {
            useCase(draft(endLocation = "   "), actorUid = "u1")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("End location", ignoreCase = true))
        }
    }

    @Test
    fun invoke_zeroDistanceKm_throwsIllegalArgument() = runTest {
        try {
            useCase(draft(distanceKm = 0f), actorUid = "u1")
            fail("Expected IllegalArgumentException for distanceKm = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Distance", ignoreCase = true))
        }
    }

    @Test
    fun invoke_negativeDistanceKm_throwsIllegalArgument() = runTest {
        val error = try {
            useCase(draft(distanceKm = -5f), actorUid = "u1")
            fail("Expected IllegalArgumentException for negative distanceKm")
            null
        } catch (error: IllegalArgumentException) {
            error
        }
        assertNotNull(error)
    }

    @Test
    fun invoke_startedAtZero_throwsIllegalArgument() = runTest {
        try {
            useCase(draft(startedAt = 0L), actorUid = "u1")
            fail("Expected IllegalArgumentException for startedAt = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Start time", ignoreCase = true))
        }
    }

    @Test
    fun invoke_endedAtBeforeStartedAt_throwsIllegalArgument() = runTest {
        try {
            useCase(draft(startedAt = 5_000L, endedAt = 3_000L), actorUid = "u1")
            fail("Expected IllegalArgumentException for endedAt < startedAt")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("End time", ignoreCase = true))
        }
    }

    @Test
    fun invoke_blankDriverUid_throwsIllegalArgument() = runTest {
        try {
            useCase(draft(driverUid = ""), actorUid = "u1")
            fail("Expected IllegalArgumentException for blank driverUid")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("driverUid", ignoreCase = true))
        }
    }

    // ── TrackingSource override ────────────────────────────────────────────────

    @Test
    fun invoke_forcesTrackingSourceToManualEntry() = runTest {
        useCase(draft(/* trackingSource = AUTO_TRACKED in draft */), actorUid = "u1")

        val saved = db.driveDao().getAllDrives("u1").first()
        assertEquals(TrackingSource.MANUAL_ENTRY, saved.single().trackingSource)
    }

    // ── classificationStatus ──────────────────────────────────────────────────

    @Test
    fun invoke_classifiedCategory_setsUserConfirmedStatus() = runTest {
        useCase(draft(tripCategory = TripCategory.BUSINESS), actorUid = "u1")

        val saved = db.driveDao().getAllDrives("u1").first()
        assertEquals(ClassificationStatus.USER_CONFIRMED, saved.single().classificationStatus)
    }

    @Test
    fun invoke_personalCategory_setsUserConfirmedStatus() = runTest {
        useCase(draft(tripCategory = TripCategory.PERSONAL), actorUid = "u1")

        val saved = db.driveDao().getAllDrives("u1").first()
        assertEquals(ClassificationStatus.USER_CONFIRMED, saved.single().classificationStatus)
    }

    @Test
    fun invoke_unclassifiedCategory_setsPendingStatus() = runTest {
        useCase(draft(tripCategory = TripCategory.UNCLASSIFIED), actorUid = "u1")

        val saved = db.driveDao().getAllDrives("u1").first()
        assertEquals(ClassificationStatus.PENDING, saved.single().classificationStatus)
    }

    // ── classificationConfidence ──────────────────────────────────────────────

    @Test
    fun invoke_alwaysSetsClassificationConfidenceTo1() = runTest {
        useCase(draft(), actorUid = "u1")

        val saved = db.driveDao().getAllDrives("u1").first()
        assertEquals(1.0f, saved.single().classificationConfidence!!)
    }

    // ── actorUid overrides draft driverUid ─────────────────────────────────────

    @Test
    fun invoke_savedDriverUidIsActorUid_notDraftUid() = runTest {
        useCase(draft(driverUid = "draft-uid"), actorUid = "actor-uid")

        val saved = db.driveDao().getAllDrives("actor-uid").first()
        assertEquals(1, saved.size)
        assertEquals("actor-uid", saved.single().driverUid)
    }

    // ── return value ──────────────────────────────────────────────────────────

    @Test
    fun invoke_returnsPositiveGeneratedId() = runTest {
        val id = useCase(draft(), actorUid = "u1")
        assertTrue("Generated id must be > 0", id > 0)
    }

    // ── MANUAL_ENTRY audit event ──────────────────────────────────────────────

    @Test
    fun invoke_writesManualEntryAuditEvent_notDriveCreated() = runTest {
        val id = useCase(draft(), actorUid = "u1")

        val auditRows = db.tripAuditDao().getAuditRowsForTripOnce(id)
        assertEquals(1, auditRows.size)
        assertEquals(AuditEventType.MANUAL_ENTRY, auditRows.single().eventType)
    }

    @Test
    fun invoke_auditRowActorUidMatchesActorUidParam() = runTest {
        val id = useCase(draft(), actorUid = "firebase-uid-789")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(id).single()
        assertEquals("firebase-uid-789", audit.actorUid)
    }

    @Test
    fun invoke_auditRowTripIdMatchesReturnedId() = runTest {
        val id = useCase(draft(), actorUid = "u1")

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(id).single()
        assertEquals(id, audit.tripId)
    }
}
