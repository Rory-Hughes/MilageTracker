package com.example.mileagetracker.domain.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.repository.ApprovalRepository
import com.example.mileagetracker.domain.model.ApprovalStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for [SubmitForApprovalUseCase] against a real (in-memory) Room DB.
 *
 * ## Rules under test
 * - Exactly one of `tripId` / `expenseId` must be non-null; any other
 *   combination → `Result.failure(IllegalArgumentException)`.
 * - On success: a PENDING [ApprovalRequest] row is written, the generated id
 *   is positive, and `submittedByUserId` / `submittedAt` are stored faithfully.
 * - `submittedAt` is bracketed by `System.currentTimeMillis()` calls around
 *   the invoke.
 */
@RunWith(AndroidJUnit4::class)
class SubmitForApprovalUseCaseTest {

    private lateinit var db:         MileageDatabase
    private lateinit var repository: ApprovalRepository
    private lateinit var useCase:    SubmitForApprovalUseCase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = ApprovalRepository(db.approvalRequestDao(), db.periodLockDao())
        useCase    = SubmitForApprovalUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    @Test
    fun invoke_bothNull_returnsFailure() = runTest {
        val result = useCase(submittedByUserId = 1L, tripId = null, expenseId = null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun invoke_bothNonNull_returnsFailure() = runTest {
        val result = useCase(submittedByUserId = 1L, tripId = 10L, expenseId = 20L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun invoke_bothNull_writesNoRow() = runTest {
        useCase(submittedByUserId = 1L, tripId = null, expenseId = null)
        assertTrue(repository.observePending().first().isEmpty())
    }

    // ── Trip submission ────────────────────────────────────────────────────────

    @Test
    fun invoke_tripIdOnly_returnsSuccess() = runTest {
        val result = useCase(submittedByUserId = 1L, tripId = 5L)
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_tripIdOnly_returnedIdIsPositive() = runTest {
        val result = useCase(submittedByUserId = 1L, tripId = 5L)
        assertTrue(result.getOrThrow() > 0L)
    }

    @Test
    fun invoke_tripIdOnly_rowHasPendingStatus() = runTest {
        val id = useCase(submittedByUserId = 1L, tripId = 5L).getOrThrow()
        val saved = repository.getById(id)!!
        assertEquals(ApprovalStatus.PENDING, saved.status)
    }

    @Test
    fun invoke_tripIdOnly_tripIdPersistedAndExpenseIdIsNull() = runTest {
        val id = useCase(submittedByUserId = 1L, tripId = 42L).getOrThrow()
        val saved = repository.getById(id)!!
        assertEquals(42L, saved.tripId)
        assertNull(saved.expenseId)
    }

    // ── Expense submission ─────────────────────────────────────────────────────

    @Test
    fun invoke_expenseIdOnly_returnsSuccess() = runTest {
        val result = useCase(submittedByUserId = 1L, expenseId = 99L)
        assertTrue(result.isSuccess)
    }

    @Test
    fun invoke_expenseIdOnly_expenseIdPersistedAndTripIdIsNull() = runTest {
        val id = useCase(submittedByUserId = 1L, expenseId = 99L).getOrThrow()
        val saved = repository.getById(id)!!
        assertEquals(99L, saved.expenseId)
        assertNull(saved.tripId)
    }

    // ── submittedByUserId ──────────────────────────────────────────────────────

    @Test
    fun invoke_submittedByUserId_persistedCorrectly() = runTest {
        val id = useCase(submittedByUserId = 77L, tripId = 1L).getOrThrow()
        val saved = repository.getById(id)!!
        assertEquals(77L, saved.submittedByUserId)
    }

    // ── submittedAt timestamp ─────────────────────────────────────────────────

    @Test
    fun invoke_submittedAt_withinTestTimeBracket() = runTest {
        val before = System.currentTimeMillis()
        val id     = useCase(submittedByUserId = 1L, tripId = 3L).getOrThrow()
        val after  = System.currentTimeMillis()

        val saved = repository.getById(id)!!
        assertTrue(
            "submittedAt (${saved.submittedAt}) not in [$before, $after]",
            saved.submittedAt in before..after,
        )
    }

    // ── Review fields are empty on creation ───────────────────────────────────

    @Test
    fun invoke_newRequest_reviewFieldsAreNull() = runTest {
        val id = useCase(submittedByUserId = 1L, tripId = 7L).getOrThrow()
        val saved = repository.getById(id)!!
        assertNull(saved.reviewedByUserId)
        assertNull(saved.reviewedAt)
        assertNull(saved.reviewComment)
    }

    // ── Multiple independent submissions ──────────────────────────────────────

    @Test
    fun invoke_twoSubmissions_eachHasUniqueId() = runTest {
        val id1 = useCase(submittedByUserId = 1L, tripId = 10L).getOrThrow()
        val id2 = useCase(submittedByUserId = 1L, tripId = 20L).getOrThrow()

        assertTrue(id1 > 0L)
        assertTrue(id2 > 0L)
        assertTrue(id1 != id2)

        assertNotNull(repository.getById(id1))
        assertNotNull(repository.getById(id2))
    }

    @Test
    fun invoke_twoSubmissions_bothAppearInPendingList() = runTest {
        useCase(submittedByUserId = 1L, tripId = 10L)
        useCase(submittedByUserId = 2L, tripId = 20L)

        val pending = repository.observePending().first()
        assertEquals(2, pending.size)
        assertTrue(pending.all { it.status == ApprovalStatus.PENDING })
    }
}
