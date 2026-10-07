package com.example.mileagetracker.ui.expense

import android.net.Uri
import com.example.mileagetracker.data.remote.ocr.MlKitOcrService
import com.example.mileagetracker.domain.model.OcrExtractionResult
import com.example.mileagetracker.domain.usecase.expense.ExtractReceiptWithOcrUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OcrReceiptViewModelTest {

    private lateinit var viewModel: OcrReceiptViewModel
    private lateinit var mlKitOcrService: MlKitOcrService
    private lateinit var extractReceiptWithOcrUseCase: ExtractReceiptWithOcrUseCase
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mlKitOcrService = mockk()
        extractReceiptWithOcrUseCase = mockk()
        viewModel = OcrReceiptViewModel(mlKitOcrService, extractReceiptWithOcrUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `prepareImageUri calls service and sets internal state`() {
        val mockUri = mockk<Uri>()
        every { mlKitOcrService.createReceiptImageUri() } returns mockUri

        val result = viewModel.prepareImageUri()

        assertEquals(mockUri, result)
    }

    @Test
    fun `onImageCaptured triggers OCR and updates state`() = runTest {
        val mockUri = mockk<Uri>()
        every { mlKitOcrService.createReceiptImageUri() } returns mockUri
        
        val ocrResult = OcrExtractionResult(
            rawText = "Shell $50.00",
            detectedAmount = 50.0,
            detectedVendor = "Shell",
            detectedDate = 1735689600000L, // 2025-01-01
            detectedGstHst = 2.5,
            confidence = 0.9f
        )
        coEvery { extractReceiptWithOcrUseCase(mockUri) } returns ocrResult

        viewModel.prepareImageUri()
        viewModel.onImageCaptured()
        
        assertTrue(viewModel.uiState.value.isProcessing)
        
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isProcessing)
        assertEquals("50.00", state.amountRaw)
        assertEquals("Shell", state.vendorRaw)
        assertNotNull(state.dateDisplay)
        assertEquals("2.50", state.gstHstRaw)
    }

    @Test
    fun `onAmountChanged updates state`() {
        viewModel.onAmountChanged("10.00")
        assertEquals("10.00", viewModel.uiState.value.amountRaw)
    }
}
