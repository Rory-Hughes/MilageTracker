package com.example.mileagetracker.domain.usecase.classification

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.local.entity.UserClassificationRuleEntity
import com.example.mileagetracker.data.repository.AuditRepositoryImpl
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.UserClassificationRuleRepositoryImpl
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.UserRule
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Integration tests for [AutoClassifyTripUseCase] against a real (in-memory)
 * Room database.
 *
 * ## What is tested
 * - **NotFound** path: unknown driveId → [AutoClassifyResult.NotFound].
 * - **Skipped** path: human-set statuses (USER_CONFIRMED, USER_CORRECTED)
 *   prevent re-classification unless `force = true`.
 * - **TrackingArtefact** rule: distance < 1 km or duration < 3 min →
 *   [AutoClassifyResult.FlaggedForReview]; DB row shows [ClassificationStatus.NEEDS_REVIEW].
 * - **WeekendPersonal** rule: Saturday/Sunday trip, ≤ 120 km →
 *   [AutoClassifyResult.Classified] with [TripCategory.PERSONAL].
 * - **LongHaulWeekday** rule: weekday trip > 200 km →
 *   [AutoClassifyResult.Classified] with [TripCategory.BUSINESS].
 * - **NoRuleMatched**: weekday noon, 10 km (no built-in rule fires).
 * - **User rule**: keyword in startLocation triggers user-defined rule.
 * - **Disabled user rule**: disabled rule does not apply.
 * - **Audit row**: [AuditEventType.AUTO_CLASSIFIED] written after every
 *   successful classification.
 * - **Explicit rules param**: supplying a rule list bypasses the user-rule
 *   repository entirely.
 *
 * ## Timezone handling
 * Day-of-week checks use `Calendar.getInstance().set(year, month, day, ...)`,
 * which produces timestamps in the device's local timezone.  The rule engine
 * converts epoch millis back to Calendar via the same default timezone, so
 * the DOW is always consistent regardless of where the test runs.
 */
@RunWith(AndroidJUnit4::class)
class AutoClassifyTripUseCaseTest {
    private companion object {
        const val OWNER_UID = "u1"
    }

    private lateinit var db:       MileageDatabase
    private lateinit var useCase:  AutoClassifyTripUseCase
    private lateinit var driveRepo: DriveRepositoryImpl
    private lateinit var calcMock: CalculateReimbursementUseCase
    private val currentUserProvider = object : CurrentUserProvider {
        override fun currentUser() = CurrentUser(OWNER_UID, "u1@example.com", null, null)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        val auditRepo = AuditRepositoryImpl(db.tripAuditDao())
        val ruleRepo  = UserClassificationRuleRepositoryImpl(
            db.userClassificationRuleDao(),
            currentUserProvider,
        )

        // Mock CalculateReimbursementUseCase.  It is only called for BUSINESS-category
        // classifications (LongHaulWeekday, priority 40).  For all other rules it is
        // never invoked, so a relaxed stub is safe for the non-BUSINESS tests.
        calcMock = mockk(relaxed = true)
        coEvery { calcMock(any(), any(), any()) } returns dummyReimbursementResult

        useCase = AutoClassifyTripUseCase(driveRepo, auditRepo, ruleRepo, calcMock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    /** Weekday (Wednesday) at noon in the device's local timezone. */
    private val wednesdayNoon: Long by lazy {
        Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 3, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Saturday at noon in the device's local timezone. */
    private val saturdayNoon: Long by lazy {
        Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 6, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private val dummyReimbursementResult = ReimbursementResult(
        taxYear             = 2024,
        newDistanceKm       = 250f,
        ytdKmBefore         = 0f,
        firstTierKm         = 250,
        secondTierKm        = 0,
        firstTierLimitKm    = 5_000,
        firstTierRateCents  = 70,
        additionalRateCents = 64,
        amountCents         = 17_500,
    )

    private suspend fun insertDrive(
        startedAt:            Long               = wednesdayNoon,
        endedAt:              Long?              = startedAt + 3_600_000L,
        distanceKm:           Float              = 10f,
        driverUid:            String             = "u1",
        classificationStatus: ClassificationStatus = ClassificationStatus.PENDING,
        tripCategory:         TripCategory       = TripCategory.UNCLASSIFIED,
        startLocation:        String             = "Home",
        purposeText:          String?            = null,
    ): Int = driveRepo.saveDrive(
        DriveEntity(
            startedAt            = startedAt,
            endedAt              = endedAt,
            distanceKm           = distanceKm,
            driverUid            = driverUid,
            startLocation        = startLocation,
            endLocation          = "Office",
            tripCategory         = tripCategory,
            classificationStatus = classificationStatus,
            trackingSource       = TrackingSource.AUTO_TRACKED,
            purposeText          = purposeText,
        )
    )

    // ── NotFound ───────────────────────────────────────────────────────────────

    @Test
    fun invoke_unknownDriveId_returnsNotFound() = runTest {
        val result = useCase(driveId = 999)
        assertEquals(AutoClassifyResult.NotFound, result)
    }

    // ── Skipped — human statuses ───────────────────────────────────────────────

    @Test
    fun invoke_userConfirmedStatus_returnsSkipped() = runTest {
        val id = insertDrive(classificationStatus = ClassificationStatus.USER_CONFIRMED)

        val result = useCase(driveId = id)

        assertTrue(result is AutoClassifyResult.Skipped)
        assertEquals(
            ClassificationStatus.USER_CONFIRMED,
            (result as AutoClassifyResult.Skipped).existingStatus,
        )
    }

    @Test
    fun invoke_userCorrectedStatus_returnsSkipped() = runTest {
        val id = insertDrive(classificationStatus = ClassificationStatus.USER_CORRECTED)

        val result = useCase(driveId = id)

        assertTrue(result is AutoClassifyResult.Skipped)
    }

    @Test
    fun invoke_force_overridesUserConfirmedStatus() = runTest {
        // USER_CONFIRMED on a Saturday trip: with force=true, WeekendPersonal fires.
        val id = insertDrive(
            startedAt            = saturdayNoon,
            endedAt              = saturdayNoon + 3_600_000L,
            distanceKm           = 50f,
            classificationStatus = ClassificationStatus.USER_CONFIRMED,
        )

        val result = useCase(driveId = id, force = true)

        assertTrue("Expected Classified but got $result", result is AutoClassifyResult.Classified)
        assertEquals(TripCategory.PERSONAL, (result as AutoClassifyResult.Classified).category)
    }

    // ── TrackingArtefact rule ──────────────────────────────────────────────────

    @Test
    fun invoke_distanceLessThan1km_flagsForReview() = runTest {
        val id = insertDrive(distanceKm = 0.5f)

        val result = useCase(driveId = id)

        assertTrue("Expected FlaggedForReview but got $result",
            result is AutoClassifyResult.FlaggedForReview)
        assertEquals("TrackingArtefact",
            (result as AutoClassifyResult.FlaggedForReview).ruleName)

        // DB row must be updated to NEEDS_REVIEW.
        val saved = driveRepo.getDriveById(id)!!
        assertEquals(ClassificationStatus.NEEDS_REVIEW, saved.classificationStatus)
    }

    @Test
    fun invoke_durationUnder3Minutes_flagsForReview() = runTest {
        // 5 km distance (≥ 1 km), but duration = 2 min < 3 min → artefact.
        val start = wednesdayNoon
        val id    = insertDrive(
            distanceKm = 5f,
            startedAt  = start,
            endedAt    = start + 120_000L,  // 2 minutes
        )

        val result = useCase(driveId = id)

        assertTrue(result is AutoClassifyResult.FlaggedForReview)
    }

    // ── WeekendPersonal rule ───────────────────────────────────────────────────

    @Test
    fun invoke_saturdayTrip_50km_classifiesAsPersonal() = runTest {
        val id = insertDrive(
            startedAt  = saturdayNoon,
            endedAt    = saturdayNoon + 3_600_000L,
            distanceKm = 50f,
        )

        val result = useCase(driveId = id)

        assertTrue("Expected Classified but got $result", result is AutoClassifyResult.Classified)
        val classified = result as AutoClassifyResult.Classified
        assertEquals(TripCategory.PERSONAL, classified.category)
        assertEquals("WeekendPersonal", classified.ruleName)
        assertEquals(0.65f, classified.confidence, 0.001f)
    }

    @Test
    fun invoke_saturdayTrip_dbRowUpdatedToAutoClassified() = runTest {
        val id = insertDrive(startedAt = saturdayNoon, distanceKm = 50f)

        useCase(driveId = id)

        val saved = driveRepo.getDriveById(id)!!
        assertEquals(TripCategory.PERSONAL, saved.tripCategory)
        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, saved.classificationStatus)
    }

    // ── LongHaulWeekday rule ───────────────────────────────────────────────────

    @Test
    fun invoke_weekday250km_classifiesAsBusiness() = runTest {
        val id = insertDrive(
            startedAt  = wednesdayNoon,
            endedAt    = wednesdayNoon + 7_200_000L,
            distanceKm = 250f,
        )

        val result = useCase(driveId = id)

        assertTrue("Expected Classified but got $result", result is AutoClassifyResult.Classified)
        val classified = result as AutoClassifyResult.Classified
        assertEquals(TripCategory.BUSINESS, classified.category)
        assertEquals("LongHaulWeekday", classified.ruleName)
    }

    // ── NoRuleMatched ──────────────────────────────────────────────────────────

    @Test
    fun invoke_weekdayNoon_10km_noRuleMatched() = runTest {
        // Weekday at noon: not morning/evening commute hours, 10 km (not short
        // enough for artefact, not long enough for long-haul), not a weekend.
        val id = insertDrive(startedAt = wednesdayNoon, distanceKm = 10f)

        val result = useCase(driveId = id)

        assertEquals(AutoClassifyResult.NoRuleMatched, result)
    }

    // ── User rule keyword match ────────────────────────────────────────────────

    @Test
    fun invoke_userRuleKeyword_inStartLocation_classifiesUsingUserRule() = runTest {
        // Insert a user rule that matches "client abc" in startLocation.
        db.userClassificationRuleDao().upsert(
            UserClassificationRuleEntity(
                ownerUid   = OWNER_UID,
                name       = "Client ABC rule",
                keyword    = "client abc",
                category   = TripCategory.BUSINESS.name,
                confidence = 0.80f,
                priority   = 50,
                isEnabled  = true,
                createdAt  = 1_000L,
            )
        )

        // Drive on a weekday noon at 10 km — no built-in rule fires without keyword.
        val id = insertDrive(
            startedAt     = wednesdayNoon,
            distanceKm    = 10f,
            startLocation = "Client ABC site",
        )

        val result = useCase(driveId = id)

        assertTrue("Expected Classified but got $result", result is AutoClassifyResult.Classified)
        val classified = result as AutoClassifyResult.Classified
        assertEquals(TripCategory.BUSINESS, classified.category)
        assertTrue("Rule name should mention user rule",
            classified.ruleName.contains("Client ABC rule"))
    }

    @Test
    fun invoke_disabledUserRule_doesNotApply() = runTest {
        // Disabled user rule — should not fire; same drive returns NoRuleMatched.
        db.userClassificationRuleDao().upsert(
            UserClassificationRuleEntity(
                ownerUid   = OWNER_UID,
                name       = "Disabled rule",
                keyword    = "client abc",
                category   = TripCategory.BUSINESS.name,
                confidence = 0.80f,
                priority   = 50,
                isEnabled  = false,
                createdAt  = 1_000L,
            )
        )

        val id = insertDrive(
            startedAt     = wednesdayNoon,
            distanceKm    = 10f,
            startLocation = "Client ABC site",
        )

        val result = useCase(driveId = id)
        assertEquals(AutoClassifyResult.NoRuleMatched, result)
    }

    // ── Audit row ─────────────────────────────────────────────────────────────

    @Test
    fun invoke_classification_writesAutoClassifiedAuditRow() = runTest {
        val id = insertDrive(startedAt = saturdayNoon, distanceKm = 50f)

        useCase(driveId = id)

        val auditRows = db.tripAuditDao().getAuditRowsForTripOnce(id)
        assertEquals(1, auditRows.size)
        val row = auditRows.single()
        assertEquals(AuditEventType.AUTO_CLASSIFIED, row.eventType)
        assertEquals("classification_status", row.fieldName)
        assertNotNull(row.newValue)
        assertTrue("newValue should contain rule name",
            row.newValue!!.contains("rule=WeekendPersonal"))
    }

    @Test
    fun invoke_classification_actorUidDefaultsToSystemClassifier() = runTest {
        val id = insertDrive(startedAt = saturdayNoon, distanceKm = 50f)

        useCase(driveId = id)

        val audit = db.tripAuditDao().getAuditRowsForTripOnce(id).single()
        assertEquals(AutoClassifyTripUseCase.SYSTEM_ACTOR, audit.actorUid)
    }

    // ── Explicit rules list bypasses user rule repo ────────────────────────────

    @Test
    fun invoke_explicitRulesList_userRuleNotApplied() = runTest {
        // Insert a user rule that would otherwise fire.
        db.userClassificationRuleDao().upsert(
            UserClassificationRuleEntity(
                ownerUid   = OWNER_UID,
                name       = "Explicit bypass rule",
                keyword    = "client abc",
                category   = TripCategory.BUSINESS.name,
                confidence = 0.80f,
                priority   = 50,
                isEnabled  = true,
                createdAt  = 1_000L,
            )
        )

        val id = insertDrive(
            startedAt     = wednesdayNoon,
            distanceKm    = 10f,
            startLocation = "Client ABC site",
        )

        // Pass ClassificationRule.ALL explicitly — this excludes user rules.
        val result = useCase(driveId = id, rules = ClassificationRule.ALL)

        // No built-in rule fires for a weekday-noon 10-km trip.
        assertEquals(AutoClassifyResult.NoRuleMatched, result)
    }
}
