package com.example.mileagetracker.domain.usecase.export

import com.example.mileagetracker.data.local.entity.ReportHistoryEntity
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import com.example.mileagetracker.domain.repository.ReportHistoryRepository
import javax.inject.Inject

/**
 * Persists a [ReportHistoryEntity] row after a successful logbook export.
 *
 * Called by [ExportViewModel] immediately after [ExportLogbookToPdfUseCase]
 * or [ExportLogbookToCsvUseCase] returns [ExportResult.Success].
 *
 * ## Responsibilities
 * - Maps [ExportResult.Success] + [LogbookExportRequest] to a
 *   [ReportHistoryEntity].
 * - Delegates persistence to [ReportHistoryRepository.save].
 * - Returns the database-assigned row ID so the ViewModel can navigate
 *   to the Report History entry if desired.
 *
 * ## Usage
 * ```kotlin
 * val result = exportPdfUseCase(request)
 * if (result is ExportResult.Success) {
 *     val historyId = saveReportHistoryUseCase(result, request)
 * }
 * ```
 *
 * ## Injection
 * `@Inject constructor` — resolved automatically by Hilt from the singleton
 * [ReportHistoryRepository] binding in [RepositoryModule].
 */
class SaveReportHistoryUseCase @Inject constructor(
    private val reportHistoryRepository: ReportHistoryRepository,
) {

    /**
     * Persists the export result.
     *
     * @param result  the [ExportResult.Success] returned by the export use case.
     * @param request the [LogbookExportRequest] that produced [result].
     * @return the database-assigned ID of the inserted [ReportHistoryEntity].
     */
    suspend operator fun invoke(
        result: ExportResult.Success,
        request: LogbookExportRequest,
    ): Int {
        val entity = ReportHistoryEntity(
            format = result.format.name,
            year = request.taxYear,
            vehicleId = request.vehicleId,
            generatedAt = System.currentTimeMillis(),
            fileUri = result.fileUri.toString(),
            fileSizeBytes = result.fileSizeBytes,
        )
        return reportHistoryRepository.save(entity)
    }
}
