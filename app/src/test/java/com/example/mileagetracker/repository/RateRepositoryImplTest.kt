package com.example.mileagetracker.repository

import com.example.mileagetracker.data.local.dao.CraRateDao
import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.data.repository.RateRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RateRepositoryImplTest {

    private lateinit var craRateDao: CraRateDao
    private lateinit var repository: RateRepositoryImpl

    @Before
    fun setUp() {
        craRateDao = mockk()
        repository = RateRepositoryImpl(craRateDao)
    }

    private fun rateFor(
        year: Int,
        firstTierRateCents: Int = 70,
        additionalKmRateCents: Int = 64,
        firstTierLimitKm: Int = 5_000,
    ) = CraRateEntity(
        taxYear = year,
        firstTierRateCents = firstTierRateCents,
        firstTierLimitKm = firstTierLimitKm,
        additionalKmRateCents = additionalKmRateCents,
        sourceLabel = "Test seed",
        effectiveFrom = System.currentTimeMillis(),
    )

    @Test
    fun `insertRate delegates to dao`() = runTest {
        val rate = rateFor(2025)
        coEvery { craRateDao.insertRate(rate) } returns 1L

        repository.insertRate(rate)

        coVerify(exactly = 1) { craRateDao.insertRate(rate) }
    }

    @Test
    fun `insertRates delegates non-empty list to dao`() = runTest {
        val rates = listOf(rateFor(2024), rateFor(2025))
        coEvery { craRateDao.insertRates(rates) } returns listOf(1L, 2L)

        repository.insertRates(rates)

        coVerify(exactly = 1) { craRateDao.insertRates(rates) }
    }

    @Test
    fun `insertRates skips dao call for empty list`() = runTest {
        repository.insertRates(emptyList())

        coVerify(exactly = 0) { craRateDao.insertRates(any()) }
    }

    @Test
    fun `isYearSeeded returns true when dao reports seeded`() = runTest {
        coEvery { craRateDao.isYearSeeded(2025) } returns true

        assertTrue(repository.isYearSeeded(2025))
    }

    @Test
    fun `isYearSeeded returns false when dao reports not seeded`() = runTest {
        coEvery { craRateDao.isYearSeeded(2099) } returns false

        assertFalse(repository.isYearSeeded(2099))
    }

    @Test
    fun `getSeededYears returns list from dao`() = runTest {
        val years = listOf(2024, 2025)
        coEvery { craRateDao.getSeededYears() } returns years

        assertEquals(years, repository.getSeededYears())
    }

    @Test
    fun `getRateForYearOnce returns rate when seeded`() = runTest {
        val rate = rateFor(2025)
        coEvery { craRateDao.getRateForYearOnce(2025) } returns rate

        val result = repository.getRateForYearOnce(2025)

        assertEquals(rate, result)
        assertEquals(70, result!!.firstTierRateCents)
        assertEquals(64, result.additionalKmRateCents)
    }

    @Test
    fun `getRateForYearOnce returns null when year not seeded`() = runTest {
        coEvery { craRateDao.getRateForYearOnce(1999) } returns null

        assertNull(repository.getRateForYearOnce(1999))
    }

    @Test
    fun `rate entity exposes correct tier threshold for reimbursement calculation`() = runTest {
        val rate = rateFor(2025, firstTierRateCents = 70, additionalKmRateCents = 64)
        coEvery { craRateDao.getRateForYearOnce(2025) } returns rate

        val result = repository.getRateForYearOnce(2025)!!

        assertEquals(5_000, result.firstTierLimitKm)
        assertTrue(result.firstTierRateCents > result.additionalKmRateCents)
    }
}
