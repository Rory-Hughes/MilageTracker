package com.example.mileagetracker.data.repository

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
 * Repository integration tests for [RateRepositoryImpl] against a real
 * (in-memory) SQLite database.
 *
 * Most CRA rate DAO behaviour is already exercised by [CraRateDaoTest].
 * These tests focus on repository-layer guarantees:
 *
 * - **`insertRates` empty-list guard**: calling `insertRates(emptyList())` is
 *   a no-op and must not crash (the `if (rates.isNotEmpty())` guard protects
 *   against a Room exception on an empty batch).
 * - **`isYearSeeded`**: `false` before any insert, `true` after.
 * - **`getSeededYears`**: returns distinct tax years in ascending order.
 * - **Mid-year revision**: when two rows exist for the same [taxYear] with
 *   different [CraRateEntity.effectiveFrom] timestamps, `getRateForYearOnce`
 *   returns the row with the higher timestamp — the rate resolver selects
 *   `ORDER BY effective_from DESC LIMIT 1`.
 * - **`getRateForYear` (Flow)**: emits `null` initially, then the entity
 *   after insertion without requiring an explicit re-fetch.
 */
@RunWith(AndroidJUnit4::class)
class RateRepositoryImplTest {

    private lateinit var db: MileageDatabase
    private lateinit var repository: RateRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MileageDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RateRepositoryImpl(db.craRateDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── fixtures ───────────────────────────────────────────────────────────────

    private fun rate(
        taxYear: Int = 2025,
        firstTierRateCents: Int = 70,
        firstTierLimitKm: Int = 5_000,
        additionalKmRateCents: Int = 64,
        sourceLabel: String = "CRA Test",
        effectiveFrom: Long = 1_000L,
    ) = CraRateEntity(
        taxYear              = taxYear,
        firstTierRateCents   = firstTierRateCents,
        firstTierLimitKm     = firstTierLimitKm,
        additionalKmRateCents = additionalKmRateCents,
        sourceLabel          = sourceLabel,
        effectiveFrom        = effectiveFrom,
    )

    // ── insertRate + getRateForYearOnce ───────────────────────────────────────

    @Test
    fun insertRate_andGetRateForYearOnce_returnsCorrectEntity() = runTest {
        repository.insertRate(rate(taxYear = 2024, firstTierRateCents = 70))

        val result = repository.getRateForYearOnce(2024)
        assertNotNull(result)
        assertEquals(2024, result!!.taxYear)
        assertEquals(70, result.firstTierRateCents)
    }

    @Test
    fun getRateForYearOnce_returnsNull_whenYearNotSeeded() = runTest {
        assertNull(repository.getRateForYearOnce(2099))
    }

    // ── isYearSeeded ──────────────────────────────────────────────────────────

    @Test
    fun isYearSeeded_returnsFalse_beforeInsert() = runTest {
        assertFalse(repository.isYearSeeded(2030))
    }

    @Test
    fun isYearSeeded_returnsTrue_afterInsert() = runTest {
        repository.insertRate(rate(taxYear = 2030))

        assertTrue(repository.isYearSeeded(2030))
    }

    @Test
    fun isYearSeeded_scopedToYear_doesNotCrossContaminate() = runTest {
        repository.insertRate(rate(taxYear = 2026))

        assertTrue(repository.isYearSeeded(2026))
        assertFalse(repository.isYearSeeded(2025))
    }

    // ── getSeededYears ────────────────────────────────────────────────────────

    @Test
    fun getSeededYears_returnsDistinctYearsAscending() = runTest {
        repository.insertRate(rate(taxYear = 2024, effectiveFrom = 1L))
        repository.insertRate(rate(taxYear = 2022, effectiveFrom = 1L))
        repository.insertRate(rate(taxYear = 2023, effectiveFrom = 1L))
        // Second row for 2024 — getSeededYears must de-duplicate.
        repository.insertRate(rate(taxYear = 2024, effectiveFrom = 2L))

        val years = repository.getSeededYears()
        assertEquals(listOf(2022, 2023, 2024), years)
    }

    @Test
    fun getSeededYears_returnsEmpty_whenNoRatesSeeded() = runTest {
        assertTrue(repository.getSeededYears().isEmpty())
    }

    // ── insertRates: batch insert ─────────────────────────────────────────────

    @Test
    fun insertRates_insertsAllRowsInBatch() = runTest {
        val batch = listOf(
            rate(taxYear = 2022, effectiveFrom = 1L),
            rate(taxYear = 2023, effectiveFrom = 1L),
            rate(taxYear = 2024, effectiveFrom = 1L),
        )

        repository.insertRates(batch)

        assertEquals(listOf(2022, 2023, 2024), repository.getSeededYears())
    }

    @Test
    fun insertRates_emptyList_isNoop_doesNotCrash() = runTest {
        repository.insertRates(emptyList()) // must not throw

        assertTrue(repository.getSeededYears().isEmpty())
    }

    // ── mid-year revision: highest effectiveFrom wins ─────────────────────────

    @Test
    fun getRateForYearOnce_midYearRevision_returnsHighestEffectiveFrom() = runTest {
        // Original rate published Jan 1.
        repository.insertRate(rate(
            taxYear = 2025, firstTierRateCents = 70, effectiveFrom = 1_000L,
        ))
        // Mid-year CRA revision published June.
        repository.insertRate(rate(
            taxYear = 2025, firstTierRateCents = 73, effectiveFrom = 5_000L,
        ))

        val resolved = repository.getRateForYearOnce(2025)!!
        // The resolver selects ORDER BY effective_from DESC LIMIT 1.
        assertEquals(73, resolved.firstTierRateCents)
        assertEquals(5_000L, resolved.effectiveFrom)
    }

    // ── getRateForYear (Flow) ─────────────────────────────────────────────────

    @Test
    fun getRateForYear_emitsNull_whenYearNotSeeded() = runTest {
        val emitted = repository.getRateForYear(2040).first()
        assertNull(emitted)
    }

    @Test
    fun getRateForYear_emitsEntity_afterInsert() = runTest {
        repository.insertRate(rate(taxYear = 2026, firstTierRateCents = 73))

        val emitted = repository.getRateForYear(2026).first()
        assertNotNull(emitted)
        assertEquals(73, emitted!!.firstTierRateCents)
    }

    // ── all fields round-trip ─────────────────────────────────────────────────

    @Test
    fun insertRate_allFields_roundTrip() = runTest {
        repository.insertRate(
            rate(
                taxYear               = 2027,
                firstTierRateCents    = 75,
                firstTierLimitKm      = 5_000,
                additionalKmRateCents = 68,
                sourceLabel           = "CRA IT522 2027",
                effectiveFrom         = 9_999L,
            ),
        )

        val result = repository.getRateForYearOnce(2027)!!
        assertEquals(2027, result.taxYear)
        assertEquals(75, result.firstTierRateCents)
        assertEquals(5_000, result.firstTierLimitKm)
        assertEquals(68, result.additionalKmRateCents)
        assertEquals("CRA IT522 2027", result.sourceLabel)
        assertEquals(9_999L, result.effectiveFrom)
    }
}
