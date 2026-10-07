package com.example.mileagetracker.domain.usecase.classification

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.data.repository.AuditRepositoryImpl
import com.example.mileagetracker.data.repository.DriveRepositoryImpl
import com.example.mileagetracker.data.repository.UserClassificationRuleRepositoryImpl
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Integration tests for [AutoClassifyPendingTripsUseCase] against a real
 * (in-memory) Room database.
 *
 * ## What is under test
 * [AutoClassifyPendingTripsUseCase] loads all UNCLASSIFIED trips for the given
 * driver via [DriveRepository.getUnclassifiedDrives] (snapshot via `Flow.first()`),
 * then processes each one sequentially through [AutoClassifyTripUseCase].
 * The returned [AutoClassifyPendingTripsUseCase.BatchResult] summarises outcomes.
 *
 * ## Query scope
 * `getUnclassifiedDrives` filters by `trip_category = 'UNCLASSIFIED'`. Therefore:
 * - Trips whose `tripCategory` is already BUSINESS/PERSONAL/COMMUTE etc. are
 *   **never loaded** into the batch, regardless of `classificationStatus`.
 * - To simulate "already-reviewed" trips being skipped, simply insert them with
 *   a non-UNCLASSIFIED `tripCategory`.
 *
 * ## Rule fixtures
 * - **WeekendPersonal** (Saturday noon, ≤ 120 km) → `Classified`
 * - **TrackingArtefact** (distance < 1 km) → `FlaggedForReview`
 * - **NoRuleMatch** (Wednesday noon, 10 km — mid-day weekday not within commute
 *   hours, not long-haul, not weekend) → `NoRuleMatched`
 *
 * ## onProgress callback
 * Called after each trip is processed with `(processedCount, totalCount)`.
 */
@RunWith(AndroidJUnit4::class)
class AutoClassifyPendingTripsUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var driveRepo: DriveRepositoryImpl
    private lateinit var useCase: AutoClassifyPendingTripsUseCase

    private val driverUid = "u1"
    private val currentUserProvider = object : CurrentUserProvider {
        override fun currentUser() = CurrentUser(driverUid, "u1@example.com", null, null)
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        val auditRepo = AuditRepositoryImpl(db.tripAuditDao())
        val ruleRepo = UserClassificationRuleRepositoryImpl(
            db.userClassificationRuleDao(),
            currentUserProvider,
        )

        val calcMock = mockk<CalculateReimbursementUseCase>(relaxed = true)
        coEvery { calcMock(any(), any(), any()) } returns ReimbursementResult(
            taxYear = 2024,
            newDistanceKm = 250f,
            ytdKmBefore = 0f,
            firstTierKm = 250,
            secondTierKm = 0,
            firstTierLimitKm = 5_000,
            firstTierRateCents = 70,
            additionalRateCents = 64,
            amountCents = 17_500,
        )

        val autoClassify = AutoClassifyTripUseCase(driveRepo, auditRepo, ruleRepo, calcMock)
        useCase = AutoClassifyPendingTripsUseCase(driveRepo, autoClassify)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────

    /** Saturday noon in the device's local timezone — WeekendPersonal fires for ≤ 120 km. */
    private val saturdayNoon: Long by lazy {
        Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 6, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Wednesday noon — mid-day weekday, no built-in rule fires for 10 km. */
    private val wednesdayNoon: Long by lazy {
        Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 3, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Inserts an UNCLASSIFIED drive (visible to the batch). */
    private suspend fun insertUnclassified(
        startedAt: Long = wednesdayNoon,
        distanceKm: Float = 10f,
        uid: String = driverUid,
        endedAt: Long = startedAt + 3_600_000L,
    ): Int = driveRepo.saveDrive(
        DriveEntity(
            startedAt = startedAt,
            endedAt = endedAt,
            distanceKm = distanceKm,
            driverUid = uid,
            startLocation = "A",
            endLocation = "B",
            tripCategory = TripCategory.UNCLASSIFIED,
            classificationStatus = ClassificationStatus.PENDING,
            trackingSource = TrackingSource.AUTO_TRACKED,
        )
    )

    // ── Empty queue ────────────────────────────────────────────────────────────

    @Test
    fun invoke_emptyPendingQueue_allCountsAreZero() = runTest {
        val result = useCase(driverUid)

        assertEquals(0, result.total)
        assertEquals(0, result.classified)
        assertEquals(0, result.flaggedReview)
        assertEquals(0, result.noRuleMatched)
        assertEquals(0, result.skipped)
        assertEquals(0, result.errors)
    }

    // ── Single trip outcomes ───────────────────────────────────────────────────

    @Test
    fun invoke_oneClassifiableTrip_classifiedCountOne() = runTest {
        // Saturday noon, 50 km → WeekendPersonal fires → Classified
        insertUnclassified(startedAt = saturdayNoon, distanceKm = 50f)

        val result = useCase(driverUid)

        assertEquals(1, result.total)
        assertEquals(1, result.classified)
        assertEquals(0, result.flaggedReview)
        assertEquals(0, result.noRuleMatched)
    }

    @Test
    fun invoke_oneArtefactTrip_flaggedReviewCountOne() = runTest {
        // Distance < 1 km → TrackingArtefact fires → FlaggedForReview
        insertUnclassified(distanceKm = 0.5f)

        val result = useCase(driverUid)

        assertEquals(1, result.total)
        assertEquals(0, result.classified)
        assertEquals(1, result.flaggedReview)
    }

    @Test
    fun invoke_oneNoMatchTrip_noRuleMatchedCountOne() = runTest {
        // Wednesday noon, 10 km → no built-in rule matches
        insertUnclassified(startedAt = wednesdayNoon, distanceKm = 10f)

        val result = useCase(driverUid)

        assertEquals(1, result.total)
        assertEquals(0, result.classified)
        assertEquals(0, result.flaggedReview)
        assertEquals(1, result.noRuleMatched)
    }

    // ── Mixed batch ────────────────────────────────────────────────────────────

    @Test
    fun invoke_mixedBatch_countsAreCorrect() = runTest {
        insertUnclassified(startedAt = saturdayNoon, distanceKm = 50f) // Classified
        insertUnclassified(distanceKm = 0.4f) // FlaggedForReview
        insertUnclassified(startedAt = wednesdayNoon, distanceKm = 10f) // NoRuleMatched

        val result = useCase(driverUid)

        assertEquals(3, result.total)
        assertEquals(1, result.classified)
        assertEquals(1, result.flaggedReview)
        assertEquals(1, result.noRuleMatched)
        assertEquals(0, result.errors)
        assertEquals(0, result.skipped)
    }

    @Test
    fun invoke_fiveUnclassifiedTrips_totalFive() = runTest {
        repeat(5) { insertUnclassified() }

        val result = useCase(driverUid)

        assertEquals(5, result.total)
    }

    // ── Driver isolation ───────────────────────────────────────────────────────

    @Test
    fun invoke_tripsForDifferentDriver_notProcessed() = runTest {
        // Trips for driver2 — should not appear in driver1's batch.
        insertUnclassified(uid = "driver-2", startedAt = saturdayNoon, distanceKm = 50f)

        val result = useCase(driverUid) // processing for "u1"

        assertEquals(0, result.total)
    }

    @Test
    fun invoke_mixedDrivers_onlyOwnTripsProcessed() = runTest {
        insertUnclassified(uid = driverUid, startedAt = saturdayNoon, distanceKm = 50f)
        insertUnclassified(uid = "driver-2", startedAt = saturdayNoon, distanceKm = 50f)

        val result = useCase(driverUid)

        assertEquals(1, result.total)
        assertEquals(1, result.classified)
    }

    // ── Non-UNCLASSIFIED trips are not in scope ────────────────────────────────

    @Test
    fun invoke_alreadyClassifiedTrip_notInQueue() = runTest {
        // A BUSINESS trip with USER_CONFIRMED — trip_category is not UNCLASSIFIED
        // so it is excluded from getUnclassifiedDrives and never reaches AutoClassifyTripUseCase.
        driveRepo.saveDrive(
            DriveEntity(
                startedAt = saturdayNoon,
                endedAt = saturdayNoon + 3_600_000L,
                distanceKm = 50f,
                driverUid = driverUid,
                startLocation = "A",
                endLocation = "B",
                tripCategory = TripCategory.BUSINESS,
                classificationStatus = ClassificationStatus.USER_CONFIRMED,
                trackingSource = TrackingSource.AUTO_TRACKED,
            )
        )

        val result = useCase(driverUid)

        assertEquals(0, result.total)
    }

    // ── DB state after batch ────────────────────────────────────────────────────

    @Test
    fun invoke_classifiableTrip_dbRowUpdatedToAutoClassified() = runTest {
        val id = insertUnclassified(startedAt = saturdayNoon, distanceKm = 50f)

        useCase(driverUid)

        val saved = driveRepo.getDriveById(id)!!
        assertEquals(TripCategory.PERSONAL, saved.tripCategory)
        assertEquals(ClassificationStatus.AUTO_CLASSIFIED, saved.classificationStatus)
    }

    @Test
    fun invoke_artefactTrip_dbRowUpdatedToNeedsReview() = runTest {
        val id = insertUnclassified(distanceKm = 0.4f)

        useCase(driverUid)

        val saved = driveRepo.getDriveById(id)!!
        assertEquals(ClassificationStatus.NEEDS_REVIEW, saved.classificationStatus)
    }

    // ── onProgress callback ───────────────────────────────────────────────────

    @Test
    fun invoke_onProgressCallback_calledForEachTrip() = runTest {
        repeat(3) { insertUnclassified() }

        val progressEvents = mutableListOf<Pair<Int, Int>>()
        useCase(driverUid) { processed, total ->
            progressEvents.add(processed to total)
        }

        assertEquals(3, progressEvents.size)
        assertEquals(1 to 3, progressEvents[0])
        assertEquals(2 to 3, progressEvents[1])
        assertEquals(3 to 3, progressEvents[2])
    }

    @Test
    fun invoke_onProgressCallback_totalMatchesPendingCount() = runTest {
        repeat(4) { insertUnclassified() }

        var lastTotal = -1
        useCase(driverUid) { _, total -> lastTotal = total }

        assertEquals(4, lastTotal)
    }
}
