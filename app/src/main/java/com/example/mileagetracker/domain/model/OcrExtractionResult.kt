package com.example.mileagetracker.domain.model

/**
 * Output of a single OCR pass over a receipt image.
 *
 * All detected fields are nullable — callers must check for null and fall
 * back to manual entry.  [confidence] is an overall quality score in
 * [0f, 1f] returned by [MlKitOcrService]; individual-field confidence is
 * surfaced separately in the UI via null checks.
 *
 * @param rawText         Full text returned by the OCR engine (debug / logging).
 * @param detectedAmount  The largest currency amount found (likely the total).
 * @param detectedVendor  First capitalised "company-like" line of the receipt.
 * @param detectedDate    Epoch-millis of the date parsed from the receipt, UTC midnight.
 * @param detectedGstHst  Amount on a line containing "GST", "HST", or "TAX".
 * @param confidence      Overall OCR confidence; 0f on total failure.
 */
data class OcrExtractionResult(
    val rawText: String,
    val detectedAmount: Double?,
    val detectedVendor: String?,
    val detectedDate: Long?,
    val detectedGstHst: Double?,
    val confidence: Float,
) {
    companion object {
        /** Returned by [ExtractReceiptWithOcrUseCase] when ML Kit throws. */
        val EMPTY = OcrExtractionResult(
            rawText = "",
            detectedAmount = null,
            detectedVendor = null,
            detectedDate = null,
            detectedGstHst = null,
            confidence = 0f,
        )
    }
}
