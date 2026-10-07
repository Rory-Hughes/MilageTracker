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
import com.example.mileagetracker.domain.auth.CurrentUser
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [ClassifyTripUseCase] against a real in-memory Room DB.
 *
 * The repositories and nested [UpdateDriveUseCase] are real so the tests verify
 * persisted snapshots and audit rows, while rate calculation and user identity
 * are mocked because they are external collaborators.
 */
@RunWith(AndroidJUnit4::class)
class ClassifyTripUseCaseTest {

    private lateinit var db: MileageDatabase
    private lateinit var driveRepo: DriveRepositoryImpl
    private lateinit var auditRepo: AuditRepositoryImpl
    private lateinit var calculateReimbursementUseCase: CalculateReimbursementUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var useCase: ClassifyTripUseCase

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
        useCase = ClassifyTripUseCase(
            driveRepo,
            updateDriveUseCase,
            calculateReimbursementUseCase,
            currentUserProvider,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertDrive(): Int =
        driveRepo.saveDrive(
            DriveEntity(
                driverUid = "u1",
                startedAt = 1_700_000_000_000L,
                endedAt = 1_700_000_600_000L,
                startLocation = "Home",
                endLocation = "Office",
                distanceKm = 10f,
                vehicleId = 1,
                tripCategory = TripCategory.UNCLASSIFIED,
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
    fun invoke_missingDrive_returnsFalse() = runTest {
        coEvery { calculateReimbursementUseCase(any(), any(), any()) } returns reimbursementResult()

        val result = useCase(
            driveId = 9999,
            category = TripCategory.BUSINESS,
            purposeText = "Client visit",
            actorUid = "u1",
        )

        assertFalse(result)
        assertTrue(auditRepo.getAuditRowsByDateRange(0L, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun invoke_businessTrip_persistsSnapshotsAndAuditRows() = runTest {
        val driveId = insertDrive()
        coEvery { calculateReimbursementUseCase(any(), any(), any()) } returns reimbursementResult()

        val result = useCase(
            driveId = driveId,
            category = TripCategory.BUSINESS,
            purposeText = "Client visit",
            actorUid = "u1",
        )

        assertTrue(result)
        val saved = driveRepo.getDriveById(driveId)!!
        assertEquals(TripCategory.BUSINESS, saved.tripCategory)
        assertEquals(ClassificationStatus.USER_CONFIRMED, saved.classificationStatus)
        assertEquals("Client visit", saved.purposeText)
        assertEquals(0.70f, saved.appliedRateSnapshot, 0.0001f)
        assertEquals(7.0f, saved.reimbursementAmountSnapshot!!, 0.0001f)

        val trail = auditRepo.getAuditTrailForTripOnce(driveId)
        assertTrue(trail.any { it.eventType == AuditEventType.CATEGORY_CHANGED })
        assertTrue(trail.any { it.eventType == AuditEventType.PURPOSE_UPDATED })
    }

    @Test
    fun invoke_personalTrip_usesCurrentUserAndClearsSnapshots() = runTest {
        val driveId = insertDrive()
        every { currentUserProvider.requireUid() } returns "signed-in-user"

        val result = useCase(
            driveId = driveId,
            category = TripCategory.PERSONAL,
            purposeText = null,
        )

        assertTrue(result)
        val saved = driveRepo.getDriveById(driveId)!!
        assertEquals(TripCategory.PERSONAL, saved.tripCategory)
        assertEquals(ClassificationStatus.USER_CONFIRMED, saved.classificationStatus)
        assertEquals(0f, saved.appliedRateSnapshot, 0.0001f)
        assertNull(saved.reimbursementAmountSnapshot)

        val trail = auditRepo.getAuditTrailForTripOnce(driveId)
        assertTrue(trail.any { it.actorUid == "signed-in-user" && it.eventType == AuditEventType.CATEGORY_CHANGED })
        coVerify(exactly = 0) { calculateReimbursementUseCase(any(), any(), any()) }
    }

    @Test
    fun invoke_customWithoutPurpose_throwsBeforeWrite() = runTest {
        val driveId = insertDrive()

        try {
            useCase(
                driveId = driveId,
                category = TripCategory.CUSTOM,
                purposeText = null,
                actorUid = "u1",
            )
            org.junit.Assert.fail("Expected IllegalArgumentException for missing custom purpose")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        val saved = driveRepo.getDriveById(driveId)!!
        assertEquals(TripCategory.UNCLASSIFIED, saved.tripCategory)
        assertEquals(ClassificationStatus.PENDING, saved.classificationStatus)
        assertTrue(auditRepo.getAuditTrailForTripOnce(driveId).isEmpty())
    }
}
