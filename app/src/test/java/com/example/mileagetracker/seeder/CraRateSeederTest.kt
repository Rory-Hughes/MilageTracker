package com.example.mileagetracker.seeder

import com.example.mileagetracker.data.local.MileageDatabase
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.seeder.CraRateSeeder
import com.example.mileagetracker.domain.repository.RateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure JVM unit tests for [CraRateSeeder] idempotency.
 *
 * Uses a fake in-memory [RateRepository] so no Android framework,
 * Room, or Hilt is needed.  Tests run on the JVM via `./gradlew test`.
 *
 * ## Coverage
 * - [CraRateSeeder.seedAll] inserts all 18 known rows on first call.
 * - Calling [CraRateSeeder.seedAll] a second and third time inserts 0 rows.
 * - [CraRateSeeder.seedYear] inserts a specific year when absent.
 * - [CraRateSeeder.seedYear] is a no-op when the year is already present.
 * - [CraRateSeeder.seedYear] returns false for an unknown year.
 * - [CraRateSeeder.unseededYears] returns empty after a full seed.
 */
class CraRateSeederTest {

    private lateinit var fakeRepo: FakeRateRepository
    private lateinit var seeder: CraRateSeeder

    @Before
    fun setUp() {
        fakeRepo = FakeRateRepository()
        seeder   = CraRateSeeder(fakeRepo)
    }

    @Test
    fun `seedAll inserts all known rows on first launch`() = runTest {
        val inserted = seeder.seedAll()
        assertEquals(CraRateSeeder.KNOWN_RATES.size, inserted)
        assertEquals(CraRateSeeder.KNOWN_RATES.size, fakeRepo.insertedRates.size)
    }

    @Test
    fun `seedAll on second launch inserts zero rows`() = runTest {
        seeder.seedAll()          // first launch
        val second = seeder.seedAll() // second launch
        assertEquals(0, second)
    }

    @Test
    fun `seedAll on third launch inserts zero rows`() = runTest {
        seeder.seedAll()
        seeder.seedAll()
        val third = seeder.seedAll()
        assertEquals(0, third)
        // Total rows in fake repo still equals KNOWN_RATES.size (no dupes)
        assertEquals(CraRateSeeder.KNOWN_RATES.size, fakeRepo.insertedRates.size)
    }

    @Test
    fun `seedAll does not produce duplicate rows`() = runTest {
        repeat(5) { seeder.seedAll() }
        val uniqueYears = fakeRepo.insertedRates.map { it.taxYear }.toSet()
        assertEquals(CraRateSeeder.KNOWN_RATES.size, uniqueYears.size)
    }

    @Test
    fun `seedYear inserts a specific year when absent`() = runTest {
        val result = seeder.seedYear(2024)
        assertTrue(result)
        assertTrue(fakeRepo.insertedRates.any { it.taxYear == 2024 })
    }

    @Test
    fun `seedYear returns false when year already present`() = runTest {
        seeder.seedYear(2024)               // first call – inserts
        val second = seeder.seedYear(2024)  // second call – should skip
        assertFalse(second)
        assertEquals(1, fakeRepo.insertedRates.count { it.taxYear == 2024 })
    }

    @Test
    fun `seedYear returns false for unknown year`() = runTest {
        val result = seeder.seedYear(1985)  // not in KNOWN_RATES
        assertFalse(result)
        assertTrue(fakeRepo.insertedRates.isEmpty())
    }

    @Test
    fun `unseededYears returns empty after full seedAll`() = runTest {
        seeder.seedAll()
        val unseeded = seeder.unseededYears()
        assertTrue(unseeded.isEmpty())
    }

    @Test
    fun `known rates cover 2009 through 2026`() {
        val years = CraRateSeeder.KNOWN_RATES.map { it.taxYear }
        assertTrue(years.contains(2009))
        assertTrue(years.contains(2026))
        assertEquals(years.sorted(), years) // must be in ascending order
    }

    @Test
    fun `all known rates have positive first and additional tier values`() {
        for (rate in CraRateSeeder.KNOWN_RATES) {
            assertTrue("firstTier must be > 0 for ${rate.taxYear}",
                rate.firstTier > 0)
            assertTrue("additional must be > 0 for ${rate.taxYear}",
                rate.additional > 0)
            assertTrue("firstTier must be > additional for ${rate.taxYear}",
                rate.firstTier > rate.additional)
        }
    }

    @Test
    fun `database default base rate matches latest CRA seeded first tier`() {
        val latest = CraRateSeeder.KNOWN_RATES.last()
        val latestFirstTierRate = latest.firstTier / 100f
        assertEquals(
            latestFirstTierRate,
            MileageDatabase.DEFAULT_POLICY_BASE_RATE,
            0.0001f,
        )
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Fake repository (in-memory, no Room)
    // ───────────────────────────────────────────────────────────────────────────

    /**
     * In-memory [RateRepository] for unit testing.
     *
     * Mirrors the `INSERT OR IGNORE` contract: [insertRate] and
     * [insertRates] silently discard rows whose `taxYear` is already
     * present, exactly as the real Room implementation must.
     */
    private class FakeRateRepository : RateRepository {

        /** All rows ever successfully inserted (no duplicates by taxYear). */
        val insertedRates = mutableListOf<CraRateEntity>()

        override suspend fun insertRate(rate: CraRateEntity) {
            if (insertedRates.none { it.taxYear == rate.taxYear }) {
                insertedRates.add(rate)
            }
        }

        override suspend fun insertRates(rates: List<CraRateEntity>) {
            rates.forEach { insertRate(it) }
        }

        override suspend fun isYearSeeded(taxYear: Int): Boolean =
            insertedRates.any { it.taxYear == taxYear }

        override suspend fun getSeededYears(): List<Int> =
            insertedRates.map { it.taxYear }

        override fun getRateForYear(taxYear: Int): Flow<CraRateEntity?> =
            flowOf(insertedRates.firstOrNull { it.taxYear == taxYear })

        override suspend fun getRateForYearOnce(taxYear: Int): CraRateEntity? =
            insertedRates.firstOrNull { it.taxYear == taxYear }
    }
}
