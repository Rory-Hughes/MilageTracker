package com.example.mileagetracker.domain.usecase.export

import android.net.Uri
import com.example.mileagetracker.domain.model.ExportFormat
import com.example.mileagetracker.domain.model.ExportResult
import com.example.mileagetracker.domain.model.LogbookExportRequest
import com.example.mileagetracker.domain.repository.ReportHistoryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SaveReportHistoryUseCaseTest {

    private lateinit var repository: ReportHistoryRepository
    private lateinit var useCase: SaveReportHistoryUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = SaveReportHistoryUseCase(repository)
    }

    @Test
    fun `maps result and request to entity and saves`() = runTest {
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns "file:///tmp/report.pdf"
        
        val result = ExportResult.Success(
            fileUri = mockUri,
            format = ExportFormat.PDF,
            fileSizeBytes = 2048L
        )
        val request = LogbookExportRequest(
            vehicleId = 1,
            taxYear = 2025,
            format = ExportFormat.PDF
        )
        
        coEvery { repository.save(any()) } returns 123

        val historyId = useCase(result, request)

        assertEquals(123, historyId)
        coEvery { repository.save(match {
            it.format == "PDF" &&
            it.year == 2025 &&
            it.vehicleId == 1 &&
            it.fileUri == "file:///tmp/report.pdf" &&
            it.fileSizeBytes == 2048L
        }) }
    }
}
