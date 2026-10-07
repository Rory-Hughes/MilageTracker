package com.example.mileagetracker.domain.usecase.classification

import com.example.mileagetracker.data.local.entity.AuditEventType
import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.repository.AuditRepository
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.repository.UserClassificationRuleRepository
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoClassifyTripUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var userRuleRepository: UserClassificationRuleRepository
    private lateinit var calculateReimbursementUseCase: CalculateReimbursementUseCase
    private lateinit var useCase: AutoClassifyTripUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        auditRepository = mockk(relaxed = true)
        userRuleRepository = mockk()
        calculateReimbursementUseCase = mockk()
        useCase = AutoClassifyTripUseCase(
            driveRepository,
            auditRepository,
            userRuleRepository,
            calculateReimbursementUseCase
        )
    }

    private fun testDrive(
        id: Int = 1,
        status: ClassificationStatus = ClassificationStatus.PENDING,
        startedAt: Long = 1000L,
        distanceKm: Float = 10f
    ) = DriveEntity(
        id = id,
        startedAt = startedAt,
        endedAt = startedAt + 1000,
        startLocation = "A",
        endLocation = "B",
        distanceKm = distanceKm,
        vehicleId = 1,
        driverUid = "u1",
        tripCategory = TripCategory.UNCLASSIFIED,
        classificationStatus = status,
        trackingSource = TrackingSource.AUTO_TRACKED,
        createdAt = startedAt
    )

    @Test
    fun `returns NotFound if drive does not exist`() = runTest {
        coEvery { driveRepository.getDriveById(1) } returns null
        val result = useCase(1)
        assertEquals(AutoClassifyResult.NotFound, result)
    }

    @Test
    fun `returns Skipped if drive is already user confirmed`() = runTest {
        val drive = testDrive(status = ClassificationStatus.USER_CONFIRMED)
        coEvery { driveRepository.getDriveById(1) } returns drive
        val result = useCase(1)
        assertEquals(AutoClassifyResult.Skipped(ClassificationStatus.USER_CONFIRMED), result)
    }

    @Test
    fun `returns NoRuleMatched if no rule matches`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { userRuleRepository.getEnabledRules() } returns emptyList()

        // Mock a custom rule list that matches nothing
        val result = useCase(1, rules = emptyList())

        assertEquals(AutoClassifyResult.NoRuleMatched, result)
    }

    @Test
    fun `returns Classified and updates drive on successful match`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive
        
        val mockRule = mockk<ClassificationRule>()
        every { mockRule.name } returns "TestRule"
        every { mockRule.priority } returns 10
        every { mockRule.evaluate(drive) } returns RuleMatch(mockRule, TripCategory.BUSINESS, 0.9f)

        coEvery { calculateReimbursementUseCase(any(), any()) } returns ReimbursementResult(
            taxYear = 1970,
            newDistanceKm = 10f,
            ytdKmBefore = 0f,
            firstTierKm = 10,
            secondTierKm = 0,
            firstTierLimitKm = 5000,
            firstTierRateCents = 70,
            additionalRateCents = 64,
            amountCents = 700
        )
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        val result = useCase(1, rules = listOf(mockRule))

        assertTrue(result is AutoClassifyResult.Classified)
        val classified = result as AutoClassifyResult.Classified
        assertEquals(TripCategory.BUSINESS, classified.category)
        assertEquals(0.9f, classified.confidence)
        
        coVerify { driveRepository.updateDrive(match { 
            it.tripCategory == TripCategory.BUSINESS && 
            it.classificationStatus == ClassificationStatus.AUTO_CLASSIFIED &&
            it.reimbursementAmountSnapshot == 7.0f
        }) }
    }

    @Test
    fun `returns FlaggedForReview if match category is null`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive

        val mockRule = mockk<ClassificationRule>()
        every { mockRule.name } returns "ArtifactRule"
        every { mockRule.priority } returns 5
        every { mockRule.evaluate(drive) } returns RuleMatch(mockRule, null, 0.8f)

        coEvery { driveRepository.updateDrive(any()) } returns Unit

        val result = useCase(1, rules = listOf(mockRule))

        assertTrue(result is AutoClassifyResult.FlaggedForReview)
        assertEquals("ArtifactRule", (result as AutoClassifyResult.FlaggedForReview).ruleName)
        
        coVerify { driveRepository.updateDrive(match { 
            it.classificationStatus == ClassificationStatus.NEEDS_REVIEW
        }) }
    }

    @Test
    fun `returns NoRuleMatched if match confidence is below minimum`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive

        val mockRule = mockk<ClassificationRule>()
        every { mockRule.name } returns "WeakRule"
        every { mockRule.priority } returns 10
        every { mockRule.evaluate(drive) } returns RuleMatch(mockRule, TripCategory.BUSINESS, 0.5f)

        val result = useCase(1, rules = listOf(mockRule))

        assertEquals(AutoClassifyResult.NoRuleMatched, result)
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
    }

    @Test
    fun `returns Skipped if drive is user corrected`() = runTest {
        val drive = testDrive(status = ClassificationStatus.USER_CORRECTED)
        coEvery { driveRepository.getDriveById(1) } returns drive

        val result = useCase(1)

        assertEquals(AutoClassifyResult.Skipped(ClassificationStatus.USER_CORRECTED), result)
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
    }

    @Test
    fun `force overrides a human-confirmed status and reclassifies`() = runTest {
        val drive = testDrive(status = ClassificationStatus.USER_CONFIRMED)
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        // PERSONAL avoids the reimbursement path so the override is isolated.
        val mockRule = mockk<ClassificationRule>()
        every { mockRule.name } returns "OverrideRule"
        every { mockRule.priority } returns 10
        every { mockRule.evaluate(drive) } returns RuleMatch(mockRule, TripCategory.PERSONAL, 0.9f)

        val result = useCase(1, force = true, rules = listOf(mockRule))

        assertTrue(result is AutoClassifyResult.Classified)
        assertEquals(TripCategory.PERSONAL, (result as AutoClassifyResult.Classified).category)
        coVerify {
            driveRepository.updateDrive(match {
                it.classificationStatus == ClassificationStatus.AUTO_CLASSIFIED
            })
        }
    }

    @Test
    fun `non-business category classifies without computing reimbursement`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        val mockRule = mockk<ClassificationRule>()
        every { mockRule.name } returns "MedicalRule"
        every { mockRule.priority } returns 10
        every { mockRule.evaluate(drive) } returns RuleMatch(mockRule, TripCategory.MEDICAL, 0.8f)

        val result = useCase(1, rules = listOf(mockRule))

        assertTrue(result is AutoClassifyResult.Classified)
        assertEquals(TripCategory.MEDICAL, (result as AutoClassifyResult.Classified).category)
        // Reimbursement is only computed for BUSINESS trips.
        coVerify(exactly = 0) { calculateReimbursementUseCase(any(), any()) }
    }

    @Test
    fun `business match whose reimbursement calc fails falls back to NoRuleMatched`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive

        val mockRule = mockk<ClassificationRule>()
        every { mockRule.name } returns "BizRule"
        every { mockRule.priority } returns 10
        every { mockRule.evaluate(drive) } returns RuleMatch(mockRule, TripCategory.BUSINESS, 0.9f)

        coEvery { calculateReimbursementUseCase(any(), any()) } throws IllegalStateException("no rate")

        val result = useCase(1, rules = listOf(mockRule))

        assertEquals(AutoClassifyResult.NoRuleMatched, result)
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
    }

    @Test
    fun `records an AUTO_CLASSIFIED audit row on successful classification`() = runTest {
        val drive = testDrive()
        coEvery { driveRepository.getDriveById(1) } returns drive
        coEvery { driveRepository.updateDrive(any()) } returns Unit

        val mockRule = mockk<ClassificationRule>()
        every { mockRule.name } returns "AuditedRule"
        every { mockRule.priority } returns 10
        every { mockRule.evaluate(drive) } returns RuleMatch(mockRule, TripCategory.PERSONAL, 0.95f)

        useCase(1, actorUid = "system-x", rules = listOf(mockRule))

        coVerify {
            auditRepository.recordEvent(
                tripId = 1,
                actorUid = "system-x",
                eventType = AuditEventType.AUTO_CLASSIFIED,
                fieldName = "classification_status",
                oldValue = null,
                newValue = match { it.contains("rule=AuditedRule") && it.contains("category=PERSONAL") },
            )
        }
    }
}
