package com.example.mileagetracker.domain.usecase.drive

import com.example.mileagetracker.data.local.entity.ClassificationStatus
import com.example.mileagetracker.data.local.entity.DriveEntity
import com.example.mileagetracker.data.local.entity.TrackingSource
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.auth.CurrentUserProvider
import com.example.mileagetracker.domain.model.CraRateRegionRequiredException
import com.example.mileagetracker.domain.repository.DriveRepository
import com.example.mileagetracker.domain.usecase.rate.CalculateReimbursementUseCase
import com.example.mileagetracker.domain.usecase.rate.ReimbursementResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackfillReimbursementSnapshotsUseCaseTest {

    private lateinit var driveRepository: DriveRepository
    private lateinit var calculateReimbursementUseCase: CalculateReimbursementUseCase
    private lateinit var currentUserProvider: CurrentUserProvider
    private lateinit var useCase: BackfillReimbursementSnapshotsUseCase

    @Before
    fun setUp() {
        driveRepository = mockk()
        calculateReimbursementUseCase = mockk()
        currentUserProvider = mockk()

        every { currentUserProvider.requireUid() } returns DRIVER_UID
        coEvery { driveRepository.updateDrive(any()) } just Runs

        useCase = BackfillReimbursementSnapshotsUseCase(
            driveRepository,
            calculateReimbursementUseCase,
            currentUserProvider,
        )
    }

    @Test
    fun `buggy BUSINESS drive is recomputed and written back`() = runTest {
        // Pre-fix swipe path stored total dollars in appliedRateSnapshot
        // and never set reimbursementAmountSnapshot. Simulate that here.
        val buggy = drive(
            id = 1,
            km = 40f,
            category = TripCategory.BUSINESS,
            appliedRateSnapshot = 29.20f,      // ← total dollars (wrong)
            reimbursementAmountSnapshot = null, // ← never set (wrong)
        )
        coEvery {
            driveRepository.getDrivesForAnnualSummary(DRIVER_UID, any(), any())
        } returns listOf(buggy)
        coEvery {
            calculateReimbursementUseCase(any(), 40f, 0f)
        } returns reimbursementResult(amountCents = 2920, firstTierRateCents = 73)

        val captured = slot<DriveEntity>()
        coEvery { driveRepository.updateDrive(capture(captured)) } just Runs

        val result = useCase()

        assertTrue(result is BackfillReimbursementSnapshotsUseCase.Result.Completed)
        result as BackfillReimbursementSnapshotsUseCase.Result.Completed
        assertEquals(1, result.scanned)
        assertEquals(1, result.updated)
        assertEquals(0.73f, captured.captured.appliedRateSnapshot, 0.0001f)
        assertEquals(29.20f, captured.captured.reimbursementAmountSnapshot!!, 0.0001f)
    }

    @Test
    fun `already-correct drive is left untouched`() = runTest {
        val good = drive(
            id = 1,
            km = 40f,
            category = TripCategory.BUSINESS,
            appliedRateSnapshot = 0.73f,
            reimbursementAmountSnapshot = 29.20f,
        )
        coEvery {
            driveRepository.getDrivesForAnnualSummary(DRIVER_UID, any(), any())
        } returns listOf(good)
        coEvery {
            calculateReimbursementUseCase(any(), 40f, 0f)
        } returns reimbursementResult(amountCents = 2920, firstTierRateCents = 73)

        val result = useCase()

        assertTrue(result is BackfillReimbursementSnapshotsUseCase.Result.Completed)
        assertEquals(0, (result as BackfillReimbursementSnapshotsUseCase.Result.Completed).updated)
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
    }

    @Test
    fun `MEDICAL drive is zeroed without invoking calculator`() = runTest {
        val medical = drive(
            id = 1,
            km = 10f,
            category = TripCategory.MEDICAL,
            appliedRateSnapshot = 5f,           // some leftover wrong value
            reimbursementAmountSnapshot = 5f,
        )
        coEvery {
            driveRepository.getDrivesForAnnualSummary(DRIVER_UID, any(), any())
        } returns listOf(medical)

        val captured = slot<DriveEntity>()
        coEvery { driveRepository.updateDrive(capture(captured)) } just Runs

        val result = useCase()

        assertTrue(result is BackfillReimbursementSnapshotsUseCase.Result.Completed)
        assertEquals(0f, captured.captured.appliedRateSnapshot, 0.0001f)
        assertEquals(null, captured.captured.reimbursementAmountSnapshot)
        coVerify(exactly = 0) { calculateReimbursementUseCase(any(), any(), any()) }
    }

    @Test
    fun `PERSONAL and deleted drives are skipped entirely`() = runTest {
        val business = drive(id = 1, km = 10f, category = TripCategory.BUSINESS)
        val personal = drive(id = 2, km = 5f, category = TripCategory.PERSONAL)
        val deletedBusiness = drive(
            id = 3,
            km = 7f,
            category = TripCategory.BUSINESS,
            deletedAt = 999L,
        )
        coEvery {
            driveRepository.getDrivesForAnnualSummary(DRIVER_UID, any(), any())
        } returns listOf(business, personal, deletedBusiness)
        coEvery {
            calculateReimbursementUseCase(any(), 10f, 0f)
        } returns reimbursementResult(amountCents = 730, firstTierRateCents = 73)

        val result = useCase()

        assertTrue(result is BackfillReimbursementSnapshotsUseCase.Result.Completed)
        assertEquals(1, (result as BackfillReimbursementSnapshotsUseCase.Result.Completed).scanned)
        coVerify(exactly = 1) { calculateReimbursementUseCase(any(), 10f, 0f) }
    }

    @Test
    fun `running YTD km drives the two-tier formula for subsequent BUSINESS drives`() = runTest {
        val first = drive(id = 1, km = 30f, category = TripCategory.BUSINESS, startedAt = 1_000L)
        val second = drive(id = 2, km = 50f, category = TripCategory.BUSINESS, startedAt = 2_000L)
        coEvery {
            driveRepository.getDrivesForAnnualSummary(DRIVER_UID, any(), any())
        } returns listOf(second, first) // intentionally out of order; use case must sort
        coEvery {
            calculateReimbursementUseCase(any(), 30f, 0f)
        } returns reimbursementResult(amountCents = 2190, firstTierRateCents = 73)
        coEvery {
            calculateReimbursementUseCase(any(), 50f, 30f)
        } returns reimbursementResult(amountCents = 3650, firstTierRateCents = 73)

        useCase()

        coVerify { calculateReimbursementUseCase(any(), 30f, 0f) }
        coVerify { calculateReimbursementUseCase(any(), 50f, 30f) }
    }

    @Test
    fun `Skipped when region is missing`() = runTest {
        val business = drive(id = 1, km = 10f, category = TripCategory.BUSINESS)
        coEvery {
            driveRepository.getDrivesForAnnualSummary(DRIVER_UID, any(), any())
        } returns listOf(business)
        coEvery {
            calculateReimbursementUseCase(any(), any(), any())
        } throws CraRateRegionRequiredException()

        val result = useCase()

        assertTrue(result is BackfillReimbursementSnapshotsUseCase.Result.Skipped)
        coVerify(exactly = 0) { driveRepository.updateDrive(any()) }
    }

    @Test
    fun `Skipped when no signed-in user`() = runTest {
        every { currentUserProvider.requireUid() } throws IllegalStateException("Not signed in")

        val result = useCase()

        assertTrue(result is BackfillReimbursementSnapshotsUseCase.Result.Skipped)
        coVerify(exactly = 0) { driveRepository.getDrivesForAnnualSummary(any(), any(), any()) }
    }

    private fun drive(
        id: Int,
        km: Float,
        category: TripCategory,
        startedAt: Long = 1_000L * id,
        appliedRateSnapshot: Float = 0f,
        reimbursementAmountSnapshot: Float? = null,
        deletedAt: Long? = null,
    ) = DriveEntity(
        id = id,
        startedAt = startedAt,
        endedAt = startedAt + 1_000L,
        startLocation = "A",
        endLocation = "B",
        distanceKm = km,
        driverUid = DRIVER_UID,
        tripCategory = category,
        classificationStatus = ClassificationStatus.USER_CONFIRMED,
        trackingSource = TrackingSource.MANUAL_ENTRY,
        appliedRateSnapshot = appliedRateSnapshot,
        reimbursementAmountSnapshot = reimbursementAmountSnapshot,
        deletedAt = deletedAt,
        createdAt = 0L,
    )

    private fun reimbursementResult(
        amountCents: Int,
        firstTierRateCents: Int,
    ) = ReimbursementResult(
        taxYear = 2026,
        newDistanceKm = 0f,
        ytdKmBefore = 0f,
        firstTierKm = 0,
        secondTierKm = 0,
        firstTierLimitKm = 5_000,
        firstTierRateCents = firstTierRateCents,
        additionalRateCents = firstTierRateCents,
        amountCents = amountCents,
    )

    private companion object {
        const val DRIVER_UID = "driver-1"
    }
}
