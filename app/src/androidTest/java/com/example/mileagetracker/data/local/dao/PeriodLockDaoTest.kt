package com.example.mileagetracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.PeriodLockEntity
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
 * Room integration tests for [PeriodLockDao] against a real (in-memory) SQLite
 * database. These exercise the [isLocked] predicate — which treats a null-month
 * row as an "annual lock" that blocks any monthly check for that year — the
 * year-DESC/month-DESC ordering (with SQLite's NULL-last behaviour for month DESC),
 * REPLACE upsert, and targeted deletion.
 */
@RunWith(AndroidJUnit4::class)
class PeriodLockDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: PeriodLockDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.periodLockDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun lock(
        id: Long = 0,
        year: Int = 2026,
        month: Int? = null,
        lockedByUserId: Long = 1L,
        lockedAt: Long = 1_000L,
    ) = PeriodLockEntity(
        id = id,
        periodYear = year,
        periodMonth = month,
        lockedByUserId = lockedByUserId,
        lockedAt = lockedAt,
    )

    // ── isLocked: monthly lock ────────────────────────────────────────────────

    @Test
    fun isLocked_returnsFalse_whenTableIsEmpty() = runTest {
        assertFalse(dao.isLocked(2026, 3))
    }

    @Test
    fun isLocked_returnsTrue_forExactMonthMatch() = runTest {
        dao.insert(lock(year = 2026, month = 3))

        assertTrue(dao.isLocked(2026, 3))
        assertFalse(dao.isLocked(2026, 4))  // different month
        assertFalse(dao.isLocked(2025, 3))  // different year
    }

    // ── isLocked: annual lock (periodMonth IS NULL) ────────────────────────────

    @Test
    fun isLocked_annualLock_blocksAnyMonthInThatYear() = runTest {
        // A null-month row is an annual lock: it covers all months for that year.
        dao.insert(lock(year = 2026, month = null))

        assertTrue(dao.isLocked(2026, 1))
        assertTrue(dao.isLocked(2026, 6))
        assertTrue(dao.isLocked(2026, 12))
        assertFalse(dao.isLocked(2025, 6)) // different year, not locked
    }

    @Test
    fun isLocked_annualLock_isDetectedWhenCheckingWithNullMonth() = runTest {
        dao.insert(lock(year = 2026, month = null))

        // Querying with month=null matches rows where periodMonth IS NULL.
        assertTrue(dao.isLocked(2026, null))
        assertFalse(dao.isLocked(2025, null))
    }

    @Test
    fun isLocked_monthlyLock_doesNotMatchNullMonthQuery() = runTest {
        dao.insert(lock(year = 2026, month = 6))

        // month=null query only matches annual (null-month) locks, not monthly locks.
        assertFalse(dao.isLocked(2026, null))
    }

    // ── insert / getAll ordering ──────────────────────────────────────────────

    @Test
    fun getAll_ordersYearDescThenMonthDesc_nullMonthLast() = runTest {
        dao.insert(lock(year = 2025, month = 12))
        dao.insert(lock(year = 2026, month = 3))
        dao.insert(lock(year = 2026, month = 6))
        dao.insert(lock(year = 2026, month = null)) // annual lock, null sorts last in DESC

        // ORDER BY periodYear DESC, periodMonth DESC
        // SQLite treats NULL as the smallest value, so DESC puts it last.
        val rows = dao.getAll().first()
        assertEquals(listOf(6, 3, null, 12), rows.map { it.periodMonth })
        assertEquals(listOf(2026, 2026, 2026, 2025), rows.map { it.periodYear })
    }

    @Test
    fun getAll_emitsEmpty_whenTableIsEmpty() = runTest {
        assertTrue(dao.getAll().first().isEmpty())
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun delete_removesMatchingYearAndMonth_keepsOthers() = runTest {
        dao.insert(lock(year = 2026, month = 3))
        dao.insert(lock(year = 2026, month = 6))
        dao.insert(lock(year = 2025, month = 3))

        dao.delete(year = 2026, month = 3)

        val remaining = dao.getAll().first()
        assertEquals(2, remaining.size)
        assertFalse(remaining.any { it.periodYear == 2026 && it.periodMonth == 3 })
    }

    @Test
    fun delete_annualLock_removesNullMonthRow() = runTest {
        dao.insert(lock(year = 2026, month = null))
        dao.insert(lock(year = 2026, month = 6))

        dao.delete(year = 2026, month = null)

        val remaining = dao.getAll().first()
        assertEquals(1, remaining.size)
        assertEquals(6, remaining.single().periodMonth)
    }

    // ── REPLACE conflict strategy ─────────────────────────────────────────────

    @Test
    fun insert_withSamePrimaryKey_replacesRow() = runTest {
        dao.insert(lock(id = 42L, year = 2026, month = 3, lockedByUserId = 1L))
        dao.insert(lock(id = 42L, year = 2026, month = 4, lockedByUserId = 2L))

        val all = dao.getAll().first()
        assertEquals(1, all.size)
        assertEquals(4, all.single().periodMonth)
        assertEquals(2L, all.single().lockedByUserId)
    }
}
