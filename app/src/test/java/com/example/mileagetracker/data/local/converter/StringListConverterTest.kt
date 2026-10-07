package com.example.mileagetracker.data.local.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringListConverterTest {

    private val converter = StringListConverter()

    @Test
    fun `fromList converts list to comma-separated string`() {
        assertEquals("A,B,C", converter.fromList(listOf("A", "B", "C")))
        assertEquals("Single", converter.fromList(listOf("Single")))
        assertEquals("", converter.fromList(emptyList()))
    }

    @Test
    fun `toList converts comma-separated string back to list`() {
        assertEquals(listOf("A", "B", "C"), converter.toList("A,B,C"))
        assertEquals(listOf("Single"), converter.toList("Single"))
        assertTrue(converter.toList("").isEmpty())
    }
}
