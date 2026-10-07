package com.example.mileagetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mileagetracker.data.local.entity.TripCategory
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import com.example.mileagetracker.domain.usecase.export.ExportLogbookToCsvUseCase
import com.example.mileagetracker.domain.usecase.export.ExportLogbookToPdfUseCase
import com.example.mileagetracker.domain.usecase.export.SaveReportHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the CRA logbook export flow (PDF and CSV).
 *
 * ## Responsibilities
 * 1. Accepts an export request from [CraLogbookScreen] via [exportLogbook].
 * 2. Drives [ExportUiState] through `Idle → Exporting → Success | Error`.
 * 3. Dispatches to [ExportLogbookToPdfUseCase] or [ExportLogbookToCsvUseCase]
 *    based on the [ExportFormat] in the request.
 * 4. On success, calls [SaveReportHistoryUseCase] to persist export metadata
 *    to the Room `report_history` table.
 * 5. Exposes [uiState] as a [StateFlow] for [CraLogbookScreen] to observe.
 *
 * ## MIME type
 * [ExportUiState.Success.mimeType] is set based on the format so the screen
 * can build the correct `Intent.ACTION_SEND` MIME type:
 * - PDF  → `"application/pdf"`
 * - CSV  → `"text/csv"`
 *
 * ## Usage
 * ```kotlin
 * // PDF
 * exportVm.exportLogbook(vehicleId, selectedYear, format = ExportFormat.PDF)
 * // CSV
 * exportVm.exportLogbook(vehicleId, selectedYear, format = ExportFormat.CSV)
 * ```
 *
 * ## Injection
 * `@HiltViewModel` — all three use cases carry `@Inject constructor` so no
 * explicit [UseCaseModule] entry is required.
 *
 * @param exportPdf         generates the PDF logbook.
 * @param exportCsv         generates the CSV logbook.
 * @param saveReportHistory persists export metadata on success.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportPdf: ExportLogbookToPdfUseCase,
    private val exportCsv: ExportLogbookToCsvUseCase,
    private val saveReportHistory: SaveReportHistoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    /**
     * Initiates a logbook export.
     *
     * A no-op if an export is already in progress ([ExportUiState.Exporting]).
     *
     * @param vehicleId            DB PK of the vehicle to export, or null for all.
     * @param taxYear              Four-digit CRA tax year.
     * @param format               [ExportFormat.PDF] or [ExportFormat.CSV].
     * @param categories           Trip categories to include in the export.
     * @param includeRouteDetails  Include start/end address columns (default true).
     */
    fun exportLogbook(
        vehicleId: Int?,
        taxYear: Int,
        format: ExportFormat = ExportFormat.PDF,
        categories: Set<TripCategory> = setOf(TripCategory.BUSINESS),
        includeRouteDetails: Boolean = true,
    ) {
        if (_uiState.value is ExportUiState.Exporting) return

        val request = LogbookExportRequest(
            vehicleId = vehicleId,
            taxYear = taxYear,
            format = format,
            categories = categories.ifEmpty { setOf(TripCategory.BUSINESS) },
            includeRouteDetails = includeRouteDetails,
        )

        viewModelScope.launch {
            _uiState.value = ExportUiState.Exporting(
                vehicleId = vehicleId,
                taxYear = taxYear,
            )

            val result = when (format) {
                ExportFormat.PDF -> exportPdf(request)
                ExportFormat.CSV -> exportCsv(request)
            }

            when (result) {
                is ExportResult.Success -> {
                    val historyId = saveReportHistory(result, request)
                    _uiState.value = ExportUiState.Success(
                        fileUri = result.fileUri,
                        format = result.format,
                        fileSizeBytes = result.fileSizeBytes,
                        historyRecordId = historyId,
                        mimeType = result.format.mimeType,
                    )
                }

                is ExportResult.Failure -> {
                    _uiState.value = ExportUiState.Error(result.reason)
                }
            }
        }
    }

    /**
     * Resets [uiState] to [ExportUiState.Idle] after a [ExportUiState.Success]
     * share intent has been consumed by the screen.
     */
    fun onShareConsumed() {
        if (_uiState.value is ExportUiState.Success) {
            _uiState.value = ExportUiState.Idle
        }
    }

    /**
     * Resets [uiState] to [ExportUiState.Idle] after a [ExportUiState.Error]
     * snackbar has been acknowledged by the screen.
     */
    fun clearError() {
        if (_uiState.value is ExportUiState.Error) {
            _uiState.value = ExportUiState.Idle
        }
    }
}
