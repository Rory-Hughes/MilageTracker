package com.example.mileagetracker.domain.usecase.formatting

import com.example.mileagetracker.data.formatting.CanadianCurrencyFormatter
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FormatCanadianCurrencyUseCaseTest {

    private lateinit var formatter: CanadianCurrencyFormatter
    private lateinit var useCase: FormatCanadianCurrencyUseCase

    @Before
    fun setUp() {
        formatter = mockk()
        useCase = FormatCanadianCurrencyUseCase(formatter)
    }

    @Test
    fun `delegates to formatter`() {
        every { formatter.format(12.34) } returns "$12.34"
        assertEquals("$12.34", useCase(12.34))
    }
}
