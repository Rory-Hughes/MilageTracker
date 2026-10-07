package com.example.mileagetracker.domain.usecase.rate

import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.domain.model.CraRateRegion
import com.example.mileagetracker.domain.repository.RateRepository
import com.example.mileagetracker.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CalculateReimbursementUseCaseTest {

    private lateinit var rateRepository: RateRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: CalculateReimbursementUseCase

    @Before
    fun setUp() {
        rateRepository = mockk()
        settingsRepository = mockk()
        useCase = CalculateReimbursementUseCase(rateRepository, settingsRepository)
    }

    private fun testRate(
        year: Int = 2025,
        firstTierRate: Int = 70,
        additionalRate: Int = 64,
        limit: Int = 5000
    ) = CraRateEntity(
        taxYear = year,
        firstTierRateCents = firstTierRate,
        firstTierLimitKm = limit,
        additionalKmRateCents = additionalRate,
        sourceLabel = "Test",
        effectiveFrom = 1000L
    )

    @Test
    fun `calculate returns correct amount for first tier only`() {
        val rate = testRate()
        val result = useCase.calculate(rate, newDistanceKm = 100f, ytdKmBefore = 0f)

        assertEquals(7000, result.amountCents) // 100 * 70
        assertEquals(100, result.firstTierKm)
        assertEquals(0, result.secondTierKm)
    }

    @Test
    fun `calculate returns correct amount for mixed tiers`() {
        val rate = testRate()
        // 4900 already driven. This drive is 200.
        // 100 in first tier (up to 5000), 100 in second tier.
        val result = useCase.calculate(rate, newDistanceKm = 200f, ytdKmBefore = 4900f)

        // (100 * 70) + (100 * 64) = 7000 + 6400 = 13400
        assertEquals(13400, result.amountCents)
        assertEquals(100, result.firstTierKm)
        assertEquals(100, result.secondTierKm)
    }

    @Test
    fun `calculate returns correct amount for second tier only`() {
        val rate = testRate()
        // 5000 already driven. This drive is 100.
        val result = useCase.calculate(rate, newDistanceKm = 100f, ytdKmBefore = 5000f)

        assertEquals(6400, result.amountCents) // 100 * 64
        assertEquals(0, result.firstTierKm)
        assertEquals(100, result.secondTierKm)
    }

    @Test
    fun `calculate rounds distances to nearest whole km half-up`() {
        val rate = testRate()
        
        // 10.4 rounds to 10 -> 700 cents
        val res1 = useCase.calculate(rate, newDistanceKm = 10.4f)
        assertEquals(700, res1.amountCents)
        
        // 10.5 rounds to 11 -> 770 cents
        val res2 = useCase.calculate(rate, newDistanceKm = 10.5f)
        assertEquals(770, res2.amountCents)
        
        // 10.6 rounds to 11 -> 770 cents
        val res3 = useCase.calculate(rate, newDistanceKm = 10.6f)
        assertEquals(770, res3.amountCents)
    }

    @Test
    fun `invoke fetches region and rate then calculates`() = runTest {
        every { settingsRepository.getCraRateRegion() } returns flowOf(CraRateRegion.PROVINCES)
        coEvery { rateRepository.getRateForYearOnce(2025) } returns testRate(2025)

        val result = useCase(taxYear = 2025, newDistanceKm = 10f, ytdKmBefore = 0f)

        assertEquals(720, result.amountCents)
    }
}
