package com.example.mileagetracker.domain.usecase.rate

import com.example.mileagetracker.data.local.entity.CraRateEntity
import com.example.mileagetracker.domain.repository.RateRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetCraRateForYearUseCaseTest {

    private lateinit var repository: RateRepository
    private lateinit var useCase: GetCraRateForYearUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = GetCraRateForYearUseCase(repository)
    }

    private fun testRate() = CraRateEntity(
        taxYear = 2025,
        firstTierRateCents = 70,
        firstTierLimitKm = 5000,
        additionalKmRateCents = 64,
        sourceLabel = "Test",
        effectiveFrom = 1000L
    )

    @Test
    fun `invoke delegates to repository flow`() = runTest {
        val rate = testRate()
        every { repository.getRateForYear(2025) } returns flowOf(rate)
        
        val result = useCase(2025).first()
        
        assertEquals(rate, result)
    }

    @Test
    fun `once delegates to repository one-shot`() = runTest {
        val rate = testRate()
        coEvery { repository.getRateForYearOnce(2025) } returns rate
        
        val result = useCase.once(2025)
        
        assertEquals(rate, result)
    }
}
