package com.example.mileagetracker.domain.model

import android.net.Uri

/**
 * Result of a logbook export operation.
 *
 * Returned by [ExportLogbookToPdfUseCase] and [ExportLogbookToCsvUseCase].
 * Consumed by [ExportViewModel] to drive [ExportState] transitions and by
 * [SaveReportHistoryUseCase] to persist the [Success.fileUri].
 *
 * ## Usage pattern
 * ```kotlin
 * when (val result = exportPdfUseCase(request)) {
 *     is ExportResult.Success -> {
 *         saveHistoryUseCase(result, request)
 *         _state.value = ExportState.Success(result.fileUri, result.format)
 *     }
 *     is ExportResult.Failure -> {
 *         _state.value = ExportState.Error(result.reason)
 *     }
 * }
 * ```
 */
sealed class ExportResult {

    /**
     * The export completed successfully.
     *
     * @property fileUri   A `file://` URI pointing to the generated file in
     *                     the app's cache directory.  Must be converted to a
     *                     `content://` URI via [FileProvider] before sharing
     *                     with external apps (Task 5.4 wires the FileProvider).
     * @property format    The format of the exported file.
     * @property fileSizeBytes Size of the generated file in bytes; used by
     *                     [SaveReportHistoryUseCase] to populate
     *                     [ReportHistoryEntity.fileSizeBytes].
     */
    data class Success(
        val fileUri: Uri,
        val format: ExportFormat,
        val fileSizeBytes: Long,
    ) : ExportResult()

    /**
     * The export failed.
     *
     * @property reason A human-readable description of why the export failed.
     *                  Surfaced in [ExportState.Error] for display in the UI.
     */
    data class Failure(
        val reason: String,
    ) : ExportResult()
}
