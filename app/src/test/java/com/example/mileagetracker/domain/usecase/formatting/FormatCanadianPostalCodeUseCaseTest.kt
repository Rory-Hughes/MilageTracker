package com.example.mileagetracker.domain.usecase.formatting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatCanadianPostalCodeUseCaseTest {

    private val useCase = FormatCanadianPostalCodeUseCase()

    @Test
    fun `formats valid raw string`() {
        assertEquals("A1A 1A1", useCase("a1a1a1"))
        assertEquals("K1A 0B1", useCase("k1a-0b1"))
        assertEquals("M5V 2H1", useCase("m5v  2h1"))
    }

    @Test
    fun `returns null for invalid inputs`() {
        assertNull(useCase("123456"))
        assertNull(useCase("ABCDEF"))
        assertNull(useCase("A1A1A")) // Too short
        assertNull(useCase("A1A1A1A")) // Too long
    }
}
