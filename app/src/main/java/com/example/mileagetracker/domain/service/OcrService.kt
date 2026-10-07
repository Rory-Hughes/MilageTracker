package com.example.mileagetracker.domain.service

import android.net.Uri
import com.example.mileagetracker.domain.model.OcrExtractionResult

/**
 * Contract for receipt OCR extraction.
 *
 * Lives in the **domain** layer so that use cases can depend on it without
 * importing anything from `data`.  The default implementation
 * ([com.example.mileagetracker.data.remote.ocr.MlKitOcrService]) runs
 * entirely on-device using Google ML Kit Text Recognition (no API key).
 * A cloud-backed provider can be substituted by changing the Hilt binding
 * in [com.example.mileagetracker.di.ExpenseModule] without touching any
 * use-case or UI code.
 */
interface OcrService {
    /**
     * Extracts structured fields from the receipt image at [imageUri].
     *
     * This is a suspending call; implementations are expected to dispatch
     * to [kotlinx.coroutines.Dispatchers.IO] internally.
     *
     * @param imageUri  Content URI of the captured / picked receipt image.
     * @return Parsed [OcrExtractionResult]; never throws — returns
     *                  [OcrExtractionResult.EMPTY] on unrecoverable failure.
     */
    suspend fun extractFromImage(imageUri: Uri): OcrExtractionResult
}
