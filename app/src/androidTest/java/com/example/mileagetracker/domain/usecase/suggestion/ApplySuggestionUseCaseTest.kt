package com.example.mileagetracker.domain.usecase.suggestion

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
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.TripSuggestion
import com.example.mileagetracker.domain.usecase.drive.ClassifyTripUseCase
import com.example.mileagetracker.domain.usecase.drive.UpdateDriveUseCase
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [ApplySuggestionUseCase] against a real in-memory Room DB.
 *
 * The drive and audit repositories are real so the test proves persisted state,
 * while reimbursement calculation and user identity are mocked because they are
 * external collaborators rather than the focus of this flow.
 */
@RunWith(AndroidJUnit4::class)
class ApplySuggestionUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var driveRepo: DriveRepositoryImpl
    private lateinit var auditRepo: AuditRepositoryImpl
    private lateinit var calculateReimbursementUseCase: CalculateReimbursementUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var classifyTripUseCase: ClassifyTripUseCase
    private lateinit var useCase: ApplySuggestionUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        driveRepo = DriveRepositoryImpl(db.driveDao(), db.routePointDao())
        auditRepo = AuditRepositoryImpl(db.tripAuditDao())
        calculateReimbursementUseCase = mockk()
        currentUserProvider = mockk()

        val updateDriveUseCase = UpdateDriveUseCase(driveRepo, auditRepo)
        classifyTripUseCase = ClassifyTripUseCase(
            driveRepo,
            updateDriveUseCase,
            calculateReimbursementUseCase,
            currentUserProvider,
        )
        useCase = ApplySuggestionUseCase(
            driveRepo,
            auditRepo,
            classifyTripUseCase,
            currentUserProvider,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertDrive(vehicleId: Int? = null): Int =
        driveRepo.saveDrive(
            DriveEntity(
                driverUid = "u1",
                startedAt = 1_700_000_000_000L,
                endedAt = 1_700_000_600_000L,
                startLocation = "Home",
                endLocation = "Office",
                distanceKm = 10f,
                vehicleId = vehicleId,
                tripCategory = TripCategory.UNCLASSIFIED,
                purposeText = null,
                classificationStatus = ClassificationStatus.PENDING,
                trackingSource = TrackingSource.AUTO_TRACKED,
            ),
        )

    private fun reimbursementResult() = ReimbursementResult(
        taxYear = 2023,
        newDistanceKm = 10f,
        ytdKmBefore = 0f,
        firstTierKm = 10,
        secondTierKm = 0,
        firstTierLimitKm = 5000,
        firstTierRateCents = 70,
        additionalRateCents = 64,
        amountCents = 700,
    )

    @Test
    fun invoke_missingTrip_returnsFalseAndWritesNoAudit() = runTest {
        coEvery { calculateReimbursementUseCase(any(), any(), any()) } returns reimbursementResult()

        val result = useCase(
            tripId = 9999,
            suggestion = TripSuggestion(
                suggestedCategory = TripCategory.BUSINESS,
                suggestedPurpose = "Client meeting",
                suggestedVehicleId = null,
                matchConfidence = 0.85f,
                basedOnTripIds = listOf(1, 2),
            ),
            actorUid = "u1",
        )

        assertFalse(result)
        assertTrue(auditRepo.getAuditRowsByDateRange(0L, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun invoke_appliesClassificationAndWritesSuggestionAudit() = runTest {
        val driveId = insertDrive()
        coEvery { calculateReimbursementUseCase(any(), any(), any()) } returns reimbursementResult()

        val result = useCase(
            tripId = driveId,
            suggestion = TripSuggestion(
                suggestedCategory = TripCategory.BUSINESS,
                suggestedPurpose = "Client meeting",
                suggestedVehicleId = null,
                matchConfidence = 0.85f,
                basedOnTripIds = listOf(10, 11),
            ),
            actorUid = "u1",
        )

        assertTrue(result)

        val saved = driveRepo.getDriveById(driveId)!!
        assertEquals(TripCategory.BUSINESS, saved.tripCategory)
        assertEquals(ClassificationStatus.USER_CONFIRMED, saved.classificationStatus)
        assertEquals("Client meeting", saved.purposeText)
        assertEquals(0.70f, saved.appliedRateSnapshot, 0.0001f)
        assertEquals(7.0f, saved.reimbursementAmountSnapshot!!, 0.0001f)

        val trail = auditRepo.getAuditTrailForTripOnce(driveId)
        assertTrue(trail.any { it.eventType == AuditEventType.CATEGORY_CHANGED })
        assertTrue(trail.any { it.eventType == AuditEventType.PURPOSE_UPDATED })
        assertTrue(
            trail.any {
                it.eventType == AuditEventType.SUGGESTION_ACCEPTED &&
                    it.newValue == "confidence=0.85,basedOn=10|11"
            },
        )
    }

    @Test
    fun invoke_suggestedVehicle_assignsVehicleWhenTripHasNone() = runTest {
        val driveId = insertDrive(vehicleId = null)
        coEvery { calculateReimbursementUseCase(any(), any(), any()) } returns reimbursementResult()

        val result = useCase(
            tripId = driveId,
            suggestion = TripSuggestion(
                suggestedCategory = TripCategory.BUSINESS,
                suggestedPurpose = "Field visit",
                suggestedVehicleId = 42,
                matchConfidence = 0.40f,
                basedOnTripIds = listOf(8, 9),
            ),
            actorUid = "u1",
        )

        assertTrue(result)
        assertEquals(42, driveRepo.getDriveById(driveId)!!.vehicleId)
    }

    @Test
    fun invoke_existingVehicle_preservesCurrentVehicle() = runTest {
        val driveId = insertDrive(vehicleId = 7)
        coEvery { calculateReimbursementUseCase(any(), any(), any()) } returns reimbursementResult()

        val result = useCase(
            tripId = driveId,
            suggestion = TripSuggestion(
                suggestedCategory = TripCategory.BUSINESS,
                suggestedPurpose = "Field visit",
                suggestedVehicleId = 42,
                matchConfidence = 0.40f,
                basedOnTripIds = listOf(8, 9),
            ),
            actorUid = "u1",
        )

        assertTrue(result)
        assertEquals(7, driveRepo.getDriveById(driveId)!!.vehicleId)
    }
}
