package com.example.mileagetracker.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.ApprovalRequestEntity
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
 * Room integration tests for [ApprovalRequestDao] against a real (in-memory) SQLite
 * database. These exercise status filtering (PENDING manager queue), per-trip and
 * per-expense lookups, driver-scoped history (newest-first), approved-in-range date
 * bounds (inclusive, reviewedAt ASC), update, and the ABORT conflict strategy.
 *
 * [ApprovalRequestEntity] has no `@ForeignKey`, so no parent rows are required.
 */
@RunWith(AndroidJUnit4::class)
class ApprovalRequestDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: ApprovalRequestDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.approvalRequestDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun request(
        id: Long = 0,
        tripId: Long? = null,
        expenseId: Long? = null,
        submittedByUserId: Long = 1L,
        submittedAt: Long = 1_000L,
        status: String = "PENDING",
        reviewedByUserId: Long? = null,
        reviewedAt: Long? = null,
        reviewComment: String? = null,
    ) = ApprovalRequestEntity(
        id = id,
        tripId = tripId,
        expenseId = expenseId,
        submittedByUserId = submittedByUserId,
        submittedAt = submittedAt,
        status = status,
        reviewedByUserId = reviewedByUserId,
        reviewedAt = reviewedAt,
        reviewComment = reviewComment,
    )

    // ── insert + getById ───────────────────────────────────────────────────────

    @Test
    fun insert_thenGetById_roundTripsAllFields() = runTest {
        val id = dao.insert(
            request(
                tripId = 10L,
                expenseId = null,
                submittedByUserId = 5L,
                submittedAt = 9_000L,
                status = "APPROVED",
                reviewedByUserId = 99L,
                reviewedAt = 10_000L,
                reviewComment = "Looks good",
            ),
        )

        val loaded = dao.getById(id)!!
        assertEquals(10L, loaded.tripId)
        assertNull(loaded.expenseId)
        assertEquals(5L, loaded.submittedByUserId)
        assertEquals("APPROVED", loaded.status)
        assertEquals(99L, loaded.reviewedByUserId)
        assertEquals(10_000L, loaded.reviewedAt)
        assertEquals("Looks good", loaded.reviewComment)
    }

    @Test
    fun getById_returnsNull_forUnknownId() = runTest {
        assertNull(dao.getById(999L))
    }

    // ── getPendingForManager ───────────────────────────────────────────────────

    @Test
    fun getPendingForManager_returnsOnlyPendingRows_oldestSubmittedAtFirst() = runTest {
        dao.insert(request(status = "PENDING",  submittedAt = 3_000L))
        dao.insert(request(status = "APPROVED", submittedAt = 2_000L))
        dao.insert(request(status = "PENDING",  submittedAt = 1_000L))
        dao.insert(request(status = "REJECTED", submittedAt = 4_000L))

        val pending = dao.getPendingForManager().first()

        assertEquals(2, pending.size)
        assertTrue(pending.all { it.status == "PENDING" })
        assertEquals(listOf(1_000L, 3_000L), pending.map { it.submittedAt }) // ASC
    }

    @Test
    fun getPendingForManager_emitsEmpty_whenNoPendingRows() = runTest {
        dao.insert(request(status = "APPROVED"))
        assertTrue(dao.getPendingForManager().first().isEmpty())
    }

    // ── getForTrip / getForExpense ────────────────────────────────────────────

    @Test
    fun getForTrip_returnsMatchingRequest_orNull() = runTest {
        dao.insert(request(tripId = 7L))
        dao.insert(request(tripId = 8L))

        assertEquals(7L, dao.getForTrip(7L)!!.tripId)
        assertNull(dao.getForTrip(999L))
    }

    @Test
    fun getForExpense_returnsMatchingRequest_orNull() = runTest {
        dao.insert(request(expenseId = 42L))

        assertEquals(42L, dao.getForExpense(42L)!!.expenseId)
        assertNull(dao.getForExpense(999L))
    }

    // ── getForDriver ──────────────────────────────────────────────────────────

    @Test
    fun getForDriver_scopesToDriver_newestSubmittedAtFirst() = runTest {
        dao.insert(request(submittedByUserId = 1L, submittedAt = 1_000L))
        dao.insert(request(submittedByUserId = 1L, submittedAt = 3_000L))
        dao.insert(request(submittedByUserId = 2L, submittedAt = 2_000L)) // other driver

        val driver1 = dao.getForDriver(1L).first()

        assertEquals(2, driver1.size)
        assertTrue(driver1.all { it.submittedByUserId == 1L })
        assertEquals(listOf(3_000L, 1_000L), driver1.map { it.submittedAt }) // DESC
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    fun update_persistsStatusAndReviewFields() = runTest {
        val id = dao.insert(request(status = "PENDING"))
        val existing = dao.getById(id)!!

        dao.update(
            existing.copy(
                status = "APPROVED",
                reviewedByUserId = 99L,
                reviewedAt = 5_000L,
                reviewComment = "OK",
            ),
        )

        val updated = dao.getById(id)!!
        assertEquals("APPROVED", updated.status)
        assertEquals(99L, updated.reviewedByUserId)
        assertEquals(5_000L, updated.reviewedAt)
        assertEquals("OK", updated.reviewComment)
    }

    // ── getApprovedInRange ────────────────────────────────────────────────────

    @Test
    fun getApprovedInRange_appliesInclusiveBounds_reviewedAtAsc_statusFilter() = runTest {
        // Approved within range
        dao.insert(request(status = "APPROVED", reviewedAt = 1_000L)) // lower bound inclusive
        dao.insert(request(status = "APPROVED", reviewedAt = 2_000L)) // upper bound inclusive
        // Excluded
        dao.insert(request(status = "APPROVED", reviewedAt = 999L))   // just before
        dao.insert(request(status = "APPROVED", reviewedAt = 2_001L)) // just after
        dao.insert(request(status = "PENDING",  reviewedAt = 1_500L)) // wrong status
        dao.insert(request(status = "APPROVED", reviewedAt = null))   // null reviewedAt excluded

        val results = dao.getApprovedInRange(fromMillis = 1_000L, toMillis = 2_000L)

        assertEquals(2, results.size)
        assertEquals(listOf(1_000L, 2_000L), results.map { it.reviewedAt }) // ASC
        assertTrue(results.all { it.status == "APPROVED" })
    }

    @Test
    fun getApprovedInRange_returnsEmpty_whenNoQualifyingRows() = runTest {
        dao.insert(request(status = "PENDING", reviewedAt = 1_500L))
        assertTrue(dao.getApprovedInRange(1_000L, 2_000L).isEmpty())
    }

    // ── ABORT conflict strategy ───────────────────────────────────────────────

    @Test
    fun insert_withDuplicatePrimaryKey_aborts() = runTest {
        dao.insert(request(id = 55L, status = "PENDING"))
        val error = try {
            dao.insert(request(id = 55L, status = "APPROVED"))
            null
        } catch (error: SQLiteConstraintException) {
            error
        }
        assertNotNull(error)
        assertEquals("PENDING", dao.getById(55L)!!.status)
    }
}
