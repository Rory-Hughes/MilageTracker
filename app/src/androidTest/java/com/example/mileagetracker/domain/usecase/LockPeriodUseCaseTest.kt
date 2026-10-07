package com.example.mileagetracker.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.repository.ApprovalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [LockPeriodUseCase] against a real (in-memory) Room DB.
 *
 * ## Behaviour under test
 * - **Monthly lock**: locking year=2026, month=3 → `isLocked(2026, 3)` returns true.
 * - **Annual lock**: locking year=2026, month=null → any month check for 2026 returns true.
 * - **Already locked**: second lock on the same period → `Result.failure(IllegalStateException)`.
 * - **Isolation**: locking 2026/3 does not affect 2026/4 or 2025/3.
 * - **Persistence**: `lockedByUserId` and `lockedAt` stored as supplied.
 * - **`lockedAt` timestamp**: bracketed by `System.currentTimeMillis()` calls around the invoke.
 */
@RunWith(AndroidJUnit4::class)
class LockPeriodUseCaseTest {

    private lateinit var db:         MileageDatabase
    private lateinit var repository: ApprovalRepository
    private lateinit var useCase:    LockPeriodUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = ApprovalRepository(db.approvalRequestDao(), db.periodLockDao())
        useCase    = LockPeriodUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Monthly lock ───────────────────────────────────────────────────────────

    @Test
    fun invoke_monthlyLock_returnsSuccess() = runTest {
        val result = useCase(year = 2026, month = 3, lockedByUserId = 1L)
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_monthlyLock_returnedIdIsPositive() = runTest {
        val result = useCase(year = 2026, month = 3, lockedByUserId = 1L)
        assertTrue(result.getOrThrow() > 0L)
    }

    @Test
    fun invoke_monthlyLock_isLockedReturnsTrueForThatMonth() = runTest {
        useCase(year = 2026, month = 3, lockedByUserId = 1L)
        assertTrue(repository.isLocked(2026, 3))
    }

    @Test
    fun invoke_monthlyLock_doesNotLockOtherMonthsInSameYear() = runTest {
        useCase(year = 2026, month = 3, lockedByUserId = 1L)
        assertFalse(repository.isLocked(2026, 4))
        assertFalse(repository.isLocked(2026, 2))
    }

    @Test
    fun invoke_monthlyLock_doesNotLockSameMonthInOtherYear() = runTest {
        useCase(year = 2026, month = 3, lockedByUserId = 1L)
        assertFalse(repository.isLocked(2025, 3))
        assertFalse(repository.isLocked(2027, 3))
    }

    // ── Annual lock (month = null) ─────────────────────────────────────────────

    @Test
    fun invoke_annualLock_blocksEveryMonthInThatYear() = runTest {
        useCase(year = 2025, month = null, lockedByUserId = 2L)

        for (month in 1..12) {
            assertTrue(
                "Expected month $month to be locked after annual lock",
                repository.isLocked(2025, month),
            )
        }
    }

    @Test
    fun invoke_annualLock_doesNotAffectOtherYears() = runTest {
        useCase(year = 2025, month = null, lockedByUserId = 2L)
        assertFalse(repository.isLocked(2024, 6))
        assertFalse(repository.isLocked(2026, 1))
    }

    // ── Already locked ─────────────────────────────────────────────────────────

    @Test
    fun invoke_alreadyLockedMonth_returnsFailure() = runTest {
        useCase(year = 2026, month = 5, lockedByUserId = 1L)
        val second = useCase(year = 2026, month = 5, lockedByUserId = 2L)

        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun invoke_alreadyLockedAnnual_returnsFailure() = runTest {
        useCase(year = 2026, month = null, lockedByUserId = 1L)
        val second = useCase(year = 2026, month = null, lockedByUserId = 2L)

        assertTrue(second.isFailure)
    }

    // ── lockedByUserId persisted ───────────────────────────────────────────────

    @Test
    fun invoke_lockedByUserId_storedCorrectly() = runTest {
        useCase(year = 2026, month = 6, lockedByUserId = 99L)

        val locks = repository.observeAllLocks()
        locks.first().let { list ->
            assertEquals(1, list.size)
            assertEquals(99L, list.single().lockedByUserId)
        }
    }

    // ── lockedAt timestamp ────────────────────────────────────────────────────

    @Test
    fun invoke_lockedAt_withinTestTimeBracket() = runTest {
        val before = System.currentTimeMillis()
        useCase(year = 2026, month = 7, lockedByUserId = 1L)
        val after  = System.currentTimeMillis()

        val locks = repository.observeAllLocks()
        locks.first().let { list ->
            val lockedAt = list.single().lockedAt
            assertTrue(
                "lockedAt ($lockedAt) not in [$before, $after]",
                lockedAt in before..after,
            )
        }
    }

    // ── Independent months can be locked separately ────────────────────────────

    @Test
    fun invoke_twoSeparateMonths_eachLockedIndependently() = runTest {
        useCase(year = 2026, month = 1, lockedByUserId = 1L)
        useCase(year = 2026, month = 2, lockedByUserId = 1L)

        assertTrue(repository.isLocked(2026, 1))
        assertTrue(repository.isLocked(2026, 2))
        assertFalse(repository.isLocked(2026, 3))
    }
}
