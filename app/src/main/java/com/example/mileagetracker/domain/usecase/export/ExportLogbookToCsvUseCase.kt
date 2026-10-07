package com.example.mileagetracker.domain.usecase.export

import android.content.Context
import android.net.Uri
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import com.example.mileagetracker.util.DateFormats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

class ExportLogbookToCsvUseCase @Inject constructor(
    private val buildLogbookExportData: BuildLogbookExportDataUseCase,
    @param:ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(request: LogbookExportRequest): ExportResult =
        withContext(Dispatchers.IO) {
            runCatching { generate(request) }
                .getOrElse { error ->
                    ExportResult.Failure(error.message ?: "Unknown error during CSV generation")
                }
        }

    private suspend fun generate(request: LogbookExportRequest): ExportResult {
        val prepared = buildLogbookExportData(request)
        val file = createExportFile(
            scopeToken = prepared.scopeToken,
            taxYear = request.taxYear,
        )

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                writer.write("\uFEFF")
                writer.writeLine(HEADER)
                writeSections(writer = writer, prepared = prepared)
                writeTotals(writer = writer, prepared = prepared)
                writeMetadata(writer = writer, prepared = prepared)
            }
        }

        return ExportResult.Success(
            fileUri = Uri.fromFile(file),
            format = ExportFormat.CSV,
            fileSizeBytes = file.length(),
        )
    }

    private fun createExportFile(scopeToken: String, taxYear: Int): File {
        val exportDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        return File(
            exportDir,
            "logbook_${scopeToken}_${taxYear}_${fileTimestamp()}.csv",
        )
    }

    private fun fileTimestamp(): String = DateFormats.exportFileTimestampMillisNow()

    private companion object {
        const val HEADER =
            "Date,Start Location,End Location,Destination,Category,Distance (km),Vehicle,Notes"
    }
}
