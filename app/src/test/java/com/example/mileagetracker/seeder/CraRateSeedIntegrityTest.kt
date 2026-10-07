package com.example.mileagetracker.seeder

import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.seeder.CraRateSeeder
import com.example.mileagetracker.domain.repository.RateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 13.4 — Seed integrity tests.
 *
 * ## Coverage
 *
 * ### CRA rates insert once
 * - First simulated launch inserts exactly [CraRateSeeder.KNOWN_RATES].size rows.
 * - No row is written a second time on any subsequent launch (idempotency).
 * - [CraRateSeeder.seedYear] inserts a single specific year once and ignores
 *   repeat calls.
 *
 * ### Repeated app launches do not duplicate data
 * - 10 consecutive [seedAll] calls leave the repository at exactly
 *   [CraRateSeeder.KNOWN_RATES].size rows — no extras, no missing.
 * - Every taxYear in the store is unique after repeated seeding.
 *
 * ### Schema evolution safety
 * - An unknown taxYear is silently ignored (no crash, no phantom row).
 * - A partial seed (only some years present) is correctly topped-up on the
 *   next launch without re-inserting already-present rows.
 * - The [CraRateSeeder.RateSeed.toEntity] mapping preserves every field,
 *   including the correct cents values and [effectiveFrom] = Jan 1 UTC.
 * - A mid-year revision row (same taxYear, later effectiveFrom) does not
 *   interfere with seeding idempotency.
 * - [unseededYears] is empty after a full seed and non-empty before.
 * - All 18 known rates have positive firstTier > additional tier values.
 */
class CraRateSeedIntegrityTest {

    private lateinit var repo: InMemoryRateRepository
    private lateinit var seeder: CraRateSeeder

    @Before
    fun setUp() {
        repo   = InMemoryRateRepository()
        seeder = CraRateSeeder(repo)
    }

    // ── CRA rates insert once ──────────────────────────────────────────────

    @Test
    fun `first launch inserts exactly KNOWN_RATES rows`() = runTest {
        val inserted = seeder.seedAll()

        assertEquals(CraRateSeeder.KNOWN_RATES.size, inserted)
        assertEquals(CraRateSeeder.KNOWN_RATES.size, repo.rowCount)
    }

    @Test
    fun `second launch inserts zero rows`() = runTest {
        seeder.seedAll()
        val second = seeder.seedAll()

        assertEquals(0, second)
        assertEquals(CraRateSeeder.KNOWN_RATES.size, repo.rowCount)
    }

    @Test
    fun `seedYear inserts exactly one row for a known year`() = runTest {
        val inserted = seeder.seedYear(2025)

        assertTrue(inserted)
        assertEquals(1, repo.rowCount)
        assertEquals(2025, repo.rows.first().taxYear)
    }

    @Test
    fun `seedYear is a no-op on repeat call for same year`() = runTest {
        seeder.seedYear(2024)
        val second = seeder.seedYear(2024)

        assertFalse(second)
        assertEquals(1, repo.rowCount)
    }

    @Test
    fun `seedYear returns false and inserts nothing for unknown year`() = runTest {
        val result = seeder.seedYear(1900) // not in KNOWN_RATES

        assertFalse(result)
        assertEquals(0, repo.rowCount)
    }

    // ── Repeated launches do not duplicate data ────────────────────────────

    @Test
    fun `ten consecutive seedAll calls leave exactly KNOWN_RATES rows`() = runTest {
        repeat(10) { seeder.seedAll() }

        assertEquals(CraRateSeeder.KNOWN_RATES.size, repo.rowCount)
    }

    @Test
    fun `every taxYear in store is unique after repeated seeding`() = runTest {
        repeat(5) { seeder.seedAll() }

        val years = repo.rows.map { it.taxYear }
        assertEquals(years.size, years.toSet().size)
    }

    @Test
    fun `repeated seedAll returns 0 on every call after the first`() = runTest {
        seeder.seedAll() // first
        val subsequentResults = (1..9).map { seeder.seedAll() }

        assertTrue(subsequentResults.all { it == 0 })
    }

    @Test
    fun `seedYear called for every known year produces no duplicates`() = runTest {
        CraRateSeeder.KNOWN_RATES.forEach { seed -> seeder.seedYear(seed.taxYear) }
        // Call again — all must be no-ops
        CraRateSeeder.KNOWN_RATES.forEach { seed ->
            val result = seeder.seedYear(seed.taxYear)
            assertFalse("Expected seedYear(${seed.taxYear}) to be a no-op", result)
        }
        assertEquals(CraRateSeeder.KNOWN_RATES.size, repo.rowCount)
    }

    // ── unseededYears contract ─────────────────────────────────────────────────

    @Test
    fun `unseededYears returns all known years before any seeding`() = runTest {
        val unseeded = seeder.unseededYears()

        val knownYears = CraRateSeeder.KNOWN_RATES.map { it.taxYear }.toSet()
        assertEquals(knownYears, unseeded.toSet())
    }

    @Test
    fun `unseededYears is empty after full seedAll`() = runTest {
        seeder.seedAll()

        assertTrue(seeder.unseededYears().isEmpty())
    }

    @Test
    fun `unseededYears reflects partial seed state correctly`() = runTest {
        // Seed only the first half of known rates
        val half = CraRateSeeder.KNOWN_RATES.take(CraRateSeeder.KNOWN_RATES.size / 2)
        half.forEach { seed -> seeder.seedYear(seed.taxYear) }

        val unseeded = seeder.unseededYears().toSet()
        val seededYears = half.map { it.taxYear }.toSet()
        val remainingYears = CraRateSeeder.KNOWN_RATES.map { it.taxYear }.toSet() - seededYears

        assertEquals(remainingYears, unseeded)
    }

    // ── Schema evolution safety ───────────────────────────────────────────────

    @Test
    fun `partial seed on first launch is fully recovered on next seedAll`() = runTest {
        // Simulate a crash mid-first-launch: only some years were written.
        val partial = CraRateSeeder.KNOWN_RATES.take(3)
        partial.forEach { repo.insertRate(it.toEntity()) }

        // Next launch should top-up the missing years only.
        val inserted = seeder.seedAll()
        val expectedMissing = CraRateSeeder.KNOWN_RATES.size - partial.size

        assertEquals(expectedMissing, inserted)
        assertEquals(CraRateSeeder.KNOWN_RATES.size, repo.rowCount)
    }

    @Test
    fun `mid-year revision row does not interfere with seedAll idempotency`() = runTest {
        // Simulate a CRA mid-year revision: a second row for 2025 with a later
        // effectiveFrom is already in the DB (inserted by the rate-update flow,
        // not by the seeder).  seedAll must still see 2025 as already seeded.
        seeder.seedAll()
        val revisionRow = repo.rows.first { it.taxYear == 2025 }.copy(
            id            = 0,
            effectiveFrom = repo.rows.first { it.taxYear == 2025 }.effectiveFrom + 86_400_000L,
        )
        repo.insertRaw(revisionRow) // bypass INSERT-OR-IGNORE key check for mid-year test

        val secondLaunch = seeder.seedAll()

        assertEquals(0, secondLaunch)
    }

    @Test
    fun `toEntity maps cents correctly for 2024 rates`() {
        val seed   = CraRateSeeder.KNOWN_RATES.first { it.taxYear == 2024 }
        val entity = seed.toEntity()

        assertEquals(2024, entity.taxYear)
        assertEquals(70, entity.firstTierRateCents)
        assertEquals(64, entity.additionalKmRateCents)
        assertEquals(5_000, entity.firstTierLimitKm)
    }

    @Test
    fun `toEntity maps cents correctly for 2026 rates`() {
        val seed   = CraRateSeeder.KNOWN_RATES.first { it.taxYear == 2026 }
        val entity = seed.toEntity()

        assertEquals(2026, entity.taxYear)
        assertEquals(73, entity.firstTierRateCents)
        assertEquals(67, entity.additionalKmRateCents)
    }

    @Test
    fun `toEntity effectiveFrom is January 1 UTC for every known year`() {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        CraRateSeeder.KNOWN_RATES.forEach { seed ->
            val entity = seed.toEntity()
            cal.timeInMillis = entity.effectiveFrom
            assertEquals(
                "Expected effectiveFrom = Jan 1 for ${seed.taxYear}",
                java.util.Calendar.JANUARY,
                cal.get(java.util.Calendar.MONTH),
            )
            assertEquals(
                "Expected effectiveFrom day = 1 for ${seed.taxYear}",
                1,
                cal.get(java.util.Calendar.DAY_OF_MONTH),
            )
            assertEquals(
                "Expected effectiveFrom year = ${seed.taxYear}",
                seed.taxYear,
                cal.get(java.util.Calendar.YEAR),
            )
        }
    }

    @Test
    fun `toEntity sourceLabel is non-blank and references the year`() {
        CraRateSeeder.KNOWN_RATES.forEach { seed ->
            val entity = seed.toEntity()
            assertTrue(
                "Expected sourceLabel to contain ${seed.taxYear}",
                entity.sourceLabel.contains(seed.taxYear.toString()),
            )
        }
    }

    @Test
    fun `known rates span 2009 through 2026 in ascending order`() {
        val years = CraRateSeeder.KNOWN_RATES.map { it.taxYear }
        assertTrue(years.contains(2009))
        assertTrue(years.contains(2026))
        assertEquals(years.sorted(), years)
    }

    @Test
    fun `all known rates have positive firstTier greater than additionalKmRate`() {
        CraRateSeeder.KNOWN_RATES.forEach { seed ->
            assertTrue("firstTier must be > 0 for ${seed.taxYear}", seed.firstTier > 0)
            assertTrue("additional must be > 0 for ${seed.taxYear}", seed.additional > 0)
            assertTrue(
                "firstTier must be > additional for ${seed.taxYear}",
                seed.firstTier > seed.additional,
            )
        }
    }

    @Test
    fun `getRateForYearOnce returns correct entity after seeding`() = runTest {
        seeder.seedAll()

        val rate2025 = repo.getRateForYearOnce(2025)
        assertNotNull(rate2025)
        assertEquals(72, rate2025!!.firstTierRateCents)
        assertEquals(66, rate2025.additionalKmRateCents)
    }

    @Test
    fun `getRateForYearOnce returns null for year not in KNOWN_RATES`() = runTest {
        seeder.seedAll()

        val result = repo.getRateForYearOnce(1985)
        assertEquals(null, result)
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // In-memory fake repository
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Faithful in-memory stand-in for [RateRepository].
     *
     * Contract mirrored from the real Room implementation:
     * - [insertRate] / [insertRates]: INSERT OR IGNORE on `taxYear` PK —
     *   silently discards a row if that taxYear is already stored.
     * - [insertRaw]: bypasses the ignore guard so tests can inject mid-year
     *   revision rows that share a taxYear with an existing seeded row.
     * - [getRateForYearOnce]: returns the row with the **latest effectiveFrom**
     *   for the requested year (mirrors the real DAO ORDER BY effective_from
     *   DESC LIMIT 1 query).
     * - [isYearSeeded]: returns true as soon as any row for that year exists.
     */
    private class InMemoryRateRepository : RateRepository {

        /** Backing store — may contain multiple rows per taxYear. */
        val rows = mutableListOf<CraRateEntity>()

        val rowCount: Int get() = rows.count { row ->
            // Count unique seeded years (mirrors isYearSeeded semantics)
            rows.indexOfFirst { it.taxYear == row.taxYear } == rows.indexOf(row)
        }

        // INSERT OR IGNORE: skip if taxYear already present
        override suspend fun insertRate(rate: CraRateEntity) {
            if (rows.none { it.taxYear == rate.taxYear }) rows.add(rate)
        }

        override suspend fun insertRates(rates: List<CraRateEntity>) {
            rates.forEach { insertRate(it) }
        }

        /** Bypass INSERT-OR-IGNORE — used only by the mid-year revision test. */
        fun insertRaw(rate: CraRateEntity) {
            rows.add(rate)
        }

        override suspend fun isYearSeeded(taxYear: Int): Boolean =
            rows.any { it.taxYear == taxYear }

        override suspend fun getSeededYears(): List<Int> =
            rows.map { it.taxYear }.distinct()

        // Mirror: SELECT * WHERE tax_year = ? ORDER BY effective_from DESC LIMIT 1
        override suspend fun getRateForYearOnce(taxYear: Int): CraRateEntity? =
            rows.filter { it.taxYear == taxYear }
                .maxByOrNull { it.effectiveFrom }

        override fun getRateForYear(taxYear: Int): Flow<CraRateEntity?> =
            flowOf(rows.filter { it.taxYear == taxYear }.maxByOrNull { it.effectiveFrom })
    }
}
