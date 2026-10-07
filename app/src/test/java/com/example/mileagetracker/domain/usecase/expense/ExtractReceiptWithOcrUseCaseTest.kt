package com.example.mileagetracker.domain.usecase.expense

import android.net.Uri
import com.example.mileagetracker.domain.model.OcrExtractionResult
import com.example.mileagetracker.domain.service.OcrService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ExtractReceiptWithOcrUseCaseTest {

    private lateinit var ocrService: OcrService
    private lateinit var useCase: ExtractReceiptWithOcrUseCase

    @Before
    fun setUp() {
        ocrService = mockk()
        useCase = ExtractReceiptWithOcrUseCase(ocrService)
    }

    @Test
    fun `returns result from ocrService`() = runTest {
        val uri = mockk<Uri>()
        val expected = OcrExtractionResult(
            rawText = "Shell $50.00",
            detectedAmount = 50.0,
            detectedVendor = "Shell",
            detectedDate = null,
            detectedGstHst = null,
            confidence = 0.9f
        )
        coEvery { ocrService.extractFromImage(uri) } returns expected

        val result = useCase(uri)

        assertEquals(expected, result)
    }

    @Test
    fun `returns EMPTY on service failure`() = runTest {
        val uri = mockk<Uri>()
        coEvery { ocrService.extractFromImage(uri) } throws RuntimeException("OCR Error")

        val result = useCase(uri)

        assertEquals(OcrExtractionResult.EMPTY, result)
    }
}
