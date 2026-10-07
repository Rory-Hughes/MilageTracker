package com.example.mileagetracker.ui.viewmodel

import android.net.Uri
import com.example.mileagetracker.domain.model.ExportFormat

/**
 * UI state for the PDF / CSV export flow driven by [ExportViewModel].
 *
 * ## State machine
 * ```
 * Idle ──► Exporting ──► Success
 *                    └──► Error
 * ```
 * - [Idle]      — no export in progress; the FAB is enabled.
 * - [Exporting] — [ExportLogbookToPdfUseCase] / [ExportLogbookToCsvUseCase]
 *                 is running; a progress dialog is shown and the FAB is
 *                 disabled.
 * - [Success]   — export completed; the share-sheet intent is ready.
 *                 [Success.mimeType] is forwarded from [ExportFormat.mimeType]
 *                 so the screen builds the correct `Intent.ACTION_SEND` MIME
 *                 type (`"application/pdf"` or `"text/csv"`).
 *                 The ViewModel resets to [Idle] after the intent is consumed
 *                 via [ExportViewModel.onShareConsumed].
 * - [Error]     — use case returned [ExportResult.Failure]; a snackbar
 *                 message is shown.  The ViewModel resets to [Idle] once
 *                 the error has been acknowledged via [ExportViewModel.clearError].
 *
 * @see ExportViewModel
 */
sealed interface ExportUiState {

    /** No export in progress. */
    data object Idle : ExportUiState

    /**
     * Export is running.
     *
     * @param vehicleId the vehicle being exported (for display only).
     * @param taxYear   the tax year being exported (for display only).
     */
    data class Exporting(
        val vehicleId: Int?,
        val taxYear: Int,
    ) : ExportUiState

    /**
     * Export succeeded.
     *
     * @param fileUri         `file://` URI of the generated file (converted to
     *                        `content://` by the screen via [FileProvider]).
     * @param format          [ExportFormat.PDF] or [ExportFormat.CSV].
     * @param fileSizeBytes   size of the generated file in bytes.
     * @param historyRecordId Room row-ID of the persisted [ReportHistoryEntity].
     * @param mimeType        IANA MIME type string derived from [ExportFormat.mimeType];
     *                        used directly in the share [Intent] so the sheet
     *                        filters to PDF viewers or spreadsheet apps correctly.
     */
    data class Success(
        val fileUri: Uri,
        val format: ExportFormat,
        val fileSizeBytes: Long,
        val historyRecordId: Int,
        val mimeType: String,
    ) : ExportUiState

    /**
     * Export failed.
     *
     * @param message human-readable reason surfaced by the export use case.
     */
    data class Error(val message: String) : ExportUiState
}
