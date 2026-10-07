package com.example.mileagetracker.data.formatting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanadianCurrencyFormatterTest {

    private val formatter = CanadianCurrencyFormatter()

    @Test
    fun `format returns expected CAD string`() {
        // Note: NBSP (Unicode \u00A0) is often used in currency formatting instead of regular space
        val result = formatter.format(1234.56).replace('\u00A0', ' ')
        assertEquals("$1,234.56", result)
    }

    @Test
    fun `format handles zero`() {
        assertEquals("$0.00", formatter.format(0.0).replace('\u00A0', ' '))
    }

    @Test
    fun `format handles negative amounts`() {
        val negative = formatter.format(-1.0).replace('\u00A0', ' ')
        assertTrue(negative.contains("1.00"))
    }
}
