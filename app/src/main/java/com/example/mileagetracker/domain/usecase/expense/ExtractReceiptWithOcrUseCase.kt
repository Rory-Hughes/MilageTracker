package com.example.mileagetracker.domain.usecase.expense

import android.net.Uri
import com.example.mileagetracker.domain.model.OcrExtractionResult
import com.example.mileagetracker.domain.service.OcrService
import javax.inject.Inject

/**
 * Accepts a receipt image [Uri] and delegates to [OcrService.extractFromImage].
 *
 * [OcrService] is a domain-layer interface; the concrete implementation
 * ([MlKitOcrService]) is bound via Hilt in [ExpenseModule] and never
 * referenced here directly, keeping the use case free of data-layer imports.
 *
 * Failures inside [OcrService] are already swallowed by [MlKitOcrService],
 * but this use case adds a final safety net so that callers always receive
 * either a populated [OcrExtractionResult] or [OcrExtractionResult.EMPTY].
 */
class ExtractReceiptWithOcrUseCase @Inject constructor(
    private val ocrService: OcrService,
) {
    suspend operator fun invoke(imageUri: Uri): OcrExtractionResult =
        runCatching { ocrService.extractFromImage(imageUri) }
            .getOrElse { OcrExtractionResult.EMPTY }
}
