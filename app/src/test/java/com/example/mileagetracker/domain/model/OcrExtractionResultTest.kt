package com.example.mileagetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrExtractionResultTest {

    @Test
    fun `EMPTY constant has correct values`() {
        val empty = OcrExtractionResult.EMPTY
        assertEquals("", empty.rawText)
        assertNull(empty.detectedAmount)
        assertNull(empty.detectedVendor)
        assertNull(empty.detectedDate)
        assertNull(empty.detectedGstHst)
        assertEquals(0f, empty.confidence, 0.001f)
    }

    @Test
    fun `confidence calculation handles partial fields`() {
        // This is primarily tested in MlKitOcrServiceTest, 
        // but we verify the data class itself here.
        val result = OcrExtractionResult(
            rawText = "text",
            detectedAmount = 10.0,
            detectedVendor = "Vendor",
            detectedDate = null,
            detectedGstHst = null,
            confidence = 0.5f
        )
        assertEquals(0.5f, result.confidence, 0.001f)
    }
}
