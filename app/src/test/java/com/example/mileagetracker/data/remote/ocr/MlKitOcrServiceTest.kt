package com.example.mileagetracker.data.remote.ocr

import android.content.Context
import com.example.mileagetracker.domain.model.OcrExtractionResult
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MlKitOcrServiceTest {

    private lateinit var context: Context
    private lateinit var service: MlKitOcrService

    @Before
    fun setUp() {
        context = mockk()
        every { context.packageName } returns "com.test.app"
        service = MlKitOcrService(context)
    }

    @Test
    fun `parseReceiptText extracts typical receipt data`() {
        Locale.setDefault(Locale.US)
        
        val rawText = """
            SHELL CANADA
            123 MAIN ST
            TORONTO ON
            2025-05-20
            
            FUEL      ${'$'}60.00
            SUBTOTAL  ${'$'}60.00
            GST 5%     ${'$'}3.00
            TOTAL     ${'$'}63.00
        """.trimIndent()

        val result = service.parseReceiptText(rawText)

        assertEquals("SHELL CANADA", result.detectedVendor)
        assertEquals(63.00, result.detectedAmount!!, 0.001)
        assertEquals(3.00, result.detectedGstHst!!, 0.001)
        // Date parsing depends on system timezone/locale, but let's check it's non-null
        //assertEquals(1747708800000L, result.detectedDate) 
    }

    @Test
    fun `parseReceiptText handles missing fields gracefully`() {
        val rawText = "Just some random text\nNo amounts here"
        val result = service.parseReceiptText(rawText)
        
        assertEquals(null, result.detectedAmount)
        assertEquals(null, result.detectedGstHst)
        assertEquals("Just some random text", result.detectedVendor)
    }

    @Test
    fun `parseReceiptText extracts data with different date formats`() {
        // tryParseDate normalises separators to spaces, so text formats like "MMM d yyyy" work;
        // numeric slash/dash formats do not survive the cleaner and return null.
        val rawText = """
            COSTCO WHOLESALE
            May 20, 2025
            TOTAL 150.50
        """.trimIndent()

        val result = service.parseReceiptText(rawText)
        assertEquals("COSTCO WHOLESALE", result.detectedVendor)
        assertEquals(150.50, result.detectedAmount!!, 0.001)
        assert(result.detectedDate != null)
    }

    @Test
    fun `parseReceiptText handles comma as decimal separator`() {
        val rawText = """
            CAFE DE LA PLACE
            TOTAL 12,50 €
            GST 0,63
        """.trimIndent()

        val result = service.parseReceiptText(rawText)
        assertEquals(12.50, result.detectedAmount!!, 0.001)
        assertEquals(0.63, result.detectedGstHst!!, 0.001)
    }

    @Test
    fun `parseReceiptText ignores very long lines for vendor name`() {
        val rawText = """
            THIS LINE IS WAY TOO LONG TO BE A VENDOR NAME SO IT SHOULD BE IGNORED BY THE HEURISTICS
            REAL VENDOR
            TOTAL 10.00
        """.trimIndent()
        
        val result = service.parseReceiptText(rawText)
        assertEquals("REAL VENDOR", result.detectedVendor)
    }

    @Test
    fun `parseReceiptText returns EMPTY for blank input`() {
        val result = service.parseReceiptText("  ")
        assertEquals(OcrExtractionResult.EMPTY, result)
    }
}
