package com.example.mileagetracker.ui.expense

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.remote.ocr.MlKitOcrService
import com.example.mileagetracker.domain.model.OcrExtractionResult
import com.example.mileagetracker.domain.usecase.expense.ExtractReceiptWithOcrUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * ViewModel for [OcrReceiptScreen].
 *
 * ## Image lifecycle
 * [prepareImageUri] delegates to [MlKitOcrService.createReceiptImageUri], which
 * creates a temp JPEG under `<cacheDir>/ocr_receipts/` and returns a
 * `content://` URI via the app's registered FileProvider
 * (authority `${applicationId}.provider`).
 *
 * The URI is retained in [_pendingImageUri] so it can be passed to
 * [ExtractReceiptWithOcrUseCase] when the camera returns via [onImageCaptured].
 */
@HiltViewModel
class OcrReceiptViewModel @Inject constructor(
    private val mlKitOcrService: MlKitOcrService,
    private val extractReceiptWithOcrUseCase: ExtractReceiptWithOcrUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrReceiptUiState())
    val uiState: StateFlow<OcrReceiptUiState> = _uiState.asStateFlow()

    private var _pendingImageUri: Uri? = null

    private val dateFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())

    // ── Camera lifecycle ──────────────────────────────────────────────────────

    /**
     * Creates the temp file and returns its FileProvider URI.
     * Must be called on the main thread before launching the camera intent.
     */
    fun prepareImageUri(): Uri {
        val uri = mlKitOcrService.createReceiptImageUri()
        _pendingImageUri = uri
        return uri
    }

    /**
     * Called by the camera launcher after a successful capture.
     * Triggers OCR extraction on [_pendingImageUri].
     */
    fun onImageCaptured() {
        val uri = _pendingImageUri ?: return
        _uiState.update { it.copy(imageUri = uri, isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            val result = extractReceiptWithOcrUseCase(uri)
            _uiState.update { current ->
                current.copy(
                    isProcessing = false,
                    result = result,
                    amountRaw = result.detectedAmount?.let { formatAmount(it) } ?: current.amountRaw,
                    vendorRaw = result.detectedVendor ?: current.vendorRaw,
                    dateDisplay = result.detectedDate?.let { formatDate(it) } ?: current.dateDisplay,
                    gstHstRaw = result.detectedGstHst?.let { formatAmount(it) } ?: current.gstHstRaw,
                    errorMessage = if (result.confidence == 0f && result.rawText.isEmpty()) {
                        "Could not read the receipt. Please enter values manually."
                    } else {
                        null
                    },
                )
            }
        }
    }

    // ── Field mutation ────────────────────────────────────────────────────────

    fun onAmountChanged(value: String) = _uiState.update { it.copy(amountRaw = value) }
    fun onVendorChanged(value: String) = _uiState.update { it.copy(vendorRaw = value) }
    fun onGstHstChanged(value: String) = _uiState.update { it.copy(gstHstRaw = value) }

    // ── Formatting helpers ────────────────────────────────────────────────────

    private fun formatAmount(value: Double) = "%.2f".format(value)

    private fun formatDate(epochMillis: Long): String =
        dateFormatter.format(Instant.ofEpochMilli(epochMillis))
}

/**
 * Immutable UI state for [OcrReceiptScreen].
 */
data class OcrReceiptUiState(
    val imageUri: Uri? = null,
    val isProcessing: Boolean = false,
    val result: OcrExtractionResult? = null,
    val amountRaw: String = "",
    val vendorRaw: String = "",
    val dateDisplay: String = "",
    val gstHstRaw: String = "",
    val errorMessage: String? = null,
)
