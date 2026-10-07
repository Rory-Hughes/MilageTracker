package com.example.mileagetracker.data.seeder

import com.example.mileagetracker.domain.repository.RateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CraRateSeederTest {

    private lateinit var rateRepository: RateRepository
    private lateinit var seeder: CraRateSeeder

    @Before
    fun setUp() {
        rateRepository = mockk()
        seeder = CraRateSeeder(rateRepository)
    }

    @Test
    fun `seedAll inserts only unseeded years`() = runTest {
        // Mock: 2024 is already seeded, 2025 is not.
        coEvery { rateRepository.isYearSeeded(any()) } returns false // Others not seeded
        coEvery { rateRepository.isYearSeeded(2024) } returns true
        
        coEvery { rateRepository.insertRates(any()) } returns Unit

        val insertedCount = seeder.seedAll()

        // insertedCount should be total known rates minus 1 (2024).
        assertEquals(CraRateSeeder.KNOWN_RATES.size - 1, insertedCount)
        coVerify { rateRepository.insertRates(match { list -> 
            list.none { it.taxYear == 2024 } && list.any { it.taxYear == 2025 }
        }) }
    }

    @Test
    fun `seedYear returns false if already seeded`() = runTest {
        coEvery { rateRepository.isYearSeeded(2024) } returns true
        val result = seeder.seedYear(2024)
        assertFalse(result)
        coVerify(exactly = 0) { rateRepository.insertRate(any()) }
    }

    @Test
    fun `seedYear inserts if not seeded`() = runTest {
        coEvery { rateRepository.isYearSeeded(2025) } returns false
        coEvery { rateRepository.insertRate(any()) } returns Unit
        
        val result = seeder.seedYear(2025)
        
        assertTrue(result)
        coVerify { rateRepository.insertRate(match { it.taxYear == 2025 }) }
    }

    @Test
    fun `unseededYears returns correct list`() = runTest {
        coEvery { rateRepository.isYearSeeded(any()) } returns false
        coEvery { rateRepository.isYearSeeded(2020) } returns true
        
        val unseeded = seeder.unseededYears()
        
        assertFalse(unseeded.contains(2020))
        assertTrue(unseeded.contains(2021))
        assertEquals(CraRateSeeder.KNOWN_RATES.size - 1, unseeded.size)
    }
}
