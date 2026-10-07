package com.example.mileagetracker.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.CraRateEntity
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
 * Room integration tests for [CraRateDao] against a real (in-memory) SQLite
 * database. These exercise the actual SQL — the mid-year revision resolution
 * (`ORDER BY effective_from DESC LIMIT 1`), reactive vs one-shot reads,
 * idempotency helpers ([isYearSeeded] / [getSeededYears]), batch insert, and
 * the `ABORT` conflict strategy that protects the read-mostly rate table.
 */
@RunWith(AndroidJUnit4::class)
class CraRateDaoTest {

    private lateinit var db: MileageDatabase
    private lateinit var dao: CraRateDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.craRateDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun rate(
        id: Int = 0,
        taxYear: Int = 2025,
        firstTierRateCents: Int = 73,
        firstTierLimitKm: Int = 5_000,
        additionalKmRateCents: Int = 67,
        sourceLabel: String = "CRA IT522 2025",
        effectiveFrom: Long = 1_735_689_600_000L, // 2025-01-01T00:00:00Z
    ) = CraRateEntity(
        id = id,
        taxYear = taxYear,
        firstTierRateCents = firstTierRateCents,
        firstTierLimitKm = firstTierLimitKm,
        additionalKmRateCents = additionalKmRateCents,
        sourceLabel = sourceLabel,
        effectiveFrom = effectiveFrom,
    )

    // ── insertRate + read-back ─────────────────────────────────────────────────

    @Test
    fun insertRate_returnsRowId_andGetOnce_roundTripsAllFields() = runTest {
        val rowId = dao.insertRate(
            rate(
                taxYear = 2024,
                firstTierRateCents = 70,
                firstTierLimitKm = 5_000,
                additionalKmRateCents = 64,
                sourceLabel = "CRA IT522 2024",
                effectiveFrom = 1_704_067_200_000L,
            ),
        )

        assertTrue(rowId > 0)
        val loaded = dao.getRateForYearOnce(2024)!!
        assertEquals(2024, loaded.taxYear)
        assertEquals(70, loaded.firstTierRateCents)
        assertEquals(5_000, loaded.firstTierLimitKm)
        assertEquals(64, loaded.additionalKmRateCents)
        assertEquals("CRA IT522 2024", loaded.sourceLabel)
        assertEquals(1_704_067_200_000L, loaded.effectiveFrom)
    }

    @Test
    fun getRateForYearOnce_returnsNull_whenNoRateSeeded() = runTest {
        assertNull(dao.getRateForYearOnce(2099))
    }

    // ── mid-year revision: highest effectiveFrom wins ──────────────────────────

    @Test
    fun getRateForYearOnce_resolvesHighestEffectiveFrom_forSameYear() = runTest {
        dao.insertRate(rate(taxYear = 2025, firstTierRateCents = 70, effectiveFrom = 1_000L))
        dao.insertRate(rate(taxYear = 2025, firstTierRateCents = 75, effectiveFrom = 2_000L)) // later revision

        val resolved = dao.getRateForYearOnce(2025)!!
        assertEquals(75, resolved.firstTierRateCents) // newer rate wins
        assertEquals(2_000L, resolved.effectiveFrom)
    }

    @Test
    fun getRateForYear_flowResolvesHighestEffectiveFrom() = runTest {
        dao.insertRate(rate(taxYear = 2025, firstTierRateCents = 70, effectiveFrom = 1_000L))
        dao.insertRate(rate(taxYear = 2025, firstTierRateCents = 75, effectiveFrom = 2_000L))

        val resolved = dao.getRateForYear(2025).first()!!
        assertEquals(75, resolved.firstTierRateCents)
    }

    @Test
    fun getRateForYear_yearScoping_doesNotCrossYears() = runTest {
        dao.insertRate(rate(taxYear = 2024, firstTierRateCents = 70, effectiveFrom = 1_000L))
        dao.insertRate(rate(taxYear = 2025, firstTierRateCents = 73, effectiveFrom = 2_000L))

        assertEquals(70, dao.getRateForYearOnce(2024)!!.firstTierRateCents)
        assertEquals(73, dao.getRateForYearOnce(2025)!!.firstTierRateCents)
    }

    // ── getRateForYear reactive null ──────────────────────────────────────────

    @Test
    fun getRateForYear_flow_emitsNull_forUnseededYear() = runTest {
        assertNull(dao.getRateForYear(2099).first())
    }

    // ── insertRates (batch) ───────────────────────────────────────────────────

    @Test
    fun insertRates_insertsAllRows_andReturnsRowIds() = runTest {
        val rates = listOf(
            rate(taxYear = 2022, firstTierRateCents = 61, effectiveFrom = 100L),
            rate(taxYear = 2023, firstTierRateCents = 68, effectiveFrom = 200L),
            rate(taxYear = 2024, firstTierRateCents = 70, effectiveFrom = 300L),
        )

        val ids = dao.insertRates(rates)

        assertEquals(3, ids.size)
        assertTrue(ids.all { it > 0 })
        assertEquals(listOf(2022, 2023, 2024), dao.getSeededYears())
    }

    // ── isYearSeeded ──────────────────────────────────────────────────────────

    @Test
    fun isYearSeeded_returnsFalse_beforeInsert() = runTest {
        assertFalse(dao.isYearSeeded(2025))
    }

    @Test
    fun isYearSeeded_returnsTrue_afterInsert() = runTest {
        dao.insertRate(rate(taxYear = 2025))

        assertTrue(dao.isYearSeeded(2025))
        assertFalse(dao.isYearSeeded(2024)) // different year, not seeded
    }

    // ── getSeededYears ────────────────────────────────────────────────────────

    @Test
    fun getSeededYears_returnsDistinctYears_sortedAscending() = runTest {
        dao.insertRate(rate(taxYear = 2025, effectiveFrom = 1_000L))
        dao.insertRate(rate(taxYear = 2023, effectiveFrom = 1_000L))
        dao.insertRate(rate(taxYear = 2025, effectiveFrom = 2_000L)) // second row for 2025 (mid-year revision)
        dao.insertRate(rate(taxYear = 2024, effectiveFrom = 1_000L))

        val years = dao.getSeededYears()

        // Distinct; 2025 appears twice in the table but once in the result.
        assertEquals(listOf(2023, 2024, 2025), years)
    }

    @Test
    fun getSeededYears_returnsEmpty_whenTableIsEmpty() = runTest {
        assertTrue(dao.getSeededYears().isEmpty())
    }

    // ── ABORT conflict strategy ───────────────────────────────────────────────

    @Test
    fun insertRate_withDuplicatePrimaryKey_aborts() = runTest {
        dao.insertRate(rate(id = 99))
        val error = try {
            dao.insertRate(rate(id = 99))
            null
        } catch (error: SQLiteConstraintException) {
            error
        }
        assertNotNull(error)
    }
}
