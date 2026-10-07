package com.example.mileagetracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.domain.model.ApprovalRequest
import com.example.mileagetracker.domain.model.ApprovalStatus
import com.example.mileagetracker.domain.model.PeriodLock
import kotlinx.coroutines.flow.first
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
 * Repository integration tests for [ApprovalRepository] against a real
 * (in-memory) SQLite database.
 *
 * [ApprovalRepository] owns two tables — `approval_requests` and
 * `period_locks` — so all operations are tested together here.
 *
 * Approval request tests verify:
 * - `insertRequest` returns the new auto-generated id.
 * - `getById`, `getForTrip`, `getForExpense` return the correct domain object.
 * - `observePending` returns only `PENDING` rows, filtered reactively.
 * - `updateRequest` persists status changes and review metadata.
 * - `getApprovedInRange` uses inclusive `reviewedAt` bounds and excludes
 *   non-APPROVED rows.
 * - `observeForDriver` returns all requests for the given `submittedByUserId`.
 *
 * Period lock tests verify:
 * - A monthly lock is detected by `isLocked(year, month)`.
 * - An **annual lock** (`periodMonth = null`) blocks `isLocked` for *any*
 *   month in that year — the DAO uses `(periodMonth = :month OR periodMonth IS NULL)`.
 * - `unlockPeriod` removes the lock.
 * - `observeAllLocks` emits mapped [PeriodLock] domain objects.
 */
@RunWith(AndroidJUnit4::class)
class ApprovalRepositoryTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: ApprovalRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = ApprovalRepository(db.approvalRequestDao(), db.periodLockDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun request(
        tripId: Long? = 1L,
        expenseId: Long? = null,
        status: ApprovalStatus = ApprovalStatus.PENDING,
        submittedAt: Long = 1_000L,
        submittedByUserId: Long = 100L,
        reviewedAt: Long? = null,
        reviewedByUserId: Long? = null,
        reviewComment: String? = null,
    ) = ApprovalRequest(
        tripId            = tripId,
        expenseId         = expenseId,
        submittedByUserId = submittedByUserId,
        submittedAt       = submittedAt,
        status            = status,
        reviewedAt        = reviewedAt,
        reviewedByUserId  = reviewedByUserId,
        reviewComment     = reviewComment,
    )

    private fun lock(
        periodYear: Int = 2026,
        periodMonth: Int? = null,
        lockedByUserId: Long = 1L,
    ) = PeriodLock(
        periodYear     = periodYear,
        periodMonth    = periodMonth,
        lockedByUserId = lockedByUserId,
        lockedAt       = 1_000L,
    )

    // ── insertRequest + getById ────────────────────────────────────────────────

    @Test
    fun insertRequest_returnsId_andGetByIdReadsItBack() = runTest {
        val id = repository.insertRequest(request(tripId = 42L))

        assertTrue(id > 0)
        val loaded = repository.getById(id)!!
        assertEquals(42L, loaded.tripId)
        assertEquals(ApprovalStatus.PENDING, loaded.status)
        assertEquals(100L, loaded.submittedByUserId)
    }

    @Test
    fun getById_returnsNull_forUnknownId() = runTest {
        assertNull(repository.getById(999L))
    }

    // ── observePending ────────────────────────────────────────────────────────

    @Test
    fun observePending_returnsOnlyPendingRequests() = runTest {
        repository.insertRequest(request(tripId = 1L, status = ApprovalStatus.PENDING))
        val approvedId = repository.insertRequest(request(tripId = 2L, status = ApprovalStatus.PENDING))
        // Transition the second request to APPROVED.
        repository.updateRequest(
            repository.getById(approvedId)!!.copy(
                status           = ApprovalStatus.APPROVED,
                reviewedAt       = 2_000L,
                reviewedByUserId = 200L,
            ),
        )

        val pending = repository.observePending().first()
        assertEquals(1, pending.size)
        assertEquals(ApprovalStatus.PENDING, pending.single().status)
        assertEquals(1L, pending.single().tripId)
    }

    @Test
    fun observePending_emitsEmpty_whenNoPendingRequests() = runTest {
        assertTrue(repository.observePending().first().isEmpty())
    }

    // ── getForTrip / getForExpense ────────────────────────────────────────────

    @Test
    fun getForTrip_returnsMostRecentRequestForThatTrip() = runTest {
        repository.insertRequest(request(tripId = 10L))

        val found = repository.getForTrip(10L)
        assertNotNull(found)
        assertEquals(10L, found!!.tripId)
    }

    @Test
    fun getForTrip_returnsNull_whenNoRequestForThatTrip() = runTest {
        repository.insertRequest(request(tripId = 5L))
        assertNull(repository.getForTrip(99L))
    }

    @Test
    fun getForExpense_returnsRequest_forMatchingExpenseId() = runTest {
        repository.insertRequest(request(tripId = null, expenseId = 55L))

        val found = repository.getForExpense(55L)
        assertNotNull(found)
        assertEquals(55L, found!!.expenseId)
        assertNull(found.tripId)
    }

    // ── updateRequest ─────────────────────────────────────────────────────────

    @Test
    fun updateRequest_persistsStatusChange_andReviewMetadata() = runTest {
        val id = repository.insertRequest(request(tripId = 7L, status = ApprovalStatus.PENDING))
        val original = repository.getById(id)!!

        repository.updateRequest(
            original.copy(
                status           = ApprovalStatus.REJECTED,
                reviewedAt       = 5_000L,
                reviewedByUserId = 200L,
                reviewComment    = "Insufficient detail",
            ),
        )

        val updated = repository.getById(id)!!
        assertEquals(ApprovalStatus.REJECTED, updated.status)
        assertEquals(5_000L, updated.reviewedAt)
        assertEquals(200L, updated.reviewedByUserId)
        assertEquals("Insufficient detail", updated.reviewComment)
    }

    // ── getApprovedInRange ────────────────────────────────────────────────────

    @Test
    fun getApprovedInRange_returnsApprovedRowsWithinInclusiveBounds() = runTest {
        // Insert as APPROVED with explicit reviewedAt timestamps.
        repository.insertRequest(request(
            tripId = 1L, status = ApprovalStatus.APPROVED,
            reviewedAt = 1_000L, reviewedByUserId = 200L,
        ))
        repository.insertRequest(request(
            tripId = 2L, status = ApprovalStatus.APPROVED,
            reviewedAt = 3_000L, reviewedByUserId = 200L,
        ))
        repository.insertRequest(request(
            tripId = 3L, status = ApprovalStatus.APPROVED,
            reviewedAt = 5_000L, reviewedByUserId = 200L,
        )) // outside range

        val inRange = repository.getApprovedInRange(fromMillis = 500L, toMillis = 3_500L)
        assertEquals(2, inRange.size)
        assertTrue(inRange.all { it.status == ApprovalStatus.APPROVED })
        assertTrue(inRange.all { it.reviewedAt!! in 500L..3_500L })
    }

    @Test
    fun getApprovedInRange_excludesNonApprovedRows() = runTest {
        // A PENDING request with null reviewedAt must never appear in approved-range results.
        repository.insertRequest(request(status = ApprovalStatus.PENDING))

        val inRange = repository.getApprovedInRange(0L, Long.MAX_VALUE)
        assertTrue(inRange.isEmpty())
    }

    // ── observeForDriver ──────────────────────────────────────────────────────

    @Test
    fun observeForDriver_returnsAllRequestsForSubmittingUser() = runTest {
        repository.insertRequest(request(tripId = 1L, submittedByUserId = 100L))
        repository.insertRequest(request(tripId = 2L, submittedByUserId = 100L))
        repository.insertRequest(request(tripId = 3L, submittedByUserId = 999L)) // different user

        val forUser = repository.observeForDriver(100L).first()
        assertEquals(2, forUser.size)
        assertTrue(forUser.all { it.submittedByUserId == 100L })
    }

    // ── lockPeriod + isLocked ─────────────────────────────────────────────────

    @Test
    fun lockPeriod_monthly_isDetectedByIsLocked() = runTest {
        repository.lockPeriod(lock(periodYear = 2026, periodMonth = 3))

        assertTrue(repository.isLocked(2026, 3))
        assertFalse(repository.isLocked(2026, 4))  // different month
        assertFalse(repository.isLocked(2025, 3))  // different year
    }

    @Test
    fun lockPeriod_annual_blocksAnyMonthInThatYear() = runTest {
        // Annual lock (periodMonth = null) uses the DAO query:
        //   WHERE periodYear = :year AND (periodMonth = :month OR periodMonth IS NULL)
        // So an annual lock matches any month query for that year.
        repository.lockPeriod(lock(periodYear = 2026, periodMonth = null))

        assertTrue("Annual lock must block Jan", repository.isLocked(2026, 1))
        assertTrue("Annual lock must block Dec", repository.isLocked(2026, 12))
        assertFalse("Different year must not be locked", repository.isLocked(2025, 6))
    }

    @Test
    fun isLocked_returnsFalse_whenNothingLocked() = runTest {
        assertFalse(repository.isLocked(2026, null))
        assertFalse(repository.isLocked(2026, 6))
    }

    // ── unlockPeriod ──────────────────────────────────────────────────────────

    @Test
    fun unlockPeriod_removesMonthlyLock() = runTest {
        repository.lockPeriod(lock(periodYear = 2026, periodMonth = 5))
        assertTrue(repository.isLocked(2026, 5))

        repository.unlockPeriod(2026, 5)

        assertFalse(repository.isLocked(2026, 5))
    }

    @Test
    fun unlockPeriod_removesAnnualLock() = runTest {
        repository.lockPeriod(lock(periodYear = 2026, periodMonth = null))
        assertTrue(repository.isLocked(2026, 1))

        repository.unlockPeriod(2026, null)

        assertFalse(repository.isLocked(2026, 1))
        assertFalse(repository.isLocked(2026, null))
    }

    // ── observeAllLocks ───────────────────────────────────────────────────────

    @Test
    fun observeAllLocks_emitsMappedDomainObjects() = runTest {
        repository.lockPeriod(lock(periodYear = 2026, periodMonth = null))
        repository.lockPeriod(lock(periodYear = 2026, periodMonth = 3))

        val locks = repository.observeAllLocks().first()
        assertEquals(2, locks.size)
        assertTrue(locks.any { it.periodMonth == null && it.periodYear == 2026 })
        assertTrue(locks.any { it.periodMonth == 3 && it.periodYear == 2026 })
        assertTrue(locks.all { it.lockedByUserId == 1L })
    }
}
