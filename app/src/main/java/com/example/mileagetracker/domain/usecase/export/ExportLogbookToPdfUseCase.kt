package com.example.mileagetracker.domain.usecase.export

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import com.example.mileagetracker.domain.model.VehicleAnnualSummary
import com.example.mileagetracker.domain.usecase.rate.GetAnnualVehicleSummaryUseCase
import com.example.mileagetracker.util.DateFormats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class ExportLogbookToPdfUseCase @Inject constructor(
    private val buildLogbookExportData: BuildLogbookExportDataUseCase,
    private val annualSummary: GetAnnualVehicleSummaryUseCase,
    @param:ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(request: LogbookExportRequest): ExportResult =
        withContext(Dispatchers.IO) {
            runCatching { generate(request) }
                .getOrElse { error ->
                    ExportResult.Failure(error.message ?: "Unknown error during PDF generation")
                }
        }

    private suspend fun generate(request: LogbookExportRequest): ExportResult {
        val prepared = buildLogbookExportData(request)
        val sectionSummaries = createSectionSummaries(prepared)
        val pdf = PdfDocument()
        var pageIndex = 1

        pageIndex = renderCoverPage(
            pdf = pdf,
            pageIndex = pageIndex,
            prepared = prepared,
            sectionSummaries = sectionSummaries.values.filterNotNull(),
        )

        prepared.sections.forEach { section ->
            pageIndex = renderSection(
                pdf = pdf,
                pageIndex = pageIndex,
                prepared = prepared,
                section = section,
                summary = sectionSummaries[section],
            )
        }

        val file = createExportFile(prepared.scopeToken, prepared.taxYear)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        return ExportResult.Success(
            fileUri = Uri.fromFile(file),
            format = ExportFormat.PDF,
            fileSizeBytes = file.length(),
        )
    }

    private suspend fun createSectionSummaries(
        prepared: PreparedLogbookExport,
    ): Map<PreparedLogbookSection, VehicleAnnualSummary?> {
        if (!prepared.isBusinessOnly) {
            return emptyMap()
        }

        return prepared.sections.associateWith { section ->
            section.vehicleId?.let { annualSummary(it, prepared.taxYear) }
        }
    }

    private fun createExportFile(scopeToken: String, taxYear: Int): File {
        val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        return File(
            exportDir,
            "logbook_${scopeToken}_${taxYear}_${fileTimestamp()}.pdf",
        )
    }

    private fun fileTimestamp(): String = DateFormats.exportFileTimestampMillisNow()
}
