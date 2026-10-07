package com.example.mileagetracker.data.formatting

import org.junit.Assert.assertEquals
import org.junit.Test

class CanadianDistanceFormatterTest {

    private val formatter = CanadianDistanceFormatter()

    @Test
    fun `format returns expected distance string`() {
        assertEquals("1,234.6 km", formatter.format(1234.56))
        assertEquals("10.0 km", formatter.format(10.0))
        assertEquals("0.0 km", formatter.format(0.0))
    }
}
