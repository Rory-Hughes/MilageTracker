package com.example.mileagetracker.ui.expense

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mileagetracker.domain.model.OcrExtractionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scaffold-level tests for OcrReceiptScreen.
 *
 * The public `OcrReceiptScreen` calls hiltViewModel() and manages the camera launcher so it
 * can't be driven directly here. `OcrReceiptScaffold` (promoted to internal) takes a plain
 * `OcrReceiptUiState` plus callback lambdas and is the seam we exercise.
 *
 * Novel dimensions vs the form templates:
 *  - Five visual states: no image, image+idle, image+processing, result with high/partial/low
 *    confidence bands.
 *  - Confirm button has a compound enabled guard: imageUri != null && !isProcessing.
 *  - Auto-detected vs Manual entry chip reflects per-field OCR result nullability.
 *  - Confirm callback receives the four output values from the current state.
 */
@RunWith(AndroidJUnit4::class)
class OcrReceiptScaffoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val stubUri: Uri = Uri.parse("content://test/receipt.jpg")

    private fun setContent(
        state: OcrReceiptUiState = OcrReceiptUiState(),
        onBack: () -> Unit = {},
        onOpenCamera: () -> Unit = {},
        onAmountChanged: (String) -> Unit = {},
        onVendorChanged: (String) -> Unit = {},
        onGstHstChanged: (String) -> Unit = {},
        onConfirm: (String, String, Long?, String) -> Unit = { _, _, _, _ -> },
    ) {
        composeRule.setContent {
            OcrReceiptScaffold(
                state = state,
                onBack = onBack,
                onOpenCamera = onOpenCamera,
                onAmountChanged = onAmountChanged,
                onVendorChanged = onVendorChanged,
                onGstHstChanged = onGstHstChanged,
                onConfirm = onConfirm,
            )
        }
    }

    // --- Static UI (no image state) ---

    @Test
    fun noImage_showsPlaceholderText() {
        setContent(state = OcrReceiptUiState(imageUri = null))

        composeRule.onNodeWithText("No receipt scanned yet").assertIsDisplayed()
    }

    @Test
    fun noImage_cameraButtonSaysOpenCamera() {
        setContent(state = OcrReceiptUiState(imageUri = null))

        composeRule.onNodeWithText("Open Camera", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Re-scan", substring = true).assertCountEquals(0)
    }

    @Test
    fun noImage_confirmButtonIsDisabled() {
        setContent(state = OcrReceiptUiState(imageUri = null))

        composeRule.onNodeWithText("Confirm & Fill Form").assertIsNotEnabled()
    }

    // --- Has image (idle) state ---

    @Test
    fun hasImage_cameraButtonSaysRescan() {
        setContent(state = OcrReceiptUiState(imageUri = stubUri))

        composeRule.onNodeWithText("Re-scan", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Open Camera", substring = true).assertCountEquals(0)
    }

    @Test
    fun hasImage_notProcessing_confirmButtonIsEnabled() {
        setContent(
            state = OcrReceiptUiState(imageUri = stubUri, isProcessing = false),
        )

        composeRule.onNodeWithText("Confirm & Fill Form").assertIsEnabled()
    }

    // --- Processing state ---

    @Test
    fun processing_confirmButtonIsDisabled() {
        // Compound guard: imageUri != null && !isProcessing — processing blocks confirm.
        setContent(
            state = OcrReceiptUiState(imageUri = stubUri, isProcessing = true),
        )

        composeRule.onNodeWithText("Confirm & Fill Form").assertIsNotEnabled()
    }

    // --- Confidence banners ---

    @Test
    fun highConfidenceResult_showsHighBanner() {
        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                result = sampleResult(confidence = 0.90f),
            ),
        )

        composeRule.onNodeWithText("High confidence", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Partial confidence", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Low confidence", substring = true).assertCountEquals(0)
    }

    @Test
    fun partialConfidenceResult_showsPartialBanner() {
        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                result = sampleResult(confidence = 0.55f),
            ),
        )

        composeRule.onNodeWithText("Partial confidence", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("High confidence", substring = true).assertCountEquals(0)
    }

    @Test
    fun lowConfidenceResult_showsLowBanner() {
        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                result = sampleResult(confidence = 0.20f),
            ),
        )

        composeRule.onNodeWithText("Low confidence", substring = true).assertIsDisplayed()
    }

    @Test
    fun noResult_noConfidenceBannerRendered() {
        setContent(state = OcrReceiptUiState(result = null))

        composeRule.onAllNodesWithText("confidence", substring = true).assertCountEquals(0)
    }

    // --- Confidence band boundary conditions ---

    @Test
    fun confidenceBoundary_exactly75_isHigh() {
        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                result = sampleResult(confidence = 0.75f),
            ),
        )

        composeRule.onNodeWithText("High confidence", substring = true).assertIsDisplayed()
    }

    @Test
    fun confidenceBoundary_exactly40_isPartial() {
        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                result = sampleResult(confidence = 0.40f),
            ),
        )

        composeRule.onNodeWithText("Partial confidence", substring = true).assertIsDisplayed()
    }

    // --- Auto-detected chips ---

    @Test
    fun resultWithAllFieldsDetected_showsAutoDetectedChips() {
        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                result = sampleResult(
                    detectedAmount = 25.00,
                    detectedVendor = "Tim Hortons",
                    detectedDate = System.currentTimeMillis(),
                    detectedGstHst = 1.25,
                ),
            ),
        )

        // All 4 fields auto-detected → 4 "Auto-detected" chips.
        composeRule.onAllNodesWithText("Auto-detected").assertCountEquals(4)
        composeRule.onAllNodesWithText("Manual entry").assertCountEquals(0)
    }

    @Test
    fun resultWithNoFieldsDetected_showsManualEntryChips() {
        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                result = sampleResult(
                    detectedAmount = null,
                    detectedVendor = null,
                    detectedDate = null,
                    detectedGstHst = null,
                ),
            ),
        )

        composeRule.onAllNodesWithText("Manual entry").assertCountEquals(4)
        composeRule.onAllNodesWithText("Auto-detected").assertCountEquals(0)
    }

    @Test
    fun partiallyDetectedResult_mixesChips() {
        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                result = sampleResult(
                    detectedAmount = 12.50,
                    detectedVendor = null,
                    detectedDate = null,
                    detectedGstHst = null,
                ),
            ),
        )

        // Amount detected, rest manual.
        composeRule.onAllNodesWithText("Auto-detected").assertCountEquals(1)
        composeRule.onAllNodesWithText("Manual entry").assertCountEquals(3)
    }

    @Test
    fun noResult_allFieldsShowManualEntryChips() {
        setContent(state = OcrReceiptUiState(result = null))

        composeRule.onAllNodesWithText("Manual entry").assertCountEquals(4)
    }

    // --- Error message ---

    @Test
    fun errorMessage_rendersWhenPresent() {
        setContent(
            state = OcrReceiptUiState(errorMessage = "OCR processing failed."),
        )

        composeRule.onNodeWithText("OCR processing failed.").assertIsDisplayed()
    }

    @Test
    fun noErrorMessage_errorSlotIsHidden() {
        setContent(state = OcrReceiptUiState(errorMessage = null))

        composeRule.onAllNodesWithText("OCR processing failed.").assertCountEquals(0)
    }

    // --- Field prefill and callbacks ---

    @Test
    fun populatedState_prefillsEditableFields() {
        setContent(
            state = OcrReceiptUiState(
                amountRaw = "32.40",
                vendorRaw = "Shell",
                gstHstRaw = "4.20",
            ),
        )

        composeRule.onNodeWithText("32.40").assertIsDisplayed()
        composeRule.onNodeWithText("Shell").assertIsDisplayed()
        composeRule.onNodeWithText("4.20").assertIsDisplayed()
    }

    @Test
    fun typingAmount_invokesOnAmountChanged() {
        var last: String? = null
        setContent(onAmountChanged = { last = it })

        composeRule.onNodeWithText("Amount (CAD)").performTextInput("18.50")
        composeRule.waitForIdle()

        assertEquals("18.50", last)
    }

    @Test
    fun editingAmount_invokesOnAmountChangedWithReplacement() {
        var last: String? = null
        setContent(
            state = OcrReceiptUiState(amountRaw = "10.00"),
            onAmountChanged = { last = it },
        )

        composeRule.onNodeWithText("10.00").performTextReplacement("22.00")
        composeRule.waitForIdle()

        assertEquals("22.00", last)
    }

    @Test
    fun typingVendor_invokesOnVendorChanged() {
        var last: String? = null
        setContent(onVendorChanged = { last = it })

        composeRule.onNodeWithText("Vendor").performTextInput("Esso")
        composeRule.waitForIdle()

        assertEquals("Esso", last)
    }

    @Test
    fun typingGstHst_invokesOnGstHstChanged() {
        var last: String? = null
        setContent(onGstHstChanged = { last = it })

        composeRule.onNodeWithText("GST/HST Amount").performTextInput("2.60")
        composeRule.waitForIdle()

        assertEquals("2.60", last)
    }

    // --- Camera callback ---

    @Test
    fun clickingOpenCamera_invokesOnOpenCamera() {
        var opened = false
        setContent(
            state = OcrReceiptUiState(imageUri = null),
            onOpenCamera = { opened = true },
        )

        composeRule.onNodeWithText("Open Camera", substring = true).performClick()
        composeRule.waitForIdle()

        assertTrue(opened)
    }

    // --- Back navigation ---

    @Test
    fun backButton_invokesOnBack() {
        var navigatedBack = false
        setContent(onBack = { navigatedBack = true })

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        assertTrue(navigatedBack)
    }

    // --- Confirm callback ---

    @Test
    fun clickingConfirm_passesCurrentStateValues() {
        val detectedDate = 1_735_000_000_000L
        var capturedAmount: String? = null
        var capturedVendor: String? = null
        var capturedDate: Long? = null
        var capturedGst: String? = null

        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                amountRaw = "55.00",
                vendorRaw = "Costco",
                gstHstRaw = "7.15",
                result = sampleResult(detectedDate = detectedDate),
            ),
            onConfirm = { amount, vendor, date, gst ->
                capturedAmount = amount
                capturedVendor = vendor
                capturedDate = date
                capturedGst = gst
            },
        )

        composeRule.onNodeWithText("Confirm & Fill Form").performClick()
        composeRule.waitForIdle()

        assertNotNull("onConfirm should fire", capturedAmount)
        assertEquals("55.00", capturedAmount)
        assertEquals("Costco", capturedVendor)
        assertEquals(detectedDate, capturedDate)
        assertEquals("7.15", capturedGst)
    }

    @Test
    fun clickingConfirm_withNoDetectedDate_passesNullDate() {
        var capturedDate: Long? = -1L
        setContent(
            state = OcrReceiptUiState(
                imageUri = stubUri,
                result = sampleResult(detectedDate = null),
            ),
            onConfirm = { _, _, date, _ -> capturedDate = date },
        )

        composeRule.onNodeWithText("Confirm & Fill Form").performClick()
        composeRule.waitForIdle()

        assertEquals(null, capturedDate)
    }

    // --- Header ---

    @Test
    fun scaffoldTitle_isScanReceipt() {
        setContent()

        composeRule.onNodeWithText("Scan Receipt").assertIsDisplayed()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun sampleResult(
        rawText: String = "",
        detectedAmount: Double? = null,
        detectedVendor: String? = null,
        detectedDate: Long? = null,
        detectedGstHst: Double? = null,
        confidence: Float = 0.90f,
    ) = OcrExtractionResult(
        rawText = rawText,
        detectedAmount = detectedAmount,
        detectedVendor = detectedVendor,
        detectedDate = detectedDate,
        detectedGstHst = detectedGstHst,
        confidence = confidence,
    )
}
