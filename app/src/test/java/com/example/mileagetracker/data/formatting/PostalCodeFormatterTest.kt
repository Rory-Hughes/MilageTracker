package com.example.mileagetracker.data.formatting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostalCodeFormatterTest {

    @Test
    fun `format normalizes valid postal codes`() {
        assertEquals("A1A 1A1", PostalCodeFormatter.format("a1a1a1"))
        assertEquals("K1A 0B1", PostalCodeFormatter.format("k1a-0b1"))
        assertEquals("M5V 2H1", PostalCodeFormatter.format("m5v  2h1"))
    }

    @Test
    fun `format returns null for invalid postal codes`() {
        assertNull(PostalCodeFormatter.format("123456"))
        assertNull(PostalCodeFormatter.format("ABCDEF"))
        assertNull(PostalCodeFormatter.format("A1A1A"))
        assertNull(PostalCodeFormatter.format("A1A1A1A"))
    }

    @Test
    fun `isValid identifies correctly formatted postal codes`() {
        assertTrue(PostalCodeFormatter.isValid("A1A 1A1"))
        assertTrue(PostalCodeFormatter.isValid("K1A 0B1"))
        
        assertFalse(PostalCodeFormatter.isValid("a1a 1a1")) // lowercase
        assertFalse(PostalCodeFormatter.isValid("A1A1A1")) // missing space
        assertFalse(PostalCodeFormatter.isValid("A1A-1A1")) // hyphen
        assertFalse(PostalCodeFormatter.isValid("1A1 A1A")) // wrong order
    }
}
